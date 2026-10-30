# Sarao: gestión de eventos y venta de entradas en microservicios

Sistema de gestión de eventos y venta de entradas, rehecho en microservicios a
partir de un sistema en capas ya terminado. Se desarrolla con **Scrum**. El
usuario trabaja solo y tiene todos los roles (Product Owner, Scrum Master y
equipo). Entrega máxima: **09/12/2026**.

El sistema base (carpeta `proyecto tecno` del Escritorio) es **solo lectura**:
se consulta y se copia de él, nunca se modifica.

---

## Stack

| Capa | Tecnología |
|---|---|
| Backend | Java 21, Spring Boot 4.1.1, Spring Cloud 2025.1.x (gateway) |
| Frontend | Angular 22.1 (Signal Forms, `httpResource`, Vitest) |
| Datos | MySQL 8: un contenedor, tres bases con usuarios separados |
| Contenedores | Docker |
| Correo | Servidor de correo propio, con simulador local por defecto en desarrollo y pruebas |
| Pago | Libélula detrás de una interfaz propia, con simulador por defecto (ver Reglas de negocio > Pago) |
| Despliegue | VPS nuevo, propio de este proyecto |

---

## Arquitectura

Detalle y motivos en `docs/decisiones.md` → «0. Microservicios», que gana sobre
las decisiones técnicas anteriores del mismo archivo.

| Servicio | Casos de uso | Base | Tablas |
|---|---|---|---|
| Seguridad | CU-01 a 04, CU-09 | `seguridad_db` | `usuario`, `recuperacion_contrasena` |
| Eventos y Venta | CU-05, 06, 08, 10 a 12, 14, 15 | `eventos_db` | `evento`, `tipo_entrada`, `reserva`, `entrada`, `acceso`, `reporte`, `visita_pagina` |
| Pagos | CU-07, CU-13 | `pagos_db` | `pago` |
| Notificaciones | Correo (transversal) | Ninguna | Ninguna |
| Gateway | Entrada única `/api` | Ninguna | Ninguna |

- Ningún servicio lee la base de otro, y no hay llaves foráneas entre bases:
  las relaciones entre servicios son identificadores.
- Los servicios se comunican por REST interno, sin broker. Las rutas `/interno`
  llevan clave de servicio y no se publican en el gateway.
- Dentro de cada servicio se mantienen las capas: API REST, lógica de negocio y
  datos.
- Cada servicio vive en `servicios/<nombre>`, con su pom y su wrapper de Maven, y
  se agrega a la matriz de `.github/workflows/pruebas.yml`. El paquete es
  `com.sarao.<servicio>`.

---

## Reglas de negocio

Estas reglas vienen del documento de análisis. **No inventar
comportamiento que no esté acá**; si algo falta, preguntar antes de
asumir.

### Usuarios y roles
- Tres roles **fijos**: Cliente, Administrador, Personal autorizado. No es
  RBAC: no se crean roles nuevos ni se configuran permisos individuales.
  Es un atributo enumerado de Usuario.
- Todo usuario que se registra por sí mismo obtiene rol **Cliente**.
- Los roles Administrador y Personal autorizado los asigna un
  Administrador sobre un usuario ya registrado (CU-09).
- El **Administrador inicial** se crea en el despliegue mediante carga
  inicial de datos, no mediante un caso de uso.
- El control de acceso se valida **en el backend**. Ocultar opciones en
  Angular según el rol es usabilidad, no seguridad.
- Estados de cuenta: activa, desactivada.

### Eventos
- Adaptable a distintos tipos de eventos.
- **Un evento por fecha.** La misma actividad en otra fecha es OTRO
  evento, no una segunda función del mismo.
- Estados: borrador, activo, postergado, cancelado. El evento nace en
  borrador y solo se activa si tiene al menos un tipo de entrada.
- El administrador cierra ventas manualmente; el sistema también las
  cierra automáticamente según hora configurada.

### Entradas
- **NO hay selección de asientos.** Se gestiona por cantidades
  disponibles.
- Tipos y precios configurables.
- Límite máximo de entradas por cliente configurable; puede ser "sin
  límite". Es acumulado por evento: cuenta las entradas ya emitidas y las
  reservas vigentes del cliente, no solo la compra en curso.
- Cada entrada tiene un código QR único.
- Estados: activa, utilizada, devuelta.

### Compra
- Flujo: seleccionar evento → tipo/cantidad → validaciones → reserva
  temporal → pago → verificación → confirmación → generación de entrada.
- Una compra corresponde a **un solo tipo de entrada**. No hay carrito.
- La **reserva temporal dura 10 minutos**, también mientras el pago está
  pendiente.
- Si no se paga dentro de esos 10 minutos, la reserva expira y las entradas
  se liberan.
- **Pago tardío** (llega después de que venció la reserva): si todavía hay
  cupo, se confirma la compra; si no, el pago queda marcado para devolución
  fuera del sistema y no se generan entradas.

### Pago
- Mediante código QR, con sistema de pago externo.
- Proveedor: **Libélula**, detrás de una interfaz propia. Un **simulador
  local** es la implementación por defecto en desarrollo y pruebas.
- El nombre del proveedor aparece **solo** en su adaptador y en su
  configuración; nunca en entidades, servicios, controladores ni en la API.
  No usar nombres de otros proveedores (Mercado Pago, Stripe, PayPal u
  otros) en código ni documentación.
- Estados: pendiente, pagado, expirado, error. No existe "rechazado": en un
  pago por QR el cliente paga desde su banco, y un fallo nunca llega al
  sistema.
- El pago se confirma consultando al sistema de pago, nunca por lo que
  muestre o diga el cliente.

### Validación de acceso
- El personal autorizado escanea la entrada.
- Se valida: correspondencia con el evento, vigencia, utilización previa.
- Tras validación correcta se registra el acceso.

### Postergación
- Las entradas conservan validez y se asocian a la nueva fecha.
- Una entrada utilizada antes de la postergación puede reactivarse cuando
  el evento vuelva a estar activo, asociada a la nueva fecha.

### Cancelación
- Las entradas permanecen registradas e identificables para devolución.
- La **devolución económica ocurre FUERA del sistema.** El sistema solo
  registra que la entrada fue devuelta.

### Correos
Tres correos definidos: recuperación de contraseña, detalle de compra,
envío de entradas.

**El sistema NO notifica a los clientes los imprevistos de los eventos.**
La comunicación de una postergación o una cancelación se realiza fuera del
sistema, con el mismo criterio que la devolución económica. Es una decisión
de alcance deliberada, no un hueco: no proponer un cuarto correo a clientes.

Excepción ya decidida (CU-14, 01/10/2026): el Administrador puede **mandar un
reporte a buzones del dominio propio**, en un correo con el PDF y/o el Excel
adjuntos. No es una notificación a clientes ni un aviso de imprevisto: es una
acción del Administrador sobre un reporte que ya generó
(`docs/decisiones.md` → «Envío de reportes por correo»).

### Reportes
- Tipos: ventas, ingresos, entradas, clientes, accesos.
- Filtrables y exportables a PDF y Excel.
- Los reportes generados se registran con su tipo, criterios de filtro y
  fecha de generación.
- El Administrador puede enviarlos por correo al dominio propio, a varios
  buzones en un solo mensaje.

### Contraseñas
- Se guardan con BCrypt; la cuenta se bloquea tras 5 intentos fallidos.
- Complejidad: 8 caracteres o más, con al menos una mayúscula, una minúscula y
  un número: `^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$`. Máximo 72 bytes.
- Se valida en el backend y en el formulario, al registrarse, al cambiar y al
  restablecer la contraseña.

---

## Decisiones de diseño que se conservan

No contradecirlas sin consultar:

1. **Tabla única `usuario`** con columna discriminadora `rol`.
2. **Sin clase Compra/Venta ni tabla de detalle:** la reserva confirmada más el
   pago cubren ese rol.
3. **IDs `Long` autoincrementales.**
4. **Bloqueo pesimista** sobre `tipo_entrada` para el último cupo.
5. **JWT sin refresh token**, y **sin migraciones**: el esquema lo genera
   Hibernate.

Si aparece una decisión que cambia un caso de uso, el modelo de datos o el corte
en servicios, no resolverla por cuenta propia: señalarla para decidirla y
anotarla en `docs/decisiones.md` antes de seguir.

---

## Reglas de trabajo

- Responder en español. Comandos de PowerShell en una sola línea.
- **Nunca escribir contraseñas ni credenciales:** las carga el usuario en su
  `.env`.
- **El repositorio es público.** No se suben `.env`,
  `application-local.properties`, appkeys, contraseñas, IP de servidores ni
  `referencias/`. Los valores de `.env.example` van vacíos.
- **No copiar ni usar la key de Libélula de otra persona.** El manual de Libélula
  no se sube a ningún repositorio público.
- **Las pruebas con Libélula cobran dinero real**, aun con la llave de pruebas:
  se usan entradas de Bs 0,10.
- El servidor del sistema base no se toca.
- Avisar antes de instalar dependencias. Los skills nuevos van solo en este
  proyecto y preguntando antes, sin `-g` ni `-y`.
- Commit, push y PR solo cuando se pidan. El merge lo hace el usuario, después
  de avisarle que el CI está en verde.
- Infraestructura y `docs/` van directo a `main`. El resto, una rama y un PR por
  unidad de trabajo.
- No borrar archivos para siempre: los borra el usuario o van a la Papelera.
- Planes cortos, con código completo solo para lo delicado.
- Si algo mejora el código o el proyecto, a cualquier escala, pero va contra una
  regla de este archivo, avisar al usuario: la regla se puede cambiar.
- Se pueden usar subagentes en tareas independientes, como un servicio o una
  búsqueda amplia.
- Las cifras van centradas en las tablas, y las pantallas angostas van centradas.
- El caveman se activa solo con `/caveman`.

---

## Scrum

- `docs/scrum/product-backlog.md`: historias de usuario, a partir de los 15 CU
  y los 7 requisitos del enunciado.
- `docs/scrum/sprints.md`: objetivo, backlog y resultado de cada sprint. Se
  mantiene al día, porque de ahí sale la documentación final.
- **La documentación de perfil se hace al final**, en el sprint 5. Durante el
  desarrollo no se escribe el Word.
- **Flujo SDD:** `/sdd:criterios` → `writing-plans` → `/sdd:analizar` →
  implementación con TDD → `/sdd:cerrar` → PR. Antes de empezar una historia,
  leer `docs/flujo-sdd.md`.

## Uso de skills

Las reglas del proyecto ganan sobre lo que indique cualquier skill:

- **Pruebas del backend sobre H2** con el perfil `test`. No introducir
  Testcontainers.
- **Pruebas del frontend con Vitest.** No volver a Jasmine ni a Karma.
- **Formularios nuevos con Signal Forms.**

---

## Frontend (Angular)

Arquitectura acordada antes de escribir la primera pantalla. Los detalles y
motivos están en `docs/decisiones.md` → "Sistema de diseño (frontend)".

- **Tres capas dentro de `frontend/src/app/`:** `features/` (una carpeta por
  caso de uso, con su componente, sus estilos y sus tests), `core/` (estado de
  sesión, servicios HTTP, interceptores, guards, modelos) y `shared/` (piezas
  visuales sin negocio). Las dependencias apuntan hacia adentro: `core/` nunca
  importa de `features/`, y `shared/` no importa de ninguno de los dos.
- **Una pantalla por ruta, cargada con `loadComponent`.**
- **Un servicio por área de la API** (`/api/auth`, `/api/cuenta`,
  `/api/usuarios`), no uno por pantalla.
- **Lecturas con `httpResource`** (carga, error y datos como signals);
  **escrituras con `HttpClient`**.
- **Interceptores funcionales** en `provideHttpClient(withInterceptors(...))`:
  uno agrega el token, otro traduce los errores de la API y cierra la sesión
  ante un 401.
- **El token se guarda en `localStorage`**, en un único archivo de `core/`.
- **Ocultar opciones por rol es usabilidad, no seguridad:** quien autoriza es el
  backend. Los guards solo evitan mostrar una pantalla que va a fallar.
- **Estilos solo por tokens** de `frontend/src/styles/_tokens.scss`, y
  componentes estilados con `:host`. Sin colores sueltos ni librerías de
  componentes.
- **La marca es Sarao**, una empresa de eventos: en la interfaz no se menciona
  la universidad ni que es un trabajo académico.

---

## Documentación de referencia (`docs/`)

No cargar todo de entrada. Consultar según la tarea:

| Archivo | Cuándo consultarlo |
|---|---|
| `docs/casos-de-uso.md` | Antes de implementar un CU. |
| `docs/criterios-aceptacion/<CU-XX>.md` | Requisitos del CU en EARS: la spec del plan. |
| `docs/modelo-diseno-y-datos.md` | Antes de escribir una entidad. Es el modelo del sistema base: el reparto por servicio está en «Arquitectura». |
| `docs/decisiones.md` | Antes de resolver una ambigüedad. Empezar por su índice. |
| `docs/flujo-sdd.md` | Antes de empezar una historia. |
| `docs/requisitos-no-funcionales.md` | Antes de resolver algo transversal. |
