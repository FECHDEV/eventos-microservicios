# Plan: roles del personal y datos de demostración

Observaciones de la predefensa (25/11/2026), para la defensa del 02/12:

1. Los roles no están bien definidos: «Cliente» no se ve como un rol, faltan roles, y cada uno
   tiene que entenderse con solo leer su nombre.
2. Un rol **Contador** que solo se encargue de los reportes.
3. El sistema tiene que verse como si una empresa ya lo usara: registros, ventas y eventos con
   imágenes, con datos inventados. La empresa es Sarao (ficticia) y organiza eventos para el
   público en general: conciertos, fiestas, stand-up, teatro, ferias.
4. El documento final se actualiza con todo lo agregado o cambiado.

## Decisiones (tomadas con el usuario el 25/11)

| Rol | Código | Qué hace | Quién lo asigna |
|---|---|---|---|
| Administrador | `ADMINISTRADOR` | Todo: personal, clientes, eventos, devoluciones, reportes y validación | Carga inicial y otro Administrador |
| Organizador de eventos | `ORGANIZADOR` (nuevo) | Crea, edita, activa, posterga y cancela eventos y sus tipos de entrada; cierra ventas | Administrador |
| Contador | `CONTADOR` (nuevo) | Solo reportes: generar, exportar a PDF y Excel, enviar por correo | Administrador |
| Control de acceso | `PERSONAL` (se conserva el código) | Valida las entradas en la puerta | Administrador |
| Cliente | `CLIENTE` | **No es un rol del personal**: es la cuenta de quien se registra en la web para comprar | Se registra solo |

- **El Cliente deja de mostrarse como rol.** La pantalla de usuarios separa **Personal** y
  **Clientes**. A los clientes solo se los activa o desactiva.
- **El Administrador da de alta al personal** (caso de uso nuevo): correo, nombre, apellido, rol y
  una contraseña inicial que cumple la regla de complejidad; el empleado la cambia después con
  CU-03. Sin correo nuevo de bienvenida.
- **CU-09 pasa a «Gestionar personal»:** alta, cambio de rol **entre roles del personal** y
  activación/desactivación. Un cliente no se convierte en personal ni al revés.
- Los códigos existentes no cambian (`CLIENTE`, `PERSONAL`): solo cambian los nombres visibles. Así
  no hay que migrar datos.
- Devoluciones: solo el Administrador (son plata y siguen a una cancelación).

Pendiente de confirmar mañana: lo anterior (cliente ↔ personal, contraseña inicial) es la
recomendación; si el usuario prefiere otra cosa, se cambia antes de programar.

## HU-24: roles del personal (rama y PR)

1. **Docs, directo a `main`:** entrada en `docs/decisiones.md`; `CLAUDE.md` (Usuarios y roles,
   tabla de reglas); `docs/casos-de-uso.md` (actores, CU-09, CU nuevo); criterios EARS del CU
   nuevo y de CU-09 con `/sdd:criterios`; matriz rol × función en `docs/`.
2. **Seguridad:** `Rol` suma `ORGANIZADOR` y `CONTADOR`; alta de personal (`POST /api/usuarios`);
   cambio de rol limitado a roles del personal; revisar la regla del último Administrador y la de
   fecha de nacimiento (solo clientes).
3. **Eventos y Venta (`SecurityConfig`):** `/api/eventos/**` → ADMINISTRADOR, ORGANIZADOR;
   `/api/reportes/**` → ADMINISTRADOR, CONTADOR; `/api/accesos/**` → ADMINISTRADOR, PERSONAL;
   `/api/devoluciones/**` → ADMINISTRADOR. Revisar la consulta del personal en el reporte de
   accesos (hoy filtra por `PERSONAL`; el Administrador también valida).
4. **Base de datos:** Hibernate crea `rol` como `enum(...)` en MySQL y `ddl-auto=update` no lo
   amplía. En una base nueva no pasa nada; la local necesita
   `ALTER TABLE usuario MODIFY rol enum(...)` o empezar de cero (los datos de demostración la
   recrean igual).
5. **Frontend:** modelo `Rol` y `NOMBRES_DE_ROL`; descripción de una línea por rol (se muestra al
   elegirlo); menú y panel por rol (`app.component.ts`, `panel.page.ts`); guards de las rutas;
   pantalla de usuarios con pestañas Personal / Clientes y formulario de alta con Signal Forms.
6. **Pruebas:** TDD en cada servicio y en el frontend; `prueba-completa.sh` suma «el Contador ve
   reportes y no eventos», «el Organizador crea eventos y no ve reportes» y el alta de personal.

## HU-25: datos de demostración (rama y PR)

- **Imágenes:** afiches generados con Pillow (uno por evento, estilo por tipo: concierto, fiesta,
  stand-up, teatro, feria, festival), guardados en `scripts/demostracion/imagenes/`. Sin fotos de
  internet.
- **Script `scripts/datos-de-demostracion.sh`**, por la API como `prueba-completa.sh` (así las
  entradas tienen su QR real y se respetan las reglas):
  - Personal: un Organizador, un Contador y dos de Control de acceso (contraseña que se pide al
    correr, como `cuentas-de-prueba.sh`).
  - ~60 clientes con nombres inventados.
  - ~12 eventos con imagen y precios en Bs: realizados (con accesos), uno el mismo día de la
    defensa (para validar en vivo), próximos, uno postergado, uno cancelado con devoluciones y
    uno en borrador.
  - ~250 compras repartidas en dos meses: se compran por la API y después se mueven al pasado con
    SQL (fechas de reservas, pagos, entradas, accesos y eventos realizados).
  - Reportes ya generados por el Contador, para que su historial no esté vacío.
- Mientras corre, **pagos usa el simulador y el correo también** (no se mandan cientos de
  correos). Al terminar, el script vuelve a encender pagos y notificaciones con el `.env` y
  **comprueba** que el proveedor de pago sea el del `.env` (en el VPS, Libélula).
- `subir-al-vps.sh` lo ofrece como paso opcional al final.

## Documento final

Actualizar: actores y diagrama de casos de uso, tabla de roles y matriz de permisos, CU-09 y CU
nuevo, diagrama de clases de Seguridad, product backlog (HU-24 y HU-25), Sprint 5 con su
registro diario y burndown reales, capturas nuevas (usuarios con pestañas, alta de personal,
panel del Contador y del Organizador, catálogo con imágenes). Regenerar y ajustar `FIN_14`.

## Calendario

| Día | Qué |
|---|---|
| Jue 26/11 | Docs y decisión; HU-24 backend (Seguridad y Eventos y Venta) |
| Vie 27/11 | HU-24 frontend, prueba completa, PR |
| Sáb 28/11 | HU-25: imágenes y script de demostración, PR |
| Dom 29 – Lun 30/11 | Documento: diagramas, capturas, Sprint 5, regenerar |
| Mar 01/12 | Lanzar el VPS, cargar los datos, ensayo |
| Mié 02/12 | Defensa |
