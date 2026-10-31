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

- **HU-17:** enviar los correos después del commit. Con correo real, una falla o la demora
  revelarían qué cuentas existen.
- **HU-18:** al vencer la reserva, la pantalla deja de consultar el pago y no muestra un pago tardío.
- **HU-19:** el lector de QR reintenta sin pausa tras un error del servidor.
- **HU-22:** límite de frecuencia en nginx para las rutas públicas de escritura (`/api/visitas`).
- **HU-22:** healthcheck de Seguridad en compose, y revisar la IP real según lo que quede delante
  de nginx. Sin Caddy, un cliente por IPv6 llega con IP interna y puede inventar su IP.
- **Sin historia:** un 401 en una pestaña borra la sesión de las demás, y cerrar sesión no se
  propaga entre pestañas. Viene del sistema base.
