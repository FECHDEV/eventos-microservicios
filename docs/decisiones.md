# Decisiones de modelado e implementación

Decisiones heredadas del sistema base y decisiones nuevas de microservicios.
Cada una lleva su motivo, para no reabrirla.

**La sección 0 gana sobre todo lo demás.** Las decisiones de negocio del sistema
base siguen vigentes. Las técnicas se revisan una por una al migrar cada parte:
las que hablan de un solo despliegue, una sola base o una sola transacción no
valen tal como están escritas.

Organizado por subsistema, en el orden de implementación del sistema base.

---

## Índice

Una línea por sección; cada decisión lleva a su detalle.

- **[0. Microservicios](#0-microservicios)**: corte, comunicación, compra, sesión, reportes y contraseñas.
- **[Transversales](#transversales)**: [Spring Boot 4.1.1](#spring-boot-411) · [Variables de entorno con prefijo `EVENTOS_`](#variables-de-entorno-con-prefijo-eventos_) · [Despliegue con Docker: cinco servicios (seis en la nube) y un solo origen](#despliegue-con-docker-cinco-servicios-seis-en-la-nube-y-un-solo-origen) · [Tipos del diagrama que no son entidades](#tipos-del-diagrama-que-no-son-entidades) · [El sistema no notifica los imprevistos de los eventos](#el-sistema-no-notifica-los-imprevistos-de-los-eventos) · [Actor "Tiempo": dos mecanismos distintos](#actor-tiempo-dos-mecanismos-distintos) · [Pago: interfaz propia con dos implementaciones](#pago-interfaz-propia-con-dos-implementaciones) · [Requisitos de la materia: tres mejoras](#requisitos-de-la-materia-tres-mejoras)
- **[1. Seguridad](#1-seguridad)**: [`Usuario` es una sola clase con `rol` enumerado](#usuario-es-una-sola-clase-con-rol-enumerado) · [Sin `codigoAdministrador` ni `codigoPersonal`](#sin-codigoadministrador-ni-codigopersonal) · [Autenticación por JWT stateless](#autenticación-por-jwt-stateless) · [Contraseñas con BCrypt](#contraseñas-con-bcrypt) · [Recuperación de contraseña en tabla aparte](#recuperación-de-contraseña-en-tabla-aparte) · [Administrador inicial con `CommandLineRunner`](#administrador-inicial-con-commandlinerunner) · [Operaciones rechazadas en CU-09](#operaciones-rechazadas-en-cu-09) · [Registro de clientes](#registro-de-clientes) · [Inicio de sesión](#inicio-de-sesión) · [Recuperación de contraseña](#recuperación-de-contraseña) · [Datos personales (CU-04)](#datos-personales-cu-04) · [Gestión de usuarios (CU-09)](#gestión-de-usuarios-cu-09)
- **[2. Notificaciones](#2-notificaciones)**: [Envío de correo detrás de una interfaz `NotificadorCorreo`](#envío-de-correo-detrás-de-una-interfaz-notificadorcorreo) · [El servidor de correo en Docker, con buzones del propio dominio](#el-servidor-de-correo-en-docker-con-buzones-del-propio-dominio)
- **[3. Gestión de Eventos](#3-gestión-de-eventos)**: [Estado `BORRADOR`](#estado-borrador) · [CU-05 lista eventos activos y postergados](#cu-05-lista-eventos-activos-y-postergados) · [Catálogo de eventos (CU-05)](#catálogo-de-eventos-cu-05) · ["Un evento por fecha" no es una validación del sistema](#un-evento-por-fecha-no-es-una-validación-del-sistema) · [`evento.id_usuario`](#eventoid_usuario) · [`evento.fecha_original`](#eventofecha_original) · [Imagen del evento (amplía el modelo)](#imagen-del-evento-amplía-el-modelo) · [Edición de un evento según su estado](#edición-de-un-evento-según-su-estado) · [Fecha y horario al registrar o editar](#fecha-y-horario-al-registrar-o-editar) · [Restricción de edad y límite de compra](#restricción-de-edad-y-límite-de-compra) · [Cierre automático de la venta con `@Scheduled`](#cierre-automático-de-la-venta-con-scheduled) · [Tipos de entrada según el estado del evento](#tipos-de-entrada-según-el-estado-del-evento) · [Cambiar la cantidad total de un tipo](#cambiar-la-cantidad-total-de-un-tipo) · [Precio de un tipo de entrada](#precio-de-un-tipo-de-entrada) · [Nombre único dentro del evento](#nombre-único-dentro-del-evento) · [Los tipos de entrada no se eliminan](#los-tipos-de-entrada-no-se-eliminan) · [Transiciones de estado del evento (CU-12)](#transiciones-de-estado-del-evento-cu-12) · [La venta se abre al activar y se cierra al cancelar](#la-venta-se-abre-al-activar-y-se-cierra-al-cancelar) · [Fecha nueva al postergar](#fecha-nueva-al-postergar) · [Fecha de nacimiento del Cliente](#fecha-de-nacimiento-del-cliente) · [Espera máxima de un bloqueo en Eventos y Venta](#espera-máxima-de-un-bloqueo-en-eventos-y-venta) · [La entrada conoce su reserva](#la-entrada-conoce-su-reserva) · [Reactivar entradas utilizadas: cuando exista la Venta](#reactivar-entradas-utilizadas-cuando-exista-la-venta) · [Bloqueo del evento al cambiar su estado](#bloqueo-del-evento-al-cambiar-su-estado)
- **[4. Venta](#4-venta)**: [Una compra es de un solo tipo de entrada](#una-compra-es-de-un-solo-tipo-de-entrada) · [Límite de compra acumulado por cliente y evento](#límite-de-compra-acumulado-por-cliente-y-evento) · [Restricción de edad contra la fecha del evento](#restricción-de-edad-contra-la-fecha-del-evento) · [`cantidad_disponible` se descuenta al reservar](#cantidad_disponible-se-descuenta-al-reservar) · [Concurrencia sobre el último cupo: bloqueo pesimista](#concurrencia-sobre-el-último-cupo-bloqueo-pesimista) · [Precio congelado en `reserva.precio_unitario`](#precio-congelado-en-reservaprecio_unitario) · [`reserva` y `entrada` no tienen FK a `evento`](#reserva-y-entrada-no-tienen-fk-a-evento) · [Adquirir entradas (CU-06)](#adquirir-entradas-cu-06) · [Consultar entradas (CU-08)](#consultar-entradas-cu-08)
- **[5. Pagos](#5-pagos)**: [Proveedor: Libélula](#proveedor-libélula) · [Adaptador de Libélula](#adaptador-de-libélula) · [El QR del proveedor llega como texto o como imagen](#el-qr-del-proveedor-llega-como-texto-o-como-imagen) · [Estados del pago: PENDIENTE, PAGADO, EXPIRADO, ERROR](#estados-del-pago-pendiente-pagado-expirado-error) · [El vencimiento de 10 minutos lo controla el sistema](#el-vencimiento-de-10-minutos-lo-controla-el-sistema) · [Pago tardío](#pago-tardío) · [Verificación del pago e idempotencia](#verificación-del-pago-e-idempotencia) · [Dos correos al confirmar la compra](#dos-correos-al-confirmar-la-compra) · [`codigo_qr` significa dos cosas](#codigo_qr-significa-dos-cosas) · [Realizar pago (CU-07)](#realizar-pago-cu-07) · [CU-13 devuelve solo entradas activas](#cu-13-devuelve-solo-entradas-activas) · [Gestionar devoluciones (CU-13)](#gestionar-devoluciones-cu-13)
- **[6. Control de Acceso](#6-control-de-acceso)**: [`validarEntrada(codigoQR, evento)`](#validarentradacodigoqr-evento) · [Vigencia de una entrada](#vigencia-de-una-entrada) · [Varios accesos por entrada](#varios-accesos-por-entrada) · [Validar entrada (CU-15)](#validar-entrada-cu-15)
- **[7. Reportes](#7-reportes)**: [Criterios de filtro por tipo](#criterios-de-filtro-por-tipo) · [Ingresos: bruto, devuelto y neto](#ingresos-bruto-devuelto-y-neto) · [Gestionar reportes (CU-14)](#gestionar-reportes-cu-14) · [Envío de reportes por correo (CU-14)](#envío-de-reportes-por-correo-cu-14)
- **[8. Sistema de diseño (frontend)](#8-sistema-de-diseño-frontend)**: [La aplicación es el producto de una empresa, no un trabajo académico](#la-aplicación-es-el-producto-de-una-empresa-no-un-trabajo-académico) · [Dirección visual: oscura, con ámbar de escenario](#dirección-visual-oscura-con-ámbar-de-escenario) · [Paleta](#paleta) · [Modo claro, opcional](#modo-claro-opcional) · [Tres estilos y modo día/noche](#tres-estilos-y-modo-díanoche) · [Tipografía](#tipografía) · [Reglas que valen para toda la interfaz](#reglas-que-valen-para-toda-la-interfaz) · [Búsqueda en el encabezado](#búsqueda-en-el-encabezado) · [Contador de visitas por página](#contador-de-visitas-por-página) · [Dónde viven estas decisiones en el código](#dónde-viven-estas-decisiones-en-el-código) · [Decisiones técnicas del frontend](#decisiones-técnicas-del-frontend)

---

## 0. Microservicios

Decidido con el usuario el 30/10/2026.

### Corte en servicios

Cuatro microservicios (Seguridad, Eventos y Venta, Pagos, Notificaciones), un
gateway y tres bases. El reparto de casos de uso y tablas está en `CLAUDE.md`.

- **Bases separadas:** si dos servicios comparten tablas, un cambio en una rompe
  al otro. Para ahorrar memoria viven en un solo contenedor MySQL, cada una con
  su usuario, sin permisos sobre las otras y sin llaves foráneas entre ellas.
- **Eventos y Venta juntos:** el último cupo se protege con un bloqueo dentro de
  una transacción, y separarlos complicaría esa garantía sin aportar a los
  requisitos.

### Comunicación y fallas

- **REST interno, sin broker:** menos piezas, memoria y riesgo. Las llamadas
  internas llevan clave de servicio y no se publican en el gateway.
- **Toda llamada interna lleva plazos.** Una caída de Pagos no tumba el catálogo.
- **Relaciones entre servicios por identificador:** `Evento`, `Reserva`,
  `Entrada`, `Acceso` y `Reporte` hacia `Usuario`; `Pago` hacia `Reserva`;
  `Entrada` guarda el id del pago y una llave foránea a su reserva, en la misma base.
- **Datos de otro servicio en listas y reportes:** se piden en lote, nunca una
  llamada por fila.

### Compra

- **Confirmación en dos pasos:** Pagos marca el pago PAGADO y llama a
  `POST /interno/reservas/{id}/confirmacion` en Eventos y Venta. La llamada es
  **idempotente**, y la tarea de cada minuto reintenta si falló. Si no hay cupo
  (pago tardío), Pagos marca el pago para devolución.
- **Vencimiento:** Pagos consulta a Eventos y Venta si la reserva sigue vigente
  antes de cobrar. La liberación perezosa queda en el servicio dueño del cupo.
- **Correos:** los servicios llaman a Notificaciones después del commit. Si falla,
  la compra no se pierde.
- **Tareas programadas:** `CierreAutomaticoDeVenta` va a Eventos y Venta;
  `ConciliacionAutomaticaDePagos`, a Pagos.

### Sesión

Decidido de nuevo el 30/10/2026, en lugar de validar la firma en el gateway: solo
Seguridad tiene la clave JWT.

- El gateway manda cada token a `POST /interno/sesiones` y recibe el id y el rol que
  la cuenta tiene ahora, para que un cambio de rol, una cuenta desactivada o una
  contraseña cambiada rijan de inmediato. Se mide el costo y se cachea unos segundos
  si hace falta.
- Pasa la identidad en `X-Usuario-Id` y `X-Usuario-Rol`, con `X-Clave-Servicio`, y
  borra esos encabezados si los manda el cliente. Un servicio solo los acepta con la
  clave.
- Un token inválido no corta la petición: sigue sin identidad, las rutas públicas
  responden y las protegidas dan 401. Si Seguridad falla, el gateway responde 503: un
  401 le cerraría la sesión a un usuario con token vigente.
- El gateway fija `X-Forwarded-For`: los límites de intentos ven la IP real y el
  cliente no puede inventarla. Necesita `trusted-proxies`: sin esa propiedad, Spring
  Cloud Gateway borra el encabezado y todos los clientes comparten una IP.

### Reportes

Quedan en Eventos y Venta. Los datos de ventas e ingresos se piden a Pagos con
una consulta interna por rango, y los nombres de clientes a Seguridad, en lote.

### Complejidad de la contraseña

Regex `^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$`, en el backend y en el formulario,
al registrarse, al cambiar y al restablecer la contraseña. Sigue el máximo de 72
bytes de BCrypt. Cumple el requisito de complejidad sin exigir símbolos.

### Estructura del repositorio

- **Un proyecto Maven por servicio, sin pom padre:** cada uno compila y se
  despliega solo.
- **Un solo `servicios/Dockerfile`:** todos los servicios se arman igual.
- **Gateway con Spring Cloud Gateway Server WebMVC:** el mismo modelo de Spring
  MVC que los servicios, sin programación reactiva.
- **Paquete `com.sarao`:** el código es público y va con la marca.
- **Las bases se crean con `mysql/crear-bases.sh`**, que solo corre con el volumen
  vacío. Cambiar una contraseña en `.env` no la cambia en una base ya creada.

### Versiones

Spring Cloud 2025.1.x: `start.spring.io` la declara compatible con Spring Boot
4.0 y 4.1 (verificado el 30/10/2026).

---

## Transversales

### Spring Boot 4.1.1

`start.spring.io` ya no ofrece la línea 3.x. Algunos starters cambiaron de
nombre (`spring-boot-starter-webmvc` en lugar de `-web`; los de prueba están
divididos por módulo), y la mayoría de las referencias externas están escritas
para 3.x: tenerlo en cuenta al buscar ejemplos.

### Variables de entorno con prefijo `EVENTOS_`

La máquina de desarrollo tiene `DB_USER`, `DB_PASSWORD` y `DB_URL` de otro
proyecto definidas a nivel de sistema. Como las variables de entorno le ganan a
los valores por defecto de `application.properties`, con nombres genéricos la
aplicación se conectaba como `root` a la base equivocada sin avisar.

### Despliegue con Docker: cinco servicios (seis en la nube) y un solo origen

`docker-compose.yml` levanta cinco servicios: `base` (MySQL 8.0), `backend` (la
API), `frontend` (nginx con el Angular compilado), `correo` (docker-mailserver)
y `webmail` (Roundcube). nginx reenvía `/api` a la API, así el navegador ve un
solo origen: no hace falta CORS y el token viaja igual que en desarrollo, donde
lo hace `proxy.conf.json`. Los secretos van en un `.env` que no se versiona
(`.env.example` dice cuáles son).

- **Lo que se publica:** en la PC, solo `frontend` (puerto 80) y `webmail` (solo
  en `127.0.0.1:8025`). La base, la API y el servidor de correo quedan en la red
  interna (→ Notificaciones: «El servidor de correo en Docker…»).
- **En la nube se suma un sexto servicio:** `docker-compose.nube.yml` agrega
  **Caddy**, el único que publica puertos (80 y 443). Pide el certificado HTTPS a
  Let's Encrypt y entrega `https://DOMINIO` a `frontend` y
  `https://correo.DOMINIO` a `webmail`. Ese archivo también quita los puertos
  de esos dos servicios (`ports: !reset []`). El dominio sale de
  `SARAO_DOMINIO` (sslip.io con la IP del servidor). La instalación la hace
  `scripts/instalar-servidor.sh`.
- **Perfil `prod`** (`application-prod.properties`): sin el SQL en los registros
  y con `server.forward-headers-strategy=native`, para que el límite de intentos
  vea la IP real del cliente y no la del proxy.
- **Los datos en volúmenes:** la base, las imágenes de los eventos, los buzones
  y la configuración del correo, el webmail y, en la nube, los certificados de
  Caddy. Sobreviven a rehacer las imágenes. El esquema lo sigue generando
  Hibernate (`ddl-auto=update`), sin migraciones.
- **La imagen de la API no corre las pruebas:** corren en desarrollo, antes de
  cada PR y en GitHub Actions. La API corre sin privilegios de root.

**Motivo:** `CLAUDE.md` fija Docker y despliegue en la nube o en un VPS. Un solo
origen reproduce en producción cómo funciona el desarrollo. Decidido el
23/09/2026 con tres contenedores (base, API y nginx). El correo y el webmail se
sumaron con el adaptador SMTP, y Caddy con el despliegue con HTTPS (PR #26 y
los commits de despliegue a `main`).

### Tipos del diagrama que no son entidades

`DatosPersonales`, `Filtro`, `ResultadoValidacion` y `Archivo` aparecen en
firmas de operaciones pero no están modelados. Son DTOs del paquete `dto/` y se
crean al llegar a cada CU.

### El sistema no notifica los imprevistos de los eventos

Ver `CLAUDE.md` → Reglas de negocio → Correos (regla y motivo completos ahí).

### Actor "Tiempo": dos mecanismos distintos

El modelo de casos de uso trata a "Tiempo" como un único actor que "dispara"
tanto el cierre de venta (CU-12 FA-01) como la liberación de reservas (CU-06
FA-04). En la implementación son mecanismos distintos: el cierre de venta usa
una tarea `@Scheduled` real (ver "2. Gestión de Eventos" más abajo); la
liberación de reservas es verificación perezosa, sin disparador activo (ver
`plan-implementacion.md`, Paso 0, decisión 6). El actor conceptual es el
mismo; el mecanismo técnico detrás no.

### Pago: interfaz propia con dos implementaciones

`IPago` tiene un **simulador local**, que se usa por defecto en desarrollo y
pruebas, y un **adaptador de Libélula**, que apunta a su entorno de testing.
Se elige con una propiedad de configuración (`simulador` | `libelula`), y las
credenciales del adaptador van en variables `EVENTOS_PAGO_*`. Reglas y
motivos en "4. Pagos".

### Requisitos de la materia: tres mejoras

El enunciado del proyecto (`referencias/DESCRIPCIÓN PROYECTO 1.pdf`, 12/10/2026)
pide siete cosas. Cuatro ya estaban: roles con permisos, contraseñas cifradas
con longitud y bloqueo, reportes en PDF por correo a varios buzones, y pago en
línea en un servidor en la nube. Faltaban tres, que entran como **mejoras
transversales**, no como CU nuevos, cada una en su rama y su PR:

1. **Búsqueda en el encabezado** (→ Sistema de diseño).
2. **Contador de visitas por página**, en el pie (→ Sistema de diseño).
3. **Tres temas (niños, jóvenes, adultos) con día y noche según la hora**
   (→ Sistema de diseño).

La complejidad de la contraseña y el bloqueo quedan como están (decidido con el
usuario el 12/10/2026): mínimo de 8 caracteres y el límite de intentos fallidos
por correo e IP (→ Seguridad: «Inicio de sesión»).

---

## 1. Seguridad

### `Usuario` es una sola clase con `rol` enumerado

`@Enumerated(EnumType.STRING)` sobre un campo `rol`, sin `@Inheritance` ni
subclases Java.

**Motivo:** CU-09 permite cambiar el rol de un usuario existente. Con
`SINGLE_TABLE`, el discriminador fija la clase Java al insertar y JPA no deja
actualizarlo: cambiar el rol obligaría a borrar y recrear la fila, lo que cambia
el `id_usuario` y rompe el historial en `reserva`, `entrada`, `acceso` y
`reporte`.

Las operaciones de las subclases van a la capa de servicios:
`Cliente.consultarEntradas()` a `EntradaService`,
`Administrador.generarReporte()` a `ReporteService` y
`PersonalAutorizado.validarEntrada()` a `AccesoService`.

### Sin `codigoAdministrador` ni `codigoPersonal`

No aparecen en ninguna ficha ni en ninguna firma, y `acceso.id_usuario` ya
identifica a quien valida cada entrada.

### Autenticación por JWT stateless

Spring Security con el token en el header `Authorization`. La documentación
solo exige que el control de acceso se valide en el backend. Las dependencias
`io.jsonwebtoken` ya están en el `pom.xml`.

- El token vence a las **8 horas**, sin refresh token: cubre una jornada, y el
  personal autorizado no queda afuera a mitad de un evento.
- Se firma con HS256 y la clave de `EVENTOS_JWT_SECRETO` (base64, al menos 256
  bits). No tiene valor por defecto: si falta o es corta, la aplicación no
  arranca, porque con una clave conocida cualquiera podría fabricar tokens.
- El token lleva el id del usuario (`sub`), `iat`, `exp` y `hc`, una huella de
  la contraseña: HMAC-SHA256 del hash BCrypt con la clave del token, truncado a
  16 bytes, que no permite reconstruir el hash. El rol, el estado y la huella se
  comparan con la base en cada petición. Si CU-09 desactiva una cuenta o le
  cambia el rol, o CU-03/CU-04 le cambian la contraseña, vale desde la siguiente
  petición y no recién al vencer el token.
- Un token inválido o vencido no autentica, pero tampoco bloquea la petición:
  los endpoints públicos la atienden igual, para que un token viejo guardado en
  el frontend no impida volver a iniciar sesión.

### Contraseñas con BCrypt

A través del `PasswordEncoder` de Spring Security. Los 60 caracteres del hash
entran en `contrasena VARCHAR(255)`.

### Recuperación de contraseña en tabla aparte

CU-03 FA-02 habla de un plazo vencido y no había dónde guardar el token. Tabla
`recuperacion_contrasena` en lugar de dos columnas en `usuario`: permite
invalidar tokens viejos sin pisar el vigente y no deja columnas vacías en todos
los usuarios que nunca recuperaron la contraseña.

**Plazo de validez: 1 hora** desde `fecha_creacion`.

### Administrador inicial con `CommandLineRunner`

Al arrancar, si no existe ningún usuario con rol ADMINISTRADOR, crea uno con la
contraseña hasheada leída de `EVENTOS_ADMIN_PASSWORD`. Es idempotente, convive
con `ddl-auto` sin pelear por el orden de ejecución, y no deja un hash escrito
en un archivo versionado como haría un `data.sql`.

- El correo sale de `EVENTOS_ADMIN_CORREO`; el nombre ("Administrador") y el
  apellido ("Inicial") son fijos.
- Si no hay ningún administrador y faltan las variables, o no cumplen las
  reglas de correo y contraseña del registro, la aplicación **no arranca**: un
  despliegue sin administradores no se puede recuperar desde ningún CU. Si ya
  existe un administrador, las variables no se leen.
- Si el correo configurado ya pertenece a otra cuenta, tampoco arranca. No se
  promueve esa cuenta: los roles solo los asigna un Administrador (CU-09).

### Operaciones rechazadas en CU-09

1. Un administrador no puede modificar su propio rol.
2. Un administrador no puede desactivar su propia cuenta.
3. No se puede quitar el rol ni desactivar al **último administrador activo**.
4. No se puede cambiar el rol de una cuenta desactivada; primero hay que
   reactivarla.

La tercera es la importante: ningún CU permite recuperarse de un sistema sin
administradores.

### Registro de clientes

- La cuenta queda **activa** al registrarse, sin confirmación por correo.
- `fecha_nacimiento` es obligatoria cuando `rol = CLIENTE`, validada en el
  servicio. La columna es anulable porque los otros roles no la tienen, y sin
  esta validación `verificarRestriccionEdad()` puede recibir `null`.
- Se rechaza una `fecha_nacimiento` posterior al día en curso. No hay edad
  mínima para registrarse: la edad se valida por evento al comprar (CU-06).
- El correo se guarda en minúsculas y sin espacios alrededor, y los duplicados
  se buscan igual: `Juan@mail.com` y `juan@mail.com` son la misma cuenta
  (FA-02). CU-02 debe normalizarlo del mismo modo al iniciar sesión.
- Contraseña: mínimo 8 caracteres y máximo 72 bytes en UTF-8. El máximo sale de
  BCrypt, que ignora lo que pasa de 72 bytes; sin él, dos contraseñas largas
  distintas serían equivalentes.

### Inicio de sesión

- El correo se normaliza igual que en el registro antes de buscar la cuenta.
- Correo inexistente y contraseña incorrecta devuelven el mismo mensaje, y el
  correo inexistente también ejecuta una comparación BCrypt: así no se revela
  qué correos tienen cuenta, ni por el texto ni por la demora.
- La cuenta desactivada (CU-02 FA-02) solo se informa cuando la contraseña es
  correcta; con contraseña incorrecta se responde como FA-01. El estado de una
  cuenta no se le revela a quien solo conoce el correo.
- Una contraseña de más de 72 bytes en UTF-8 se rechaza como incorrecta aunque
  BCrypt la daría por buena: la truncaría a 72 bytes y podría coincidir con una
  registrada de exactamente 72. Se compara igual, para que la demora de la
  respuesta no cambie.
- **Límite de intentos.** 5 intentos fallidos para el mismo correo desde la
  misma IP en 15 minutos bloquean ese correo desde esa IP durante 15 minutos
  (429 `INTENTOS_EXCEDIDOS`), aunque la contraseña sea correcta. Un inicio de
  sesión exitoso reinicia el conteo.
  - **Restablecer la contraseña (CU-03) también lo reinicia,** para ese correo
    desde todas las IPs. Sin esto, quien falló 5 veces y por eso recuperó la
    contraseña recibiría 429 al entrar con la nueva. Es seguro porque restablecer
    exige el token que llegó al correo del dueño.
  - **Por correo e IP:** nadie puede bloquear la cuenta de otro desde su propia
    dirección.
  - **Los correos inexistentes también cuentan,** para no revelar cuáles
    existen.
  - **Contadores en memoria:** se pierden al reiniciar y suponen una sola
    instancia del backend.
  - **Detrás de un proxy inverso,** hay que configurar el reenvío de la IP real
    (`server.forward-headers-strategy`) al desplegar: si no, todos los clientes
    comparten la IP del proxy.

### Recuperación de contraseña

- Mismo mensaje siempre al solicitar la recuperación, exista o no el correo:
  no revela cuentas, igual criterio que "Inicio de sesión".
- Token: cadena aleatoria de 256 bits (`SecureRandom`, codificada en
  base64url), sin relación con el JWT ni con ningún dato del usuario. No se
  usa UUID: la columna guarda un secreto, no un identificador, y solo la
  versión 4 de UUID es impredecible.
- **La base guarda el SHA-256 del token, no el token.** El token original
  solo viaja en el correo; al restablecer se hashea lo recibido y se busca
  por ese valor. Así, quien logre leer la base no puede pedir la recuperación
  de una cuenta ajena (por ejemplo, la del Administrador) y usar el token de
  la fila recién creada. Alcanza con SHA-256 y no hace falta BCrypt: con 256
  bits aleatorios no se puede probar por fuerza bruta. Los 64 caracteres en
  hexadecimal entran en `token VARCHAR(255) UNIQUE`.
- Vigencia 1 hora desde `fecha_creacion` (ver "Recuperación de contraseña en
  tabla aparte", más arriba).
- Una solicitud nueva invalida (`usado = true`) cualquier token vigente
  anterior de la misma cuenta: solo el último sirve.
- Igual que al iniciar sesión, no revela el estado de la cuenta: se puede
  restablecer la contraseña de una cuenta DESACTIVADA sin reactivarla.
- El plazo vencido (CU-03 FA-02) responde siempre el mismo mensaje para
  token inexistente, vencido o ya usado, sin distinguir el motivo.
- **Límite de solicitudes.** 3 solicitudes para el mismo correo desde la
  misma IP en 15 minutos bloquean ese correo desde esa IP durante 15 minutos
  (429 `SOLICITUDES_EXCEDIDAS`), igual mecanismo que el límite de intentos de
  inicio de sesión pero con umbral más bajo porque cada solicitud aceptada
  dispara un correo real. Los correos inexistentes también cuentan.
- Con un correo inexistente la solicitud no inserta ninguna fila, así que
  responde unos milisegundos antes. Se acepta: a diferencia de la comparación
  BCrypt del inicio de sesión, esa diferencia se pierde en la variación de la
  red, y el límite de solicitudes impide repetir la medición las veces que
  haría falta para distinguirla.

### Datos personales (CU-04)

- Se pueden modificar nombre, apellido y teléfono, con las mismas reglas del
  registro. El rol y el estado solo los cambia un Administrador (CU-09).
- **El correo no se modifica:** es la identidad con la que se inicia sesión y el
  destino de la recuperación de contraseña (CU-03).
- **La fecha de nacimiento no se modifica:** CU-06 la usa para validar la edad
  mínima de cada evento, y si el Cliente pudiera cambiarla esa validación
  dependería de lo que declare. Un error de tipeo al registrarse no se corrige
  desde el sistema.
- **Cambiar la contraseña exige la actual,** para que quien tenga una sesión
  ajena abierta no pueda quedarse con la cuenta. La nueva sigue las reglas del
  registro, y una actual de más de 72 bytes se trata como incorrecta, igual que
  al iniciar sesión.
- **Una contraseña actual incorrecta cuenta en el límite de intentos del inicio
  de sesión** (mismo correo e IP): adivinarla por esta vía no da más intentos que
  por el inicio de sesión. Mientras ese correo esté bloqueado desde esa IP, el
  cambio se rechaza aunque la actual sea correcta, y un cambio exitoso reinicia
  el conteo, igual que un inicio de sesión exitoso.
- **Tras cambiar la contraseña, la respuesta trae un token nuevo.** La huella
  `hc` invalida todos los tokens anteriores, también el de la sesión que hizo el
  cambio: así el Usuario sigue sin volver a entrar, y las demás sesiones abiertas
  se cierran.

### Gestión de usuarios (CU-09)

- **Cada operación rechazada responde 409 con su propio código** y un mensaje
  que dice el motivo (ver "Operaciones rechazadas en CU-09", más arriba). Quien
  pregunta es un Administrador: no hay nada que ocultar, y así sabe qué corregir.
- **Solo se puede pasar a CLIENTE una cuenta con fecha de nacimiento,** y al
  dejar de ser CLIENTE la fecha se conserva. `fecha_nacimiento` es obligatoria
  para un CLIENTE (ver "Registro de clientes") y CU-09 cambia el rol o el
  estado, no carga datos. Toda cuenta registrada por sí misma nace CLIENTE con
  fecha, así que en la práctica solo queda afuera el Administrador inicial. Se
  rechaza con 409, igual que las otras cuatro.
- **Reactivar una cuenta no tiene reglas extra:** el usuario vuelve a iniciar
  sesión con la contraseña que tenía.
- **Listado paginado:**
  - 20 usuarios por página, 100 como máximo, del registro más reciente al más
    antiguo.
  - Se filtra por rol y por estado, y se busca un texto en correo, nombre y
    apellido sin distinguir mayúsculas.
  - Muestra lo necesario para identificar la cuenta y decidir: nombre,
    apellido, correo, rol, estado y fecha de registro. No muestra el teléfono
    ni la fecha de nacimiento (RNF-03).
- **El último administrador activo se protege con bloqueo.** Sin operaciones
  simultáneas la regla no se alcanza, porque quien opera es un administrador
  activo distinto del afectado. Pero si dos administradores se quitan el rol o
  se desactivan entre sí al mismo tiempo, cada uno ve al otro y el sistema queda
  sin administradores. La operación bloquea las filas de los administradores
  activos (bloqueo pesimista) antes de contarlos: la segunda espera, vuelve a
  contar y se rechaza.
- **Los cambios rigen desde la siguiente petición del usuario afectado,** sin
  código propio: el filtro del token compara el rol y el estado con la base (ver
  "Autenticación por JWT stateless"). Una petición que ya estaba en curso
  termina con los permisos con los que empezó.
- **La tabla `usuario` lleva un índice por `rol` y `estado`.** El modelo físico
  deja los índices a la implementación (`modelo-diseno-y-datos.md` 3). Sin él, en
  MySQL el bloqueo de administradores activos no puede usar índice y bloquea
  todas las filas que recorre, de modo que cada operación de CU-09 haría esperar
  a un registro nuevo (CU-01) o a un cambio de contraseña (CU-04).
- **Reactivar no cuenta ni bloquea a los administradores,** porque nunca puede
  reducir su número. Desactivar y cambiar el rol sí, y siempre en el orden
  administradores → cuenta afectada, para que dos operaciones simultáneas se
  esperen en vez de bloquearse mutuamente.
- **Si se agota la espera de un bloqueo, la respuesta es 409
  `OPERACION_SIMULTANEA`** y se puede reintentar. El diseño provoca esa espera a
  propósito, así que el conflicto se informa con el formato de la API en lugar de
  salir como un error inesperado.
- **Aceptado tal como está:** al reactivar una cuenta, los tokens emitidos antes
  de desactivarla vuelven a valer hasta que vencen (8 horas como máximo), porque
  el filtro solo compara rol, estado y huella de la contraseña. Cerrarlo exigiría
  un dato nuevo en `usuario` — modelo cerrado —. Para cortar de verdad las
  sesiones de una cuenta hay que cambiarle la contraseña. Decidido con el usuario
  el 23/09/2026: no se cambia el modelo por esto.

---

## 2. Notificaciones

### Envío de correo detrás de una interfaz `NotificadorCorreo`

Vive en el servicio Notificaciones (31/10/2026): los demás servicios lo llaman por
`/interno/correos`, reciben 202 y no esperan la entrega. Seguridad pide el correo de
recuperación después del commit y en segundo plano, sin propagar fallos.

Mismo patrón que `IPago` (`plan-implementacion.md` → Paso 0, decisión 4): un
simulador que no manda nada de verdad, solo registra qué se habría enviado, y
un adaptador SMTP real contra el servidor propio (`plan-implementacion.md` →
Paso 0, decisión 5). Se elige con `eventos.correo.proveedor`: `simulador` (por
defecto, y siempre en las pruebas) o `smtp` (en Docker).

Tres correos de alcance (`CLAUDE.md` → Reglas de negocio → Correos):
recuperación de contraseña (CU-03), detalle de compra y envío de entradas.

El simulador registra el destinatario, y el token y los códigos solo con su logger
en DEBUG. Sirve para desarrollo y pruebas; **no debe quedar activo en producción**.

**El envío no puede cambiar la respuesta de CU-03.** La solicitud de
recuperación responde igual exista o no la cuenta, pero el correo solo se
manda si existe: si el envío tardara o fallara dentro de la petición, la
demora o un 500 revelarían qué correos tienen cuenta. El adaptador SMTP tiene
que enviar en segundo plano y registrar los fallos en el log sin propagarlos.
El simulador no lo necesita, porque solo escribe en el log.

### El servidor de correo en Docker, con buzones del propio dominio

El servidor de correo (docker-mailserver) y su webmail (Roundcube) son dos
servicios más del `docker-compose.yml`, en el perfil `correo` para no consumir
memoria en desarrollo, en el mismo servidor que la aplicación,
en lugar de las dos VMs del planteo original. Los destinatarios de la
demostración son buzones del propio dominio (`EVENTOS_CORREO_DOMINIO`, por
defecto `mail.com`: fabio@mail.com), leídos en el webmail. Así todo se entrega
dentro del servidor: el puerto 25 no se publica, y no hacen falta DNS, MX, SPF
ni DKIM, que un VPS suele bloquear o complicar. Una cuenta con un correo de
otro dominio no recibe los mensajes, sin afectar la compra ni la recuperación.

- **Sin contraseña para la API:** el servidor acepta envíos sin autenticación
  solo desde las redes de Docker (`PERMIT_DOCKER=connected-networks`), y no
  publica puertos. La API no guarda otra credencial.
- **Nada sale del servidor:** Postfix rechaza todo destinatario de otro
  dominio (`default_transport = error` en `correo/postfix-main.cf`). Sin eso,
  en un VPS con el puerto 25 de salida abierto, el correo para una cuenta de
  Gmail saldría firmado como un dominio ajeno (`mail.com` es un proveedor
  real), y cualquier contenedor de la red interna podría usarlo de relay. La
  API recibe el rechazo al instante y lo registra.
- **El webmail solo en esta máquina:** se publica en `127.0.0.1`, porque va por
  HTTP; en un servidor va detrás del proxy con HTTPS.
- **Buzones:** los crea quien despliega, con
  `docker compose exec correo setup email add …`, porque llevan contraseña.
  `no-responder` es el remitente; `postmaster` es un alias hacia él.
- **Los tres correos** van en texto plano y en HTML con la marca Sarao, armados
  en Java sin motor de plantillas. En el HTML se escapa todo dato que escribió
  un usuario (nombre, evento, lugar, tipo), y el asunto es fijo: no lleva datos
  del usuario. El de entradas lleva los códigos y el enlace a Mis entradas, no
  la imagen del QR, que se dibuja en el cliente (→ Consultar entradas).
- **Enlaces:** se arman con `eventos.correo.url-del-sitio` (`EVENTOS_SITIO_URL`).
- **Fallos:** se registran con el destinatario y el motivo, nunca con el
  contenido, porque el de recuperación lleva el token.

---

## 3. Gestión de Eventos

### Estado `BORRADOR`

El evento nace en borrador, se le definen los tipos de entrada (CU-11) y recién
entonces se activa (CU-12), que verifica que tenga al menos uno.

**Motivo:** "activar" no tenía estado de origen, y entre CU-10 y CU-11 el evento
existía sin tipos de entrada, violando la composición `Evento 1 — TipoEntrada
1..*`.

### CU-05 lista eventos activos y postergados

Un postergado sigue vendiendo entradas. Uno en borrador todavía no se puede
comprar. Uno cancelado tampoco, y quien ya tiene entradas lo ve por CU-08, que
es el camino a la devolución.

### Catálogo de eventos (CU-05)

- **Filtros:** un texto, que se busca en el nombre y el lugar, y un rango de
  fechas «desde» y «hasta», con los extremos incluidos. No hay filtro por precio
  ni por categoría: el modelo no tiene categorías, y el precio es de cada tipo de
  entrada.
- **Sin eventos terminados:** además de BORRADOR y CANCELADO, el catálogo oculta
  los eventos cuya fecha más hora de fin ya pasó, con el cruce de medianoche. No
  cambia ningún estado: el evento sigue ACTIVO o POSTERGADO, y su detalle
  responde 404 igual que uno que no existe. La administración lo marca
  «Terminado» junto al estado, con la misma cuenta: es una etiqueta, no un
  estado del modelo.
- **Disponibilidad:** el detalle muestra de cada tipo su precio y si está
  «Disponible» o «Agotado», no cuántas quedan. Sale de `cantidad_disponible`,
  que ya descuenta las reservas vigentes; las vencidas se liberan antes de
  leerla (ver «Adquirir entradas (CU-06)»).
- **Portada:** el catálogo es `/`, público. La bienvenida con los accesos del
  Administrador y del Personal autorizado pasa a una pantalla propia.

**Motivo:** la ficha dice «criterios de búsqueda o filtrado» sin nombrarlos, y
RF-20 habla de eventos disponibles: uno que ya pasó no lo está aunque su estado
siga siendo ACTIVO. Decidido con el usuario el 21/09/2026, al generar los
criterios de CU-05.

### "Un evento por fecha" no es una validación del sistema

`CLAUDE.md` define que la misma actividad en otra fecha es otro evento, no
una segunda función. No hay `UNIQUE` en el modelo ni un chequeo en ningún CU
que lo haga cumplir: queda a criterio del Administrador al crear el evento,
no a una regla que el sistema imponga.

### `evento.id_usuario`

Respalda la asociación `Administrador 1 — 0..* Evento`, que el modelo físico no
tenía: registra quién creó cada evento.

### `evento.fecha_original`

Se completa al postergar por primera vez. `postergar()` sobrescribe `fecha`, y
sin esta columna la fecha anterior se perdía.

### Imagen del evento (amplía el modelo)

El evento puede tener **una imagen opcional**. Es una columna nueva,
`evento.imagen VARCHAR(100) NULL`, que el modelo físico de Fase 4 no tenía.
**Hay que reflejarla en el documento académico** (clase `Evento` y tabla
`evento`).

- **Se sube al backend.** El Administrador elige un archivo; el backend
  comprueba que sea JPG, PNG o WebP por su contenido (los primeros bytes), no
  por la extensión ni por el tipo que declara el navegador, y que no pase de
  2 MB.
- **El gateway no la interpreta:** pasa la subida entera (`spring.servlet.multipart.enabled=false`)
  y el límite lo aplica Eventos y Venta, que da el aviso `IMAGEN_DEMASIADO_GRANDE`.
- **Se guarda en una carpeta del servidor**, configurable con
  `EVENTOS_IMAGENES_DIRECTORIO` (un volumen en Docker). El nombre del archivo lo
  genera el sistema; el que trae la persona se descarta. La tabla guarda solo
  ese nombre.
- **Se sirve desde el backend**, por el evento al que pertenece, nunca por una
  ruta armada con lo que manda el cliente.
- **Se reemplaza o se quita** mientras el evento se pueda editar (CU-10).
  Reemplazarla borra el archivo anterior.
- Sin imagen, las pantallas muestran un fondo neutro.

**Motivo:** el sistema de diseño (sección 8) cuenta con las fotos de los
eventos como la única fuente de color fuerte, y ni la ficha de CU-10 ni el
modelo tenían dónde guardarlas. Se descartó pegar una URL externa (la imagen
dependería de un sitio ajeno, que además vería cada visita del cliente) y
guardarla en la base (agranda la base y sus respaldos). Decidido con el
usuario el 18/09/2026, al generar los criterios de CU-10.

### Edición de un evento según su estado

- **BORRADOR:** se edita todo.
- **ACTIVO y POSTERGADO:** se edita todo menos la fecha y las horas. Cambiar la
  fecha de un evento que ya vende es postergarlo, y eso es CU-12.
- **CANCELADO:** no se edita.

**Motivo:** CU-10 dice "modificar" sin estados y CU-12 tiene su propia
operación `postergar(nuevaFecha)`. Sin esta regla, CU-10 sería una forma de
postergar sin guardar `fecha_original`.

### Fecha y horario al registrar o editar

La fecha no puede ser anterior a hoy. La hora de fin puede ser menor que la de
inicio: el evento termina al día siguiente (un concierto de 22:00 a 03:00).
Inicio y fin iguales se rechazan. El cierre automático de la venta usa ese fin,
del día siguiente si cruza la medianoche.

### Restricción de edad y límite de compra

Vacíos significan "sin restricción" y "sin límite" (CU-10 FA-02 y FA-03). Si
tienen valor, la edad va de 1 a 99 años y el límite es de 1 entrada o más. El 0
no se acepta, para que "sin límite" tenga una sola forma de decirse.

### Cierre automático de la venta con `@Scheduled`

Una tarea que corre cada minuto cierra los eventos cuya `fecha` más `hora_fin`
ya pasó y siguen con `venta_abierta = true`. Además, CU-06 revalida la hora
antes de reservar, para cubrir el minuto de desfase. Si el evento se postergó,
la tarea usa la fecha nueva.

**Motivo:** `venta_abierta` es una columna almacenada; si nadie la cambia queda
en `true` para siempre. La expiración de reservas, en cambio, sigue siendo
perezosa (decisión 6 del Paso 0): solo importa al calcular disponibilidad, y
ese cálculo ocurre justo cuando alguien consulta.

### Tipos de entrada según el estado del evento

En BORRADOR, ACTIVO y POSTERGADO se registran tipos nuevos y se modifican el
nombre, el precio y la cantidad de los existentes. En CANCELADO no se toca nada,
con la misma regla que la edición del evento. Consultarlos se puede siempre.

**Motivo:** la ficha de CU-11 no distingue estados, y un evento que ya vende
puede necesitar un tipo más o más cupo. El precio nuevo solo afecta a las
reservas siguientes (ver "Precio congelado en `reserva.precio_unitario`").

### Cambiar la cantidad total de un tipo

Lo vendido y lo reservado es `cantidad_total - cantidad_disponible`. Al cambiar
la total, la disponible se mueve en la misma diferencia. Si la nueva total es
menor que lo vendido más lo reservado, se rechaza e indica el mínimo posible.

**Motivo:** editar la disponible a mano permitiría vender más de lo que existe o
dejar entradas emitidas fuera de la cuenta. La cantidad mínima es 1.

### Precio de un tipo de entrada

Mayor que 0, con hasta 2 decimales y hasta 99.999.999,99 Bs (`DECIMAL(10,2)`).
**No hay entradas gratis:** CU-06 siempre incluye Realizar pago y
`entrada.id_pago` es NOT NULL. Admitirlas cambiaría CU-06, CU-07 y la tabla
`entrada`, y habría que decidirlo aparte.

### Nombre único dentro del evento

Dos tipos del mismo evento no pueden llamarse igual, sin distinguir mayúsculas ni
espacios en los extremos. En eventos distintos sí se repite.

**Motivo:** el cliente elige el tipo por su nombre, y dos opciones iguales con
precios distintos lo confundirían.

### Los tipos de entrada no se eliminan

La ficha de CU-11 solo registra y modifica. Un tipo mal creado se corrige
editándolo. Es el mismo criterio que CU-10, que tampoco elimina eventos.

Las cinco decisiones se tomaron con el usuario el 19/09/2026, al generar los
criterios de CU-11.

### Transiciones de estado del evento (CU-12)

- **Activar:** desde BORRADOR, si tiene al menos un tipo de entrada, y desde
  POSTERGADO, que vuelve a ACTIVO sin volver a verificar los tipos.
- **Postergar:** desde ACTIVO o POSTERGADO. Un borrador cambia su fecha
  editándose (CU-10), sin guardar fecha original.
- **Cancelar:** desde cualquier estado menos CANCELADO, que es definitivo.
- Pedir el estado que el evento ya tiene se rechaza con 409, en vez de no hacer
  nada: así el administrador se entera de que su pedido llegó tarde.

**Motivo:** la ficha de CU-12 enumera las operaciones sin decir desde qué estado
sale cada una, y FA-02 habla de «cuando el evento vuelve a estar activo», que
solo tiene sentido si un postergado se puede activar.

### La venta se abre al activar y se cierra al cancelar

Activar abre la venta; cancelar la cierra; cerrarla a mano la cierra sin tocar el
estado. Volver a activar el evento es la única forma de reabrirla.

**Motivo:** el modelo solo tiene `cerrarVenta()`, y el evento nace con la venta
cerrada. Sin esto, un evento activo se quedaba sin vender hasta que alguien
cambiara la columna a mano.

### Fecha nueva al postergar

No puede ser anterior a hoy ni igual a la que el evento tiene. Las horas no
cambian: `postergar(nuevaFecha)` solo recibe la fecha, y en ACTIVO o POSTERGADO
CU-10 tampoco deja editarlas.

**Motivo:** postergar a la misma fecha no es postergar, y una fecha pasada
contradice la regla de CU-10. Permitir una fecha anterior a la actual (pero no a
hoy) deja corregir una postergación que se fue demasiado lejos.

### Fecha de nacimiento del Cliente

La fecha de nacimiento vive en Seguridad y CU-06 la necesita para validar la edad
(31/10/2026). Eventos y Venta la pide por `GET /interno/usuarios/{id}/fecha-nacimiento`,
con clave de servicio y plazos de 1 s para conectar y 2 s para responder, **antes** de
abrir la transacción de la reserva: una llamada a otro servicio no espera con el
evento bloqueado. No se copia a `eventos_db`. Si Seguridad no responde, la reserva
sale 503 `SERVICIO_NO_DISPONIBLE`, porque no se sabe si el Cliente cumple la edad.

### Espera máxima de un bloqueo en Eventos y Venta

La conexión fija `innodb_lock_wait_timeout=5` (31/10/2026): quien no obtiene un
bloqueo a los 5 segundos recibe 409 `OPERACION_SIMULTANEA` (CU-06 RF-14), en vez de
retener hilo y conexión los 50 segundos por defecto de MySQL. Va en la URL de la
conexión porque MySQL no aplica `jakarta.persistence.lock.timeout`.

### La entrada conoce su reserva

**Amplía el modelo de datos (31/10/2026, decidido con el usuario).** En el sistema
base, una entrada llegaba a su compra por el pago: `entrada → pago → reserva`. Pago
ahora vive en otro servicio, y la entrada guarda de él solo el id. Para agrupar las
entradas por compra sin llamar a Pagos, `entrada` gana `id_reserva`, con llave foránea
dentro de `eventos_db`. Así «Mis entradas» sigue funcionando con Pagos caído.

### Reactivar entradas utilizadas: cuando exista la Venta

CU-12 FA-02 reactiva las entradas usadas antes de una postergación cuando el
evento vuelve a estar activo. La tabla `entrada` nace con CU-06 y CU-07, así que
**esa reactivación se implementa al llegar la Venta**, no en CU-12. Lo que CU-12
sí hace es dejar el evento ACTIVO con su fecha nueva, que es de donde las
entradas toman su validez (cuelgan del evento a través de su tipo).

En microservicios el orden se conservó (30/10/2026): FA-02 entró con HU-16, y liberar
las reservas vencidas al listar o modificar tipos (CU-11), con HU-15.

**Un postergado cuya fecha ya pasó no se activa (31/10/2026, decidido con el usuario):**
responde 409 `EVENTO_TERMINADO` y hay que postergarlo a una fecha futura. Reactivar
entradas de un evento terminado devolvería un QR que nadie puede usar y borraría el
historial de cuáles se usaron (CU-12 RF-33).

Al implementarla, en su rama propia, se decidió con el usuario (22/09/2026):

- **Es automática al activar.** Activar un evento POSTERGADO pasa a ACTIVA,
  en la misma transacción, todas sus entradas UTILIZADAS; la confirmación dice
  cuántas. No hay una acción aparte ni se eligen una por una.
- **Todas las UTILIZADAS cuentan como usadas antes de la postergación:**
  mientras el evento está postergado ninguna puede usarse, porque la vigencia
  exige el evento activo.

**Motivo:** la ficha dice que el sistema «permite» la reactivación sin decir
quién la dispara; así el evento vuelve a estar activo y sus entradas listas para
la fecha nueva en un solo paso.

Al implementarla se sumaron estas reglas técnicas:

- **Actualización en bloque, después de activar.** Un `update` JPQL condicionado
  a `estado = UTILIZADA` cumple el papel de `Entrada.reactivar()` del modelo.
  Llega al evento por el tipo, porque la entrada no tiene FK a evento. Corre
  después de `evento.activar()`: con `flushAutomatically`, la activación se
  escribe antes, en la misma transacción y con el evento bloqueado.
- **La respuesta de la activación** agrega `entradasReactivadas` al JSON del
  evento, sin anidarlo (`@JsonUnwrapped`). Las otras tres operaciones no
  cambian.
- **Activar un postergado pide confirmación** en la pestaña Estado, y su tarjeta
  avisa que las entradas usadas vuelven a estar activas: a diferencia de activar
  un borrador, eso no se deshace cerrando la venta. La reactivación sigue siendo
  automática al confirmar.

### Bloqueo del evento al cambiar su estado

Todas las operaciones de CU-12 toman la fila del evento con bloqueo pesimista,
igual que CU-10 y CU-11. Es lo que hace efectivo el bloqueo de CU-11: sin esto,
una cancelación podía colarse mientras se registraba un tipo de entrada.

Las cinco decisiones se tomaron con el usuario el 19/09/2026, al generar los
criterios de CU-12.

---

## 4. Venta

### Una compra es de un solo tipo de entrada

`reserva` tiene una sola FK a `tipo_entrada` y una sola `cantidad`, y CU-06
habla de "el tipo" en singular. Comprar dos tipos son dos compras, con sus dos
reservas, pagos y correos.

**Consecuencia para la interfaz:** cada tipo de entrada tiene su propio botón de
comprar. No hay carrito.

### Límite de compra acumulado por cliente y evento

`verificarLimiteCompra(cliente, cantidad)` suma las entradas ya emitidas al
cliente para ese evento, sus reservas vigentes y la cantidad pedida.

**Motivo:** con solo la cantidad de la solicitud, tres compras sucesivas de 5
con límite 5 daban 15 entradas. Al ser cada compra de un solo tipo, encadenar
compras es el camino normal.

### Restricción de edad contra la fecha del evento

No contra la de la compra: la restricción protege el acceso al evento. Así nadie
queda bloqueado por cumplir años entre la compra y el evento.

### `cantidad_disponible` se descuenta al reservar

Y se restituye al liberar, como indican `reducirDisponibilidad()` y
`liberarDisponibilidad()`. El barrido perezoso de reservas vencidas corre
**antes** de cualquier lectura de disponibilidad, para que ninguna consulta vea
un valor desactualizado.

### Concurrencia sobre el último cupo: bloqueo pesimista

`SELECT ... FOR UPDATE` sobre la fila de `tipo_entrada` mientras se crea la
reserva. El bloqueo optimista con `@Version` exigía otra columna en el modelo;
con el tráfico bajo que ya asume el Paso 0, bloquear una fila durante una
transacción corta no tiene costo apreciable.

### Precio congelado en `reserva.precio_unitario`

Se copia del tipo de entrada al reservar. CU-11 permite cambiar el precio en
cualquier momento, y sin copiarlo el monto se movería durante los 10 minutos de
la reserva.

### `reserva` y `entrada` no tienen FK a `evento`

No es un error: llegan al evento vía `tipo_entrada`. Consultar por evento
requiere un JOIN.

### Adquirir entradas (CU-06)

- **CU-06 llega hasta la reserva vigente.** El paso 6, «incluir(Realizar
  pago)», el paso 7 (confirmar la compra) y FA-05 se implementan con CU-07, en
  el subsistema de Pagos. Hasta entonces, una reserva solo termina por
  vencimiento.
- **Solo el rol Cliente compra.** Un Administrador o un Personal autorizado
  recibe 403: no tiene fecha de nacimiento, y la restricción de edad la
  necesita.
- **Sin sesión, «Comprar» lleva a iniciar sesión** con destino al detalle del
  evento y el tipo elegido, y al entrar vuelve ahí.
- **Tipo de un evento que no se muestra:** si el evento está en borrador,
  cancelado o ya terminado, responde 404 `TIPO_ENTRADA_NO_ENCONTRADO`, como el
  detalle del catálogo. Si el evento se muestra pero tiene la venta cerrada,
  responde 409 `VENTA_CERRADA`.

**Motivo:** la ficha incluye el pago, pero el plan pone Pagos después de Venta.
Separarlos deja cada rama del tamaño de las anteriores. Decidido con el usuario
el 21/09/2026, al generar los criterios de CU-06.

Al implementarlo se sumaron estas reglas técnicas:

- **Liberación en bloque:** las reservas vencidas se liberan con dos
  actualizaciones condicionadas, no a través de la entidad:
  `UPDATE reserva SET estado='EXPIRADA' WHERE id=? AND estado='VIGENTE'`, y solo
  si esa actualización cambió una fila, `cantidad_disponible + cantidad` en la
  base. Así una reserva se libera una sola vez aunque dos pedidos coincidan, sin
  depender de lo que la transacción ya haya leído. Cumple el papel de
  `Reserva.liberar()` y `TipoEntrada.liberarDisponibilidad()` del modelo.
- **Dónde se libera:** antes de leer o usar la disponibilidad. Eso es al
  reservar, en el detalle del catálogo, en la lista y el cambio de cantidad de
  los tipos (CU-11) y al consultar una reserva. Una consulta solo bloquea el
  evento si hay algo vencido.
- **Orden de los bloqueos:** evento, reserva y tipo, en todos los caminos, para
  que no se traben entre sí.
- **`reservar` en `READ_COMMITTED`:** el límite se cuenta con el evento ya
  bloqueado. Con REPEATABLE READ, el nivel por defecto de MySQL, la foto de la
  transacción sale de una lectura anterior al bloqueo, y dos compras
  simultáneas del mismo Cliente podrían pasar juntas el límite.
- **Límite por plazo:** el límite cuenta las reservas cuyo plazo no pasó,
  aunque el barrido todavía no haya marcado las vencidas. Desde CU-07 suma
  también las entradas emitidas al Cliente para el evento, salvo las devueltas.
- **Cantidades con decimales:** se rechazan con 400
  (`spring.jackson.deserialization.accept-float-as-int=false`) en vez de
  truncarse. Vale también para la edad, el límite y la cantidad total de CU-10
  y CU-11.
- **La cuenta regresiva sale del servidor:** la respuesta de la reserva trae
  `segundosRestantes`, y la pantalla cuenta desde ahí, sin depender del reloj ni
  de la zona del navegador.

### Consultar entradas (CU-08)

- **La imagen QR se dibuja en el frontend.** Una librería npm genera la imagen a
  partir del código que ya devuelve la API. No hay un endpoint de imágenes ni
  una librería de QR en el backend.
- **También el QR del cobro pasa a imagen.** La pantalla de la reserva (CU-07)
  usa el mismo componente, para que el cliente lo escanee con la app de su
  banco.
- **Listado por compra.** Una tarjeta por compra confirmada, con el evento, el
  tipo, la cantidad y sus entradas numeradas con su estado. Primero los eventos
  próximos, del más cercano al más lejano, y después los pasados, del más
  reciente al más antiguo. Un evento postergado o cancelado lleva una etiqueta y
  su fecha vigente, sin frases sobre la devolución: es un dato de la consulta,
  no un aviso. Las compras pasadas, además, llevan «Terminado» y se ven
  apagadas, y una línea las separa de las próximas (pedido en la prueba
  manual: solo con el título de la sección no se distinguían). Las de eventos
  cancelados van en su propia sección, «Cancelados», al final, hayan terminado
  o no, con contorno rojo (también pedido en la prueba manual).
- **El QR solo se muestra si puede servir.** Se muestra el de una entrada ACTIVA
  de un evento no cancelado. Para una utilizada, una devuelta o una de un evento
  cancelado, el backend no entrega el código y el detalle dice por qué no hay
  QR. El listado nunca lleva los códigos.

**Motivo:** la ficha no dice cómo se agrupan las entradas ni qué pasa con el QR
de una entrada que ya no sirve, y la imagen QR necesitaba decidir dónde se
genera. Decidido con el usuario el 22/09/2026, al generar los criterios de
CU-08.

Al implementarlo se sumaron estas reglas técnicas:

- **Una compra es un pago con sus entradas.** El listado parte de las entradas
  y las agrupa por pago; cada compra se identifica con su `idReserva`. Una
  reserva sin entradas (pendiente, vencida o con el pago para devolución) no
  aparece.
- **Una sola consulta:** las entradas del Cliente llegan con su pago, su
  reserva, su tipo y su evento en la misma consulta JPQL. El agrupamiento y el
  orden se hacen en Java, porque los pasados van en el orden inverso de los
  próximos.
- **`Evento.termino(ahora)`** es la única regla de «terminó», con el cruce de
  medianoche. La usan el catálogo, el cierre de la venta y este listado.
- **`Entrada.muestraSuCodigo()`** decide si el detalle entrega el código. El
  motivo cuando no lo entrega lo arma el frontend con el estado de la entrada y
  el del evento; si no reconoce el caso, no inventa uno.
- **El número de la entrada** («Entrada 2 de 3») es su posición entre las
  entradas de su pago, por id.
- **El QR se dibuja como `<svg>` en la plantilla:** `qrcode-generator` calcula
  la matriz y el componente arma un `path` con un cuadrado por módulo oscuro,
  con 4 módulos de zona silenciosa y corrección de errores M. No se usa
  `innerHTML`. Tiene sus propios tokens de color, negro sobre blanco, para que
  no dependa del tema.
- **`codigoQr` del cobro es el contenido del QR, no una imagen.** El componente
  codifica ese texto. Cada adaptador entrega en `CobroGenerado.codigoQr` el
  texto del QR interoperable, venga como venga del proveedor
  (→ Pagos: el QR del proveedor llega como texto o como imagen).

---

## 5. Pagos

### Proveedor: Libélula

Stripe y PayPal no operan para comercios en Bolivia. Entre las opciones
locales, OpenBCB exige una solicitud institucional, las APIs bancarias
entregan credenciales a pedido y los intermediarios con cuota mensual no
documentan un entorno de pruebas. Libélula publica su entorno de testing en el
manual de integración, y su página de pago ofrece QR interoperable, tarjeta y
Tigo Money.

El simulador se mantiene como implementación por defecto, para que el sistema
y sus pruebas no dependan de un servicio externo.

### Adaptador de Libélula

Decidido el 13/10/2026, al recibir la appkey de Libélula, para cobrar como en
producción. Las reglas de CU-07 no cambian: todo queda en el adaptador
(`service/pago/AdaptadorLibelula`), su configuración y un aviso público.

- **Configuración.** `EVENTOS_PAGO_PROVEEDOR=libelula`, con
  `EVENTOS_PAGO_LIBELULA_APPKEY` y `EVENTOS_PAGO_LIBELULA_EMAIL` solo en el
  `.env`. Sin ellas la API no arranca. La appkey no aparece en el registro ni en
  los mensajes de error. Las pruebas usan un servidor falso y nunca llaman a
  Libélula.
- **Un correo fijo de la empresa** en `email_cliente`, que Libélula exige.
  `IPago` no recibe el correo del Cliente, y nuestros dos correos de la compra
  salen igual.
- **La referencia es nuestro identificador** (`SARAO-R{idReserva}-{8 hex}`), no
  el `id_transaccion` de Libélula: la consulta de deudas busca por él. Lleva un
  sufijo al azar para que un reintento después de una falla no choque con la
  deuda anterior.
- **Vencimiento de la deuda:** el día en que vence la reserva, a las 23:59
  (`yyyy-MM-dd HH:mm`, como en el ejemplo del manual). Solo con la fecha,
  Libélula la toma como las 00:00 y rechaza la de hoy por pasada. Los 10 minutos
  los sigue controlando el sistema.
- **Consulta:** `consultar_deudas/por_identificador`. `pagado` → PAGADO; si no,
  PENDIENTE. Una deuda con otro identificador no cuenta.
- **Aviso (`callback_url`):** `GET|POST /api/pagos/aviso?referencia=…`, sin
  sesión. Siempre responde 200, exista o no la referencia. El aviso **no
  confirma nada**: solo adelanta la consulta, que es la que aplica el
  resultado. Llega solo si el sitio es público; en `localhost` alcanzan la
  pantalla y la conciliación de cada minuto.
- **Vuelta del Cliente (`url_retorno`):** la pantalla de su reserva, para quien
  pagó en la página de Libélula.
- **Plazos:** 5 s para conectar y 15 s para leer, porque el Cliente espera la
  respuesta de «Pagar».
- **El cuerpo va entero, con `Content-Length`, por HTTP/1.1.** Libélula no lee
  un cuerpo enviado por partes (`Transfer-Encoding: chunked`, lo que hace por
  defecto el cliente HTTP de Java): responde «Debe especificar el parámetro
  AppKey» aunque la appkey vaya en el JSON. Visto en la primera prueba con la
  appkey real, el 13/10/2026.
- **`pago.codigo_qr` pasa a 1000 caracteres**, porque el texto de un QR
  interoperable puede pasar de 255. `ddl-auto=update` no agranda una columna
  existente: en una base ya creada se aplica un `ALTER TABLE` una vez (README →
  Despliegue).

**Motivo:** el circuito del manual de integración (registrar, consultar y el
aviso), sin que el resto del sistema sepa qué proveedor hay detrás (CLAUDE.md →
Pago).

### El QR del proveedor llega como texto o como imagen

Decidido el 02/10/2026, al ver una integración real de Libélula: al registrar la
deuda devuelve el QR solo como imagen (`qr_simple_url` y `qr_simple_base64`,
PNG), sin su texto. Otros proveedores de QR también entregan solo la imagen.

El adaptador normaliza los dos casos, y el resto del sistema no se entera:

- **Si el proveedor da el texto del QR**, va tal cual a `CobroGenerado.codigoQr`.
- **Si solo da la imagen**, el adaptador la lee y saca el texto que codifica.
  La librería que lee QR es una dependencia del adaptador, no del dominio. Se
  agrega cuando se implemente un adaptador que la necesite, avisando antes.
- **Si la imagen no se puede leer**, el cobro no se genera:
  `SistemaDePagoNoDisponibleException`, como cualquier otra falla del
  proveedor. La reserva sigue vigente y el cliente puede volver a intentar.
- **`urlPasarela`** lleva la página de pago del proveedor cuando la hay (en
  Libélula, `url_pasarela_pagos`): es la alternativa para quien prefiere pagar
  con tarjeta o desde otro dispositivo.

**Motivo:** el frontend sigue dibujando el QR desde el texto (→ Consultar
entradas), con el mismo componente para el cobro y las entradas, sin mostrar
imágenes que vienen de un dominio externo. Cambiar de proveedor sigue siendo
cambiar solo el adaptador.

### Estados del pago: PENDIENTE, PAGADO, EXPIRADO, ERROR

No hay "rechazado". En un pago por QR el cliente paga desde la app de su banco:
si algo falla, falla ahí, y a nuestro sistema no le llega ningún rechazo. El
pago queda pendiente hasta que se realiza (pagado), vence la reserva
(expirado) o el sistema de pago informa un problema (error).

### El vencimiento de 10 minutos lo controla el sistema

Libélula solo acepta el vencimiento de la deuda por día (`yyyy-MM-dd`) y no
permite anularla. Por eso los 10 minutos de la reserva los controla el
sistema, y un cliente puede llegar a pagar después de que la reserva venció.

### Pago tardío

Si el pago llega después de que venció la reserva y todavía hay disponibilidad
para esa cantidad, se vuelve a descontar y se confirma la compra. Si no hay
disponibilidad, el pago queda con `requiere_devolucion`, no se generan
entradas y se informa al cliente. La devolución económica ocurre fuera del
sistema y **ningún CU registra que se efectuó**: el pago queda identificado y
se refleja en el reporte de ingresos.

Por este caso, la multiplicidad `Pago — Entrada` es `1 — 0..*`.

### Verificación del pago e idempotencia

- En el servidor desplegado, Libélula avisa por `callback_url` (GET con
  `transaction_id`). Ese aviso necesita una URL pública, así que en desarrollo
  local la verificación usa la consulta de pagos por período (conciliación).
- El aviso no alcanza por sí solo: el pago se confirma contra la consulta del
  sistema de pago.
- Un mismo `transaction_id` se procesa una sola vez, para que un aviso repetido
  o una consulta posterior no generen entradas dos veces.

### Dos correos al confirmar la compra

Detalle de compra y envío de entradas, como mensajes separados.

### `codigo_qr` significa dos cosas

`pago.codigo_qr` es el que el cliente escanea para pagar; `entrada.codigo_qr`,
el que el personal escanea en la puerta. Las columnas conservan su nombre, pero
en Java los atributos se llaman `codigoQrPago` y `codigoQrEntrada`.

### Realizar pago (CU-07)

- **Todo con el simulador.** CU-07 implementa el pago, la verificación, el pago
  pendiente y el tardío, las entradas y los dos correos, con el simulador de
  pago y el de correo. El adaptador de Libélula va en una rama propia cuando
  haya credenciales de su entorno de testing. `IPago` imita su circuito (crear
  el cobro, consultarlo, recibir el aviso), así que ese cambio queda dentro del
  adaptador y su configuración.
- **Verificación desde la pantalla y en segundo plano.** Mientras el Cliente
  mira el QR, la pantalla consulta cada pocos segundos y el backend pregunta al
  sistema de pago. Además, una tarea `@Scheduled` concilia cada minuto los pagos
  pendientes, y los expirados que todavía pueden cobrarse, igual que el cierre
  automático de la venta. No contradice la decisión 6 del Paso 0: la expiración
  de reservas sigue siendo perezosa, y esta tarea solo concilia pagos.
- **El cobro se genera con «Pagar».** La pantalla de la reserva lo ofrece
  mientras la reserva está vigente. Hay uno por reserva (`pago.id_reserva`
  UNIQUE): volver a pedirlo devuelve el mismo. Si el sistema de pago no puede
  generarlo, no se registra un Pago (`codigo_qr` es NOT NULL) y las entradas se
  liberan (CU-07 FA-01, CU-06 FA-05).
- **Botones de prueba del simulador.** Con el simulador activo, bajo el QR
  aparecen «Simular pago» y «Simular error», marcados como herramienta de
  pruebas. Su endpoint solo existe con ese proveedor.
- **Código de la entrada.** Es aleatorio y no se puede adivinar: 128 bits de
  `SecureRandom` en texto apto para URL, UNIQUE en `entrada.codigo_qr`. El
  correo de entradas lleva los códigos. Desde CU-08, tanto el QR de la entrada
  como el de pago se muestran como imagen, junto al enlace a la página de pago.
- **Pago tardío solo si el evento sigue vendiendo.** Se confirma si el evento se
  muestra en el catálogo, tiene la venta abierta y hay cupo para la cantidad. Si
  no, queda `requiere_devolucion`, sin entradas. Es la misma salida que «sin
  disponibilidad»: así no se emiten entradas de un evento cancelado o terminado.

**Motivo:** la ficha deja abiertos el disparador de la verificación, el momento
del cobro y el formato del código, y FA-03 no dice qué pasa con un evento que
dejó de vender. Decidido con el usuario el 22/09/2026, al generar los criterios
de CU-07.

Al implementarlo se sumaron estas reglas técnicas:

- **Una sola pieza aplica el resultado** (`ConciliacionDePagos`), para la
  pantalla y para la tarea. Consulta al sistema de pago antes de bloquear, sin
  cargar el pago: lee solo su referencia. Después bloquea el evento, libera las
  vencidas y lee el pago con bloqueo; si ya está procesado, no hace nada. Si el
  pago hubiera quedado cargado de antes, la lectura con bloqueo devolvería esa
  copia vieja y una verificación que se cruza con otra marcaría para devolución
  una compra ya confirmada.
- **Ninguna llamada al sistema de pago con filas bloqueadas.** «Pagar» usa dos
  transacciones cortas: la primera valida la reserva y la segunda guarda el
  cobro, con la llamada entre las dos. Si en el medio otra pestaña guardó su
  cobro, gana ese, y el generado queda sin mostrarse. Así una llamada lenta no
  frena las reservas ni los pagos del evento.
- **`READ_COMMITTED` en «Pagar» y en la conciliación**, por el mismo motivo que
  `reservar`: lo que se lee después del bloqueo del evento tiene que incluir lo
  que otra transacción acaba de confirmar.
- **El pago vence con su reserva:** el barrido que expira una reserva pasa su
  pago PENDIENTE a EXPIRADO con otra actualización condicionada. Un pago
  procesado no cambia.
- **Ventana de 24 horas:** la tarea concilia los pagos pendientes y expirados
  cuya reserva es de las últimas 24 horas, porque la deuda del sistema de pago
  vence por día. Los pendientes también llevan esa ventana: el barrido solo
  corre cuando alguien mira el evento, y uno abandonado se consultaría siempre.
- **Correos después del commit:** la confirmación publica un evento, y los dos
  correos salen con `@TransactionalEventListener(AFTER_COMMIT)`, cada uno por
  su lado. Una compra revertida no manda correos, y un correo que falla no
  revierte la compra.
- **Simulador en memoria:** tras un reinicio olvida sus cobros; uno desconocido
  sigue pendiente y se puede forzar igual, porque la referencia llega desde la
  base y solo para el dueño de la reserva.

### CU-13 devuelve solo entradas activas

Una entrada utilizada corresponde a alguien que ya asistió. Si se reactivó tras
una postergación, vuelve a estar activa y entra en la devolución sin regla
extra.

Una entrada usada antes de una postergación, de un evento que se cancela sin
volver a activarse, sigue UTILIZADA y no entra en la devolución: la reactivación
de CU-12 FA-02 ocurre solo al activar. Es un caso raro y, como la devolución
económica ocurre fuera del sistema, si corresponde se resuelve ahí. Decidido con
el usuario el 22/09/2026, en la revisión de CU-13.

### Gestionar devoluciones (CU-13)

- **El registro es el estado DEVUELTA.** No se guarda la fecha ni quién registró
  la devolución: el modelo no tiene dónde, y la devolución económica y su
  comprobante viven fuera del sistema. La entrada queda identificable por su
  evento, su Cliente y su pago.
- **Por compra.** Las entradas pendientes de un evento cancelado se agrupan por
  compra (un pago con sus entradas). Se registra la compra entera, que pasa a
  DEVUELTA todas sus activas, o una sola entrada.
- **Las devueltas a la vista.** La pantalla del evento muestra aparte, solo
  lectura, las entradas ya devueltas; las utilizadas no aparecen.

**Motivo:** la ficha no dice dónde queda «el registro de la devolución
realizada», si se devuelve por entrada o por compra, ni si las devueltas se
siguen viendo. La devolución económica suele hacerse por compra. Decidido con el
usuario el 22/09/2026, al generar los criterios de CU-13.

Al implementarlo se sumaron estas reglas técnicas:

- **Actualización en bloque condicionada a ACTIVA:** cumple el papel de
  `Entrada.marcarDevuelta()`. Si no cambia ninguna fila, otra petición ya la
  registró (FA-02); si la compra no tiene devueltas, es que solo le quedan
  usadas, y responde `ENTRADA_NO_DEVOLVIBLE`.
- **Con el evento bloqueado primero y en `READ_COMMITTED`:** dos registros de la
  misma compra se serializan y el segundo ve lo que confirmó el primero.
- **El número de cada entrada** es su posición entre todas las de su compra,
  incluidas las usadas, igual que en «Mis entradas».
- **La tarjeta muestra el precio por entrada** (el congelado al reservar) además
  del monto de la compra: es lo que se devuelve por cada una cuando la compra
  está devuelta en parte.

---

## 6. Control de Acceso

### `validarEntrada(codigoQR, evento)`

El personal autorizado selecciona el evento antes de escanear.

**Motivo:** con solo el código QR, el evento se deducía de la propia entrada y
la verificación de correspondencia de CU-15 daba siempre verdadero.

### Vigencia de una entrada

Vigente = estado **activa**, evento **activo** y fecha del evento igual al
**día en curso**. Cubre la entrada devuelta, la de un evento cancelado o
postergado, y la que se presenta otro día.

«Día en curso» incluye la parte de un evento que cruza la medianoche: la entrada
vale desde el día del evento hasta su hora de fin, al día siguiente si termina
después de las 00:00. Es la regla de «terminado» del catálogo (precisado con el
usuario el 22/09/2026, al generar los criterios de CU-15).

### Varios accesos por entrada

`acceso.id_entrada` no es UNIQUE (`Entrada 1 — 0..* Acceso`).

**Motivo:** una entrada usada antes de una postergación se reactiva (CU-12
FA-02) y se valida otra vez en la fecha nueva; con UNIQUE, la base rechazaba ese
segundo acceso. Además conserva el historial que necesita el reporte de accesos.

### Validar entrada (CU-15)

- **Cámara y código a mano.** La pantalla lee el QR con la cámara, con una
  librería npm; si no hay cámara o el QR no se lee, el código se escribe a mano.
  El Cliente lo tiene en el correo de sus entradas (CU-08 no lo muestra como
  texto).
- **Solo los eventos activos de hoy.** El Personal elige entre los eventos
  ACTIVOS que son hoy o siguen en curso: son los únicos donde una entrada puede
  estar vigente.
- **El resultado dice el tipo de entrada**, sin datos del Cliente.
- **Orden de las verificaciones:** correspondencia con el evento, utilización
  previa y el resto de la vigencia. La vigencia exige «activa»: sin verificar
  antes si ya se usó, FA-03 no se daría nunca.

**Motivo:** la ficha no dice cómo se lee el QR, qué eventos se eligen, qué
muestra la validación ni qué pasa pasada la medianoche. Decidido con el usuario
el 22/09/2026, al generar los criterios de CU-15.

Al implementarlo se sumaron estas reglas técnicas:

- **Los rechazos son resultados:** FA-01 a FA-03 responden 200 con
  `NO_CORRESPONDE`, `NO_VIGENTE` o `YA_UTILIZADA`. Solo es error un pedido sin
  sentido: el evento no existe (404) o no admite validar (409
  `EVENTO_NO_DISPONIBLE`).
- **El código de otro evento y el inexistente dicen lo mismo:** la respuesta no
  permite averiguar si un código existe en otro evento.
- **La entrada se busca sin cargarla y se relee con bloqueo** (`PESSIMISTIC_WRITE`),
  en `READ_COMMITTED`: dos validaciones de la misma entrada se serializan y la
  segunda la ve UTILIZADA (RF-11).
- **El evento se lee con bloqueo compartido** (`PESSIMISTIC_READ`), antes que la
  entrada: las validaciones no se esperan entre sí, pero una cancelación o una
  postergación en curso, que toma el evento con bloqueo exclusivo, se confirma
  antes y la validación la ve (RF-9). Es el mismo orden que el resto del
  sistema: evento y después entrada.
- **El evento elegido queda en la URL** (`/validar?evento=5`): una recarga no lo
  pierde. Un id que no está entre los de hoy vuelve a la elección, y también
  vuelve a ella la pantalla cuando el backend responde que el evento no existe o
  ya no admite validar.
- **El resultado queda en pantalla hasta que la cámara pasa 5 segundos sin ver
  su código**, y mientras tanto ese código no se reenvía (RF-15). Un error no
  deja resultado, así que el mismo código se reintenta enseguida.
- **Un código más largo que la columna (255) no se envía:** ninguna entrada lo
  tiene, así que la pantalla responde «No es válida para este evento» sin
  preguntar. El backend lo rechazaría como dato inválido.
- **El lector de QR:** `jsqr`, detrás de un token inyectable para que los tests
  lo reemplacen (jsdom no dibuja). Lee unas 10 veces por segundo, sobre el cuadro
  achicado a 640 px de ancho y fuera de la zona de Angular: decodificar cada
  cuadro a resolución completa calienta el celular de la puerta. Si el video no
  se puede reproducir, apaga la cámara y ofrece escribir el código.

---

## 7. Reportes

### Criterios de filtro por tipo

| Reporte | Criterios |
|---|---|
| Ventas | Rango de fechas, evento, tipo de entrada |
| Ingresos | Rango de fechas, evento |
| Entradas | Evento, tipo de entrada, estado de la entrada |
| Clientes | Rango de fechas de registro, evento en el que adquirió entradas |
| Accesos | Evento, rango de fechas, personal autorizado que validó |

### Ingresos: bruto, devuelto y neto

Como la devolución económica ocurre fuera del sistema, el pago de una entrada
devuelta sigue figurando como pagado. Sin separar los tres montos, el reporte
contaría como ingreso dinero que se reintegró. El monto devuelto incluye
también los pagos tardíos con `requiere_devolucion`.

### Gestionar reportes (CU-14)

- **Resumen y detalle.** Cada reporte muestra arriba los totales agrupados (por
  evento y tipo de entrada, o por evento y Personal en accesos) y abajo el
  detalle fila por fila. El PDF y el Excel llevan lo mismo que la pantalla.
- **Una venta es una compra confirmada:** un pago PAGADO sin
  `requiere_devolucion`, que es el que generó entradas. Una compra que después
  se devolvió sigue siendo venta: el dinero devuelto se ve en ingresos, y así
  ventas mide lo que se vendió e ingresos lo que quedó.
- **Se registra cada generación con resultados.** Exportar no crea otro
  registro, y una búsqueda sin resultados (FA-02) no se registra, porque no se
  generó ningún reporte.

**Motivo:** la ficha define los criterios de cada reporte, pero no qué muestra,
qué cuenta como venta ni en qué momento se registra un reporte generado.
Decidido con el usuario el 23/09/2026, al generar los criterios de CU-14.

Al implementarlo se sumaron estas reglas técnicas:

- **Una tabla genérica para los cinco reportes.** Columnas con su tipo (texto,
  entero, monto, fecha, fecha y hora) y filas de valores: la pantalla, el PDF y
  el Excel tienen un solo recorrido cada uno. Cada tipo de reporte es un
  `ArmadorDeReporte` con su consulta y los criterios que admite.
- **Exportar vuelve a armar el reporte** con los criterios registrados, en vez
  de guardar su contenido, que exigiría una columna nueva en `reporte`. El
  archivo dice cuándo se generó y cuándo se exportó, para que se note si los
  datos cambiaron en el medio. Decidido con el usuario en la revisión de CU-14.
- **`criterios_filtro` como `clave=valor` separados por `;`**
  (`desde=2026-09-01;idEvento=5`): el modelo deja el formato abierto, se lee a
  simple vista y no depende de la librería de JSON.
- **Rangos por día, con los dos extremos incluidos:** desde las 00:00 del primer
  día hasta antes de las 00:00 del siguiente al último. Una fecha fuera de los
  años 1000 a 9998 se rechaza como dato inválido.
- **Lo devuelto se ubica en la fecha del pago:** la devolución no tiene fecha
  propia (CU-13).
- **Los filtros se ofrecen desde `/api/reportes/opciones`:** los eventos que no
  están en borrador, con sus tipos, y el Personal que validó alguna entrada,
  aunque hoy tenga otro rol.
- **Sin resultados (FA-02) es 404 `REPORTE_SIN_RESULTADOS`**, y la pantalla lo
  muestra como aviso, no como error.
- **OpenPDF y FastExcel:** OpenPDF arma tablas con poco código y sin
  dependencias obligatorias; FastExcel escribe `.xlsx` sin la cadena de Apache
  POI. Un texto se escribe siempre como celda de texto, así que un nombre que
  empieza con `=` no se ejecuta como fórmula. El PDF usa Helvetica (Latin-1):
  alcanza para los nombres en español.
- **La exportación se pide con `HttpClient`, no con `httpResource`**, aunque sea
  un GET: es una descarga a pedido, con el clic, y no un dato que la pantalla
  muestre. El archivo llega como Blob porque un enlace no puede mandar el token.

### Envío de reportes por correo (CU-14)

- **Es un cuarto correo, y no es una notificación.** Los tres de
  `CLAUDE.md → Correos` llegan a clientes; este lo pide el Administrador a buzón
  del dominio propio, con el reporte adjunto. Amplía CU-14 sin tocar esa regla.
- **Un solo correo a varios destinatarios**, todos en el campo Para, hasta 10.
  El servidor de correo solo entrega al dominio propio
  (`EVENTOS_CORREO_DOMINIO`), así que cualquier otra dirección se rechaza con
  400 en el campo `destinatarios`: aceptarla daría un «enviado» que no llega.
- **PDF, Excel o ambos**, con los mismos nombres de archivo de la descarga
  (`reporte-ventas-12.pdf`), armados por `ReporteService.paraExportar`: los datos
  son los del momento del envío y no queda registrado otro reporte.
- **202 Accepted sin esperar al servidor de correo.** El envío va en segundo
  plano con el resto de los correos y un fallo se registra sin propagarse, igual
  que en RF-18.

**Motivo:** la ficha de CU-14 no lo pedía. Decidido con el usuario el
01/10/2026, al ampliar CU-14 con el envío por correo.

En el código: `EnvioDeReporte` (qué se envía y a quién), `NotificadorCorreo.
enviarReporte` (cómo), `POST /api/reportes/{idReporte}/correo`, y el panel
«Enviar por correo» de la pantalla de reportes.

---

## 8. Sistema de diseño (frontend)

### La aplicación es el producto de una empresa, no un trabajo académico

La marca del sitio es **Sarao**, una empresa que vende entradas para eventos en
Santa Cruz. En la interfaz no aparece la universidad, la materia ni la
metodología: ni en los textos, ni en el pie de página, ni en los correos.

**Motivo:** el sistema se presenta como un producto real, y el nombre de la
marca es parte del diseño. La documentación de este repositorio sigue siendo
académica; la regla alcanza solo a lo que ve el usuario dentro de la
aplicación. El nombre vive en un único lugar del código, para poder cambiarlo
sin tocar las pantallas.

### Dirección visual: oscura, con ámbar de escenario

Fondo azul nocturno, ámbar para la acción principal y para los precios, y las
fotos de los eventos como única fuente de color fuerte. Se eligió entre tres
propuestas (clara y ordenada, vibrante en bloques, y esta).

**Motivo:** es la que más resalta las fotos de los eventos y la que más se
parece a las plataformas de venta de entradas que la gente ya conoce.

**Consecuencia asumida:** el modo claro (→ Modo claro, opcional) tiene su
propia paleta, no la oscura invertida. Además, los formularios largos y las tablas de administración se leen con más
esfuerzo en oscuro, así que esas pantallas se cuidan especialmente: interlineado
holgado, nada de pesos de letra finos y separación clara entre las filas.

### Paleta

| Token | Valor | Uso | Contraste |
|---|---|---|---|
| `--sarao-fondo` | `#0B1020` | Fondo de la aplicación | base |
| `--sarao-superficie` | `#151B2E` | Tarjetas, campos, encabezado de tabla | 15.86:1 con el texto |
| `--sarao-superficie-alta` | `#1C2440` | Hover, fila activa | — |
| `--sarao-texto` | `#F4F6FF` | Texto principal | 17.56:1 |
| `--sarao-texto-suave` | `#A7B0CC` | Ayudas, metadatos | 8.77:1 |
| `--sarao-ambar` | `#F5B301` | Acción principal, precios, foco | 10.22:1 |
| `--sarao-ambar-fuerte` | `#D99A00` | Hover y activo | 7.73:1 |
| `--sarao-borde` | `#2B3350` | Separadores de tabla | decorativo |
| `--sarao-borde-control` | `#6B7799` | Borde de campos y botones | 4.26:1 |
| `--sarao-exito` | `#4ADE80` | Activa, disponible, pagado | 10.86:1 |
| `--sarao-aviso` | `#FBBF24` | Postergado, pago pendiente | 11.34:1 |
| `--sarao-error` | `#FCA5A5` | Cancelado, error, desactivada | 9.98:1 |
| `--sarao-neutro` | `#94A3B8` | Agotado, borrador | 7.38:1 |

Los contrastes están medidos contra `--sarao-fondo` y comprobados uno por uno,
no estimados a ojo. Sobre fondo oscuro los colores de estado van en tonos claros
porque los oscuros del modo claro (`#15803D`, `#B91C1C`) no llegan a 4.5:1.

### Modo claro, opcional

> Reemplazado el 12/10/2026 por «Tres estilos y modo día/noche»: el claro de
> abajo es ahora el «Día» del estilo Adultos, y el botón sol/luna pasó a ser el
> menú «Apariencia». La tabla de contrastes sigue valiendo para esa paleta.

Decidido con el usuario el 02/10/2026. **El oscuro sigue siendo el modo por
defecto**; un botón con sol o luna en el encabezado alterna al claro, con o sin
sesión, y la elección se guarda en el navegador (`localStorage`, clave
`sarao.tema`).

- **Cómo se activa:** el claro redefine los tokens de color en
  `:root[data-tema='claro']`; el oscuro sigue en `:root`. Lo maneja
  `core/tema/tema.service.ts`, que también cambia el `theme-color` de la barra
  del navegador. Un script mínimo en `index.html` aplica el claro antes de que
  arranque Angular, para que la página no aparezca oscura un instante.
- **Dos tokens nuevos, en los dos modos:** `--sarao-sobre-ambar` (el texto
  sobre un fondo ámbar, que ya no puede ser `--sarao-fondo`) y `--sarao-velo`
  (lo que oscurece la página detrás de un diálogo).
- **El ámbar se oscurece en claro:** el `#F5B301` no llega a 4.5:1 sobre
  blanco. El de claro es `#A84B06`, con el texto de los botones en blanco.
- **El QR no cambia:** sigue negro sobre blanco en los dos modos.

| Token | Claro | Contraste medido |
|---|---|---|
| `--sarao-fondo` | `#F6F7FB` | base |
| `--sarao-superficie` | `#FFFFFF` | 17.26:1 con el texto |
| `--sarao-superficie-alta` | `#EEF1F8` | 15.26:1 con el texto |
| `--sarao-texto` | `#141A2E` | 16.12:1 |
| `--sarao-texto-suave` | `#4A5370` | 7.10:1 (6.72:1 sobre superficie alta) |
| `--sarao-ambar` | `#A84B06` | 5.34:1 (5.06:1 sobre superficie alta) |
| `--sarao-ambar-fuerte` | `#92400E` | 6.62:1 |
| `--sarao-sobre-ambar` | `#FFFFFF` | 5.72:1 sobre ámbar, 7.09:1 sobre ámbar fuerte |
| `--sarao-borde` | `#DDE2EE` | decorativo |
| `--sarao-borde-control` | `#7A849E` | 3.49:1 |
| `--sarao-exito` | `#166534` | 6.66:1 (6.49:1 sobre su fondo tenue `#DCFCE7`) |
| `--sarao-aviso` | `#854D0E` | 6.40:1 (6.15:1 sobre su fondo tenue `#FEF3C7`) |
| `--sarao-error` | `#B91C1C` | 6.04:1 (5.30:1 sobre su fondo tenue `#FEE2E2`) |
| `--sarao-neutro` | `#5B6577` | 5.49:1 |
| `--sarao-velo` | `rgb(20 26 46 / 0.45)` | — |

Los contrastes están medidos contra `--sarao-fondo` con la fórmula de
luminancia relativa de WCAG, uno por uno. Los de la primera propuesta que no
llegaban (aviso sobre su fondo tenue, neutro, y ámbar y éxito sobre la
superficie alta) se oscurecieron hasta pasar.

### Tres estilos y modo día/noche

El enunciado pide al menos tres temas (niños, jóvenes, adultos) y el modo día o
noche según el horario del cliente. Decidido con el usuario el 12/10/2026:

- **Un menú «Apariencia»** en el encabezado, con o sin sesión, reemplaza al
  botón sol/luna. Tiene dos grupos de radios nativos: **Estilo** (Niños, Jóvenes,
  Adultos) y **Modo** (Automático según la hora, Día, Noche). Cada elección se
  aplica en el momento. El menú se cierra con el botón, con Escape (el foco
  vuelve al botón) o con un clic afuera. Es `shared/apariencia/`, sin negocio;
  lo conecta el encabezado con `core/tema/tema.service.ts`.
- **Por defecto: Adultos y Noche**, como hasta ahora. El automático hay que
  elegirlo.
- **Automático:** de día de 6:00 a 18:59 con la hora del dispositivo, y de
  noche el resto. Cambia solo al llegar la hora, con un temporizador hasta el
  próximo cambio, programado fuera de la zona de Angular para no dejar la
  aplicación siempre «inestable», y vuelve a mirar la hora al volver a la
  pestaña, porque en segundo plano el temporizador se atrasa.
- **Cada estilo cambia colores, forma y letra de títulos.** Adultos es el de
  siempre (azul noche y ámbar, Sora, radios de 10 y 8 px). Jóvenes: violeta y
  cian, Space Grotesk, radios de 14 y 10 px. Niños: turquesa profundo y coral,
  Fredoka, radios de 20 y 14 px. El texto sigue en Inter en los tres, por
  legibilidad. Las dos fuentes nuevas se piden en el mismo enlace de Google
  Fonts, solo en los pesos 600 y 700.
- **Cómo se activa:** `data-estilo` (sin marca para adultos) y
  `data-tema="claro"` en `<html>`, que redefinen los tokens en `_tokens.scss`.
  Los componentes no cambian: el token sigue llamándose `--sarao-ambar` aunque
  en jóvenes sea cian. Los colores de éxito, aviso y error, y sus fondos
  tenues, son los mismos en todos los estilos (los de noche y los de día).
- **Se guarda en el navegador** (`sarao.estilo`, `sarao.modo`) solo cuando la
  persona elige algo: sin elección no queda nada guardado, así un cambio futuro
  del valor por defecto le llega. Quien había elegido el claro con el botón viejo
  (`sarao.tema`) lo conserva como «Día».
  El script de `index.html` repite la lectura antes de que arranque Angular, con
  la misma tabla de colores de la barra del navegador.

Contraste mínimo de cada paleta nueva, sobre el fondo y las dos superficies
(fórmula de luminancia relativa de WCAG). Las de Adultos están en «Paleta» y
«Modo claro, opcional»:

| Paleta | Texto | Texto suave | Acento como texto | Texto sobre acento | Borde de control | Éxito, aviso, error, neutro |
|---|---|---|---|---|---|---|
| Jóvenes, noche | 14.49:1 | 7.78:1 | 8.80:1 | 10.62:1 | 3.55:1 | 6.33:1 |
| Jóvenes, día | 15.33:1 | 7.32:1 | 6.05:1 | 7.10:1 | 3.56:1 | 5.51:1 |
| Niños, noche | 10.96:1 | 6.72:1 | 4.99:1 | 7.30:1 | 3.65:1 | 5.46:1 |
| Niños, día | 12.09:1 | 6.24:1 | 4.64:1 | 5.18:1 | 3.53:1 | 5.26:1 |

Los mínimos exigidos: 4.5:1 para texto y 3:1 para el borde de un control.

### Tipografía

Sora para los títulos (600/700) e Inter para el texto y los datos (400/500),
ambas de Google Fonts y con su pila de reserva del sistema. Escala de 12, 14, 16
(base), 18, 20, 22, 28 y 34 px; interlineado 1.5 en el texto y 1.1 en los
títulos; línea de menos de 80 caracteres. Sora es la del estilo Adultos: Jóvenes
y Niños titulan con Space Grotesk y Fredoka (→ «Tres estilos y modo día/noche»);
el texto sigue en Inter en los tres.

**Motivo:** sobre fondo oscuro el texto de peso fino se deshace, así que el
cuerpo no baja de 400.

### Reglas que valen para toda la interfaz

- **Contraste medido:** 4.5:1 en texto y 3:1 en bordes de control, verificado
  antes de fijar cualquier color nuevo.
- **El estado nunca depende solo del color:** cada etiqueta lleva su texto y una
  forma propia (círculo, rombo, cuadrado), para que se distinga también con
  daltonismo.
- **Errores junto al campo y resumidos al principio del formulario:** el resumen
  recibe el foco al fallar el envío y cada línea lleva a su campo.
- **Movimiento:** entre 150 y 250 ms, solo como respuesta a una acción, y
  desactivado con `prefers-reduced-motion`.
- **Áreas tocables de 44 px** y foco visible en todo lo que se use con teclado.
- **Iconos en SVG** dibujados en el proyecto, nunca emojis, y sin instalar
  librerías de componentes ni de estilos.
- **Espaciado** en múltiplos de 4 px; radio de 10 px en tarjetas y 8 px en
  campos y botones; ancho máximo del contenido de 1120 px.
- **Lo angosto va centrado:** una pantalla, pestaña o formulario más angosto que
  la columna de contenido se centra, también dentro de la ficha de un evento.
  El ancho y el centrado van juntos en el mixin `pagina-angosta` de
  `frontend/src/styles/_pagina.scss`, para no escribir `max-width` suelto.
  Decidido con el usuario el 21/09/2026, después de varias pantallas pegadas a
  la izquierda en las pruebas manuales.

### Búsqueda en el encabezado

El enunciado pide la búsqueda en el encabezado de la página principal. El
encabezado es el mismo en todas las pantallas, así que el buscador está en todas
y siempre busca en el catálogo: lleva a la portada con `?texto=`, que es el
mismo filtro de CU-05. No hay endpoint nuevo.

- **En la portada conserva las fechas** del catálogo y vuelve a la primera
  página. Desde otra pantalla, la búsqueda empieza sin fechas.
- **Muestra el texto que filtra la portada** y se vacía en las demás pantallas,
  así el encabezado y el formulario del catálogo nunca dicen cosas distintas.
- **El formulario del catálogo se queda:** es el único con las fechas. En la
  portada los dos campos de texto muestran lo mismo.
- **Va en una fila propia, debajo del menú**, para todos los roles: con los seis
  módulos del administrador no entraba en la misma fila y empujaba el perfil y
  el botón del tema a otra línea (prueba manual, 12/10/2026). En el celular ocupa
  todo el ancho.
- Hasta 200 caracteres, como el catálogo.

### Contador de visitas por página

El enunciado pide un contador de visitas en el pie de cada página. Decidido con
el usuario el 12/10/2026:

- **Una visita es abrir o recargar una pantalla**, sin distinguir quién. Cambiar
  solo la consulta (buscar o paginar en la portada) o la pestaña de la ficha de
  un evento no cuenta otra visita; abrir otro evento sí.
- **Se cuenta por página, no por URL:** el enum `Pagina` tiene una por pantalla,
  sin los ids. Todas las fichas de eventos suman a `DETALLE_DE_EVENTO`, y las
  pestañas de la ficha del administrador cuentan como `FICHA_DE_EVENTO`. Cada
  ruta dice la suya en `data.pagina`, y el frontend tiene la misma lista en
  `core/modelos/visitas.ts`. Una pantalla nueva agrega su valor en los dos.
- **Amplía el modelo de datos:** tabla `visita_pagina` (`id_visita_pagina`,
  `pagina` única, `total`). La columna `pagina` es `VARCHAR`, y en la entidad es texto y
  no un enum: Hibernate pondría un `CHECK` con los valores, y con `ddl-auto=update`
  un valor nuevo no cabría en la tabla ya creada (corregido el 31/10/2026).
- **`POST /api/visitas/{pagina}` es público**, como el catálogo, y devuelve el
  total. Una página fuera del enum sale como 400, así nadie crea filas pidiendo
  páginas inventadas. Cualquiera puede inflar el contador repitiendo la petición:
  se acepta, porque es un dato informativo y no cuenta dinero ni cupos.
- **Las filas se crean al arrancar** (`PaginasDelContador`, un
  `ApplicationRunner`): una visita es un `update ... total = total + 1` en la
  base, que bloquea la fila hasta el final de la transacción. Dos visitas a la
  vez no se pisan ni compiten por insertar la primera fila. Si la fila falta (la
  tabla se vació con la aplicación andando), la visita la crea en vez de fallar.
  Supone una sola instancia del backend, como el límite de intentos.
- **Sin token:** el frontend no lo manda al contador, como a `/api/auth/`. Un
  token vencido no cierra la sesión por contar una visita.
- **En el pie:** «Visitas a esta página: N», con miles separados por punto.
  Mientras se cuenta la pantalla nueva no queda el total de la anterior, y si la
  cuenta falla el pie queda vacío, sin aviso de error.

### Dónde viven estas decisiones en el código

Los valores se definen una sola vez en `frontend/src/styles/_tokens.scss`, que
importa `frontend/src/styles.scss`. Los componentes usan solo tokens, nunca
colores sueltos, y se estilan con `:host`. El nombre de la marca vive en un
único archivo de configuración del frontend.

**Motivo:** cuando cambie un color, cambia en un solo lugar, y las pantallas
nuevas no pueden desentonar sin que se note en la revisión.

### Decisiones técnicas del frontend

**El token se guarda en `localStorage`.** La sesión sobrevive a recargar la
página y a cerrar el navegador, hasta que el token vence a las 8 horas.

**Motivo:** no hay refresh token, así que guardarlo solo en memoria obligaría a
iniciar sesión en cada recarga. **Riesgo asumido:** ante una falla de XSS el
token sería legible; Angular escapa el HTML por defecto y no se usa
`bypassSecurityTrust*`, así que ese riesgo se considera bajo. Dos archivos de
`core/` tocan `localStorage`, siempre dentro de `try/catch` porque en modo
privado puede fallar: `sesion/almacen-token.ts` (el token) y
`tema/almacen-tema.ts` (el estilo y el modo elegidos, `sarao.estilo` y
`sarao.modo`, y la clave vieja `sarao.tema` para no perder el claro elegido con
el botón sol/luna). El script de `index.html` repite esa lectura antes de que
arranque Angular.

**Lecturas con `httpResource` y escrituras con `HttpClient`.** Las consultas
entregan carga, error y datos como signals, que es lo que necesitan las
pantallas; las escrituras son acciones puntuales y no ganan nada con eso.

**Un solo diccionario traduce los códigos de error del backend.** Los códigos
(`CUENTA_EXISTENTE`, `ULTIMO_ADMINISTRADOR_ACTIVO`, `CREDENCIALES_INCORRECTAS`…)
se convierten a frases para la persona en un único lugar, no repetidos por
pantalla, para que el mismo error diga siempre lo mismo.

**Sin pruebas de extremo a extremo automatizadas.** Vitest cubre servicios,
interceptores, guards y componentes; el flujo completo se prueba a mano contra
el backend levantado, como en los CU de backend. No se introduce Cypress ni
Playwright.

**Una rama y un PR por caso de uso también en el frontend.** La base común
(tokens de estilo, sesión, interceptores, guards, pantalla base) entra con la
rama de CU-02, que es el primero: sin iniciar sesión no hay forma de probar el
token ni los interceptores.
