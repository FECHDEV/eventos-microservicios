# HU-17: servicio Notificaciones

Spec: `docs/decisiones.md` → «2. Notificaciones» y `CLAUDE.md` → Correos.

## Tareas

1. **Esqueleto:** `servicios/notificaciones`, paquete `com.sarao.notificaciones`, puerto 8083, sin base.
   Compose, matriz del CI, perfil `correo` y `correo/`. Prueba: `NotificacionesApplicationTests`.
2. **API interna:** `POST /interno/correos/{recuperacion,detalle-de-compra,entradas}`, 202, con clave
   de servicio. Prueba: `CorreosControllerTest`.
3. **Envío:** `NotificadorCorreoSimulado` y `NotificadorCorreoSmtp`. Pruebas:
   `NotificadorCorreoSmtpTest`, `NotificadorCorreoSimuladoTest`, `SeleccionDelNotificadorTest`.
4. **Seguridad:** `RecuperacionSolicitada`, `CorreoDeRecuperacion` y `NotificadorCorreoRemoto`
   reemplazan al simulador. Pruebas: `RecuperacionContrasenaServiceTest`,
   `RecuperacionContrasenaIntegracionTest`, `NotificadorCorreoRemotoTest`.

## Decisiones

- **Después del commit y en segundo plano:** el evento sale tras confirmar la transacción, y la
  llamada a Notificaciones corre aparte. Código delicado: `CorreoDeRecuperacion` con
  `@TransactionalEventListener(AFTER_COMMIT)` y `NotificadorCorreoRemoto.enviarRecuperacion` con
  `@Async`, que atrapa los fallos sin registrar el token.
- El simulador sigue por defecto. El correo real se activa con `EVENTOS_CORREO_PROVEEDOR=smtp` y
  `--profile correo`.
- El correo con reportes adjuntos entra con HU-20. Los de compra quedan listos para HU-18.

## Resultado

- **Suites:** Notificaciones con 20 pruebas y Seguridad con 236, en verde.
- **Prueba en Docker con el servidor de correo real:** el correo de recuperación llegó a un buzón, en
  texto y HTML con el nombre escapado. Su enlace cambió la contraseña y el token no sirvió dos veces.
  Otro dominio se rechaza y queda en el log, sin salir del servidor.
- **Sin revelar cuentas:** con buzón, sin buzón, sin cuenta y con Notificaciones caído responde 202
  por debajo de 0,1 s. El token no aparece en ningún log.
- **Prueba manual en el navegador:** pasada por el usuario, con el correo leído en el webmail.
- **Sin probar:** los correos de compra con el servidor real, porque los pide HU-18.
- **Pendiente:** `/code-review`, `/security-review` y CI, que necesitan el commit.
