# HU-19: validar entradas (CU-15)

Spec: `docs/criterios-aceptacion/CU-15.md`. Las pantallas ya vinieron con HU-09 (RF-13 a RF-16), con
`jsqr` para leer el QR.

## Tareas

1. **Modelo:** `Acceso` (con `idPersonal`, un id sin llave foránea), `Entrada.verificarVigencia` y
   `marcarUtilizada`, `Evento.admiteValidacion`, y las consultas de `EntradaRepository` y
   `EventoRepository`. Prueba: `EntradaTest` (RF-8, RF-9).
2. **Validación:** `ValidacionDeEntradas`, `AccesosController` (`GET /api/accesos/eventos`,
   `POST /api/accesos`), solo con rol PERSONAL. Prueba: `ValidacionIntegracionTest` (RF-1 a RF-12).
3. **Gateway:** `/api/accesos/**` hacia Eventos y Venta, con identidad. Prueba: `EnrutamientoTest`.
4. **Frontend:** tras un error, la cámara espera 2 s antes de reintentar el mismo código (pendiente
   del sprint 1). Prueba: `validar-entradas.page.spec.ts`.

## Decisiones

- Es la validación del sistema base, sin cambios de reglas. El Personal sale de la sesión del
  gateway; el acceso guarda su id.
- **Bloqueos:** el evento con bloqueo compartido y la entrada con exclusivo, en ese orden. En H2 el
  compartido es exclusivo, así que la prueba de dos validaciones a la vez no prueba el bloqueo de la
  entrada: se prueba contra MySQL.

## Resultado

- **Suites:** Eventos y Venta con 352 pruebas, gateway con 15 y frontend con 907, en verde. `ng build`
  sin avisos.
- **Prueba en Docker contra MySQL, por nginx y el gateway:** cuenta de Personal asignada con CU-09;
  los eventos de hoy para elegir; entrada válida con su tipo, y la segunda vez «ya utilizada»; de
  otro evento, inexistente y devuelta rechazadas; 20 validaciones a la vez de la misma entrada dieron
  1 válida y 19 «ya utilizada», con un solo acceso; «Mis entradas» de Ana la muestra utilizada;
  Cliente y Administrador reciben 403, y un evento postergado 409. Sin errores en el log.
- **Prueba manual en el navegador:** pasada por el usuario.
- **Sin probar:** la cámara del celular, que necesita HTTPS (`docs/flujo-sdd.md`): va con el VPS.
- **CI:** en verde.
- **`/security-review`:** sin vulnerabilidades.
- **`/code-review`, corregidos:** la pausa de la cámara solo vale para lo que lee la cámara; un error
  de red o del servidor se reintenta a los 2 s, y un código que el backend rechaza no se reenvía
  mientras siga a la vista. La prueba del gateway cubre también `/api/accesos`, y un import.
- **`/code-review`, no corregido:**
  - **La puerta espera a las ventas del mismo evento:** el bloqueo compartido de validar espera al
    exclusivo de reservar y confirmar, que son transacciones cortas. Es el orden del sistema base y
    lo que pide RF-9 para la cancelación y la postergación.
  - **Una entrada se reactiva en cada postergación:** coincide con la regla, porque se usó antes de
    esa postergación.
  - **La vigencia del evento se mira dos veces** (servicio y entidad), como en el sistema base.
