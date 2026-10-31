# Sprints

Entrega máxima: 09/12/2026. Cada sprint anota su resultado al cerrar, porque de
acá sale la documentación final.

| Sprint | Fechas | Objetivo |
|---|---|---|
| 1 | 30/10 a 07/11 | Repositorio, Docker, CI, gateway y Seguridad, con la complejidad de la contraseña |
| 2 | 08/11 a 16/11 | Eventos y Venta: catálogo, gestión de eventos, reservas y entradas |
| 3 | 17/11 a 25/11 | Notificaciones y Pagos con Libélula; compra de punta a punta |
| 4 | 26/11 a 02/12 | Acceso, reportes, devoluciones, despliegue en el VPS nuevo y pruebas |
| 5 | 03/12 a 09/12 | Documentación de perfil con Scrum, correcciones y margen de la entrega |

---

## Sprint 1 (30/10 a 07/11)

**Objetivo:** un usuario se registra, inicia sesión y gestiona su cuenta a través
del gateway, con el sistema levantado en Docker y el CI en verde.

**Backlog:** HU-01 a HU-09.

**Nota:** CU-03 envía un correo y Notificaciones llega en el sprint 3. Hasta
entonces, Seguridad usa el simulador de correo.

**Resultado:** HU-01 a HU-09 hechas el 30/10/2026. El sitio funciona en Docker: registro, inicio
de sesión, recuperación, datos personales y gestión de usuarios, por nginx y el gateway. Seguridad
con 232 pruebas, gateway con 8 y frontend con 900.

**Pendientes para las próximas historias** (de las revisiones del sprint):

- **HU-18:** al vencer la reserva, la pantalla deja de consultar el pago y no muestra un pago tardío.
- **HU-19:** el lector de QR reintenta sin pausa tras un error del servidor.
- **HU-22:** en el VPS, `EVENTOS_SITIO_URL` con el dominio real y `EVENTOS_CORREO_PROVEEDOR=smtp`:
  sin ellos, los enlaces apuntan a localhost o el simulador queda activo sin avisar.
- **HU-22:** límite de frecuencia en nginx para las rutas públicas de escritura (`/api/visitas`).
- **HU-22:** healthcheck de Seguridad en compose, y revisar la IP real según lo que quede delante
  de nginx. Sin Caddy, un cliente por IPv6 llega con IP interna y puede inventar su IP.
- **Sin historia:** un 401 en una pestaña borra la sesión de las demás, y cerrar sesión no se
  propaga entre pestañas. Viene del sistema base.

---

## Sprint 2 (08/11 a 16/11)

**Objetivo:** el catálogo es público, el Administrador gestiona sus eventos y el Cliente reserva y
consulta sus entradas, en un servicio propio de Eventos y Venta.

**Backlog:** HU-10 a HU-16.

**Resultado:** HU-10 a HU-16 hechas el 31/10/2026, antes de la fecha prevista. Eventos y Venta en
Docker: gestión de eventos, catálogo, contador de visitas, reservas con el último cupo probado con
concurrencia contra MySQL, y consulta de entradas. Eventos y Venta con 325 pruebas, Seguridad con
233, gateway con 13 y frontend con 905.

**Límite conocido:** la compra llega hasta la reserva. El aviso de la pantalla de reserva sigue
hasta HU-18, y las pantallas de entradas se probaron sin entradas reales, porque las emite el pago.
