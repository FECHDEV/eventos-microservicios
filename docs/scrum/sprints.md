# Sprints

Entrega y defensa final: 02/12/2026. Cada sprint anota su resultado al cerrar, porque de
acá sale la documentación final.

**Cambio de fecha (12/11/2026):** la entrega pasó del 09/12 al 02/12, el día de la defensa final. Como
los sprints 1 a 4 terminaron antes de lo previsto (HU-22, la última, el 12/11), el sprint 5 se adelanta y
empieza el 13/11.

**Replanificación (13/11/2026):** el plan reparte el rango del 30/10 al 25/11 entre los sprints 1 a 4,
con los días según el esfuerzo y el riesgo de cada uno, y deja la última semana para el sprint 5. Las fechas de la tabla son
las del plan; lo que se trabajó cada día es lo real, tomado de git, y está en el resultado de cada sprint.
La estimación del Product Backlog es en puntos de historia (128 en total).

| Sprint | Fechas | Objetivo |
|---|---|---|
| 1 | 30/10 a 05/11 | Repositorio, Docker, CI, gateway y Seguridad, con la complejidad de la contraseña |
| 2 | 06/11 a 11/11 | Eventos y Venta: catálogo, gestión de eventos, reservas y entradas |
| 3 | 12/11 a 18/11 | Notificaciones y Pagos con Libélula; compra de punta a punta |
| 4 | 19/11 a 25/11 | Acceso, reportes, devoluciones, despliegue en el VPS nuevo y pruebas |
| 5 | 26/11 a 02/12 | Documentación de perfil con Scrum, correcciones y defensa final |

---

## Sprint 1 (30/10 a 05/11)

**Objetivo:** un usuario se registra, inicia sesión y gestiona su cuenta a través
del gateway, con el sistema levantado en Docker y el CI en verde.

**Backlog:** HU-01 a HU-09.

**Nota:** CU-03 envía un correo y Notificaciones llega en el sprint 3. Hasta
entonces, Seguridad usa el simulador de correo.

**Resultado:** HU-01 a HU-09 hechas el 30/10/2026. El sitio funciona en Docker: registro, inicio
de sesión, recuperación, datos personales y gestión de usuarios, por nginx y el gateway. Seguridad
con 232 pruebas, gateway con 8 y frontend con 900.

**Pendientes para las próximas historias** (de las revisiones del sprint):

- **HU-18 (resuelto):** al vencer la reserva, la pantalla deja de consultar el pago y no muestra un
  pago tardío.
- **HU-19 (resuelto):** el lector de QR reintenta sin pausa tras un error del servidor.
- **HU-22:** en el VPS, `EVENTOS_SITIO_URL` con el dominio real y `EVENTOS_CORREO_PROVEEDOR=smtp`:
  sin ellos, los enlaces apuntan a localhost o el simulador queda activo sin avisar.
- **HU-22 (resuelto):** `EVENTOS_PAGO_PROVEEDOR` es obligatorio en Docker: sin él, el compose no
  arranca. En el VPS va `libelula`.
- **Con Libélula:** la tarea de cada minuto consulta al proveedor por cada cobro abandonado durante
  24 horas: medir su costo en el VPS.
- **HU-22 (resuelto):** límite de frecuencia en nginx para `/api/visitas` y `/api/pagos/aviso`.
- **HU-22 (resuelto):** healthchecks en compose (#19) y la IP real detrás de Caddy
  (`docker-compose.nube.yml`). En el servidor va siempre con Caddy: sin él, un cliente por IPv6
  llega con IP interna y puede inventar su IP.
- **Sin historia (resuelto, #18 y #21):** un 401 en una pestaña borra la sesión de las demás, y
  cerrar sesión no se propaga entre pestañas. Viene del sistema base.

---

## Sprint 2 (06/11 a 11/11)

**Objetivo:** el catálogo es público, el Administrador gestiona sus eventos y el Cliente reserva y
consulta sus entradas, en un servicio propio de Eventos y Venta.

**Backlog:** HU-10 a HU-16.

**Resultado:** HU-10 a HU-16 hechas el 31/10/2026, antes de la fecha prevista. Eventos y Venta en
Docker: gestión de eventos, catálogo, contador de visitas, reservas con el último cupo probado con
concurrencia contra MySQL, y consulta de entradas. Eventos y Venta con 325 pruebas, Seguridad con
233, gateway con 13 y frontend con 905.

**Límite conocido:** la compra llega hasta la reserva. El aviso de la pantalla de reserva sigue
hasta HU-18, y las pantallas de entradas se probaron sin entradas reales, porque las emite el pago.

---

## Sprint 3 (12/11 a 18/11)

**Objetivo:** el Cliente paga su reserva con QR y recibe sus entradas por correo, con los correos
desde un servicio propio de Notificaciones y el pago desde un servicio propio de Pagos.

**Backlog:** HU-17 y HU-18.

**Resultado:** HU-17 y HU-18 hechas, antes de la fecha prevista.

- **HU-17**, el 31/10/2026: Notificaciones manda los tres correos desde el servidor de correo propio
  en Docker, sin que una falla de correo afecte la compra.
- **HU-18**, el 02/11/2026: pago, verificación, pago tardío, entradas y correos con el simulador, y
  la conciliación cada minuto (#13). El adaptador de Libélula (#14) lee el QR con zxing y recibe el
  aviso de pago. Se probó con un pago real de Bs 0,01: compra confirmada y correos recibidos.

Pagos con 63 pruebas, Eventos y Venta con 341, Seguridad con 238, Notificaciones con 21, gateway con 15 y
frontend con 906 (CI del cierre del sprint, #14).

---

## Sprint 4 (19/11 a 25/11)

**Objetivo:** el Personal autorizado valida entradas en la puerta, el Administrador saca reportes y
registra devoluciones, y el sistema queda publicado en el VPS nuevo.

**Backlog:** HU-19 a HU-22.

**Avance:**

- **HU-19, HU-20 y HU-21 hechas el 02/11/2026**, antes de la fecha prevista, probadas por el usuario
  en el navegador:
  - validación de entradas con la cámara (#15);
  - cinco reportes filtrables, exportables a PDF y Excel, y enviados por correo al dominio propio
    (#16);
  - devoluciones de compras y entradas de eventos cancelados (#17). CU-13 pasó de Pagos a Eventos y
    Venta.
- **Mejoras fuera de las historias:**
  - el 06/11/2026: la sesión se comparte entre pestañas (#18), un cliente interno común con
    healthchecks en compose (#19) y la prueba de punta a punta en Docker
    `scripts/prueba-completa.sh` (#20), que pasa 34 de 34;
  - el 10/11/2026: iniciar sesión con la sesión abierta lleva al destino, y cerrar sesión borra la
    sesión guardada (#21).
- **HU-22 hecha el 12/11/2026.** El 10/11/2026 quedó lista la preparación (#22): proveedor de pago
  obligatorio, límites en nginx, HTTPS con Caddy y el despliegue de un comando. El 12/11/2026 se
  desplegó en un VPS Ubuntu 26.10 de 2 CPU y 3,4 GB, y el usuario lo probó por HTTPS:
  - una compra real de Bs 0,01 con Libélula, confirmada sola, con los correos de detalle y de
    entradas en el buzón del cliente;
  - la validación de la entrada con la cámara del celular;
  - el reporte de ventas en PDF y Excel, enviado a dos buzones del dominio propio.

  El primer arranque falló: con MySQL 8.0.46, el `set -eu` de `mysql/crear-bases.sh` cortaba el
  script de la imagen. Se corrigió en `main` con un subshell.

Eventos y Venta con 380 pruebas, Seguridad con 239, Pagos con 64, Notificaciones con 23, gateway con
17 y frontend con 921 (CI del cierre del sprint, 12/11).
