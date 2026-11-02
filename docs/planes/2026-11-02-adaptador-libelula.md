# Adaptador de Libélula en Pagos (CU-07)

Spec: `docs/decisiones.md` → «Adaptador de Libélula» y «El QR del proveedor llega como texto o como
imagen». Es el adaptador del sistema base, movido al servicio Pagos; las reglas de CU-07 no cambian.

## Tareas

1. **Adaptador:** `AdaptadorLibelula`, `LectorDeQr` (zxing 3.5.3, autorizada por el usuario),
   `LibelulaConfig` y `LibelulaProperties`. Pruebas: `AdaptadorLibelulaTest`, `LectorDeQrTest`,
   `LibelulaConfigTest`, `SeleccionDelProveedorDePagoTest`.
2. **Aviso:** `GET|POST /api/pagos/aviso` en Pagos, sin sesión, y su ruta pública en el gateway.
   Pruebas: `PagoIntegracionTest`, `EnrutamientoTest`.
3. **Configuración:** variables de Libélula en compose y `.env.example`, vacías.

## Decisiones

- El adaptador ya no lee la duración de `Reserva`, que vive en Eventos y Venta: usa los 10 minutos de
  `CLAUDE.md` → Compra.
- El aviso va por una ruta propia del gateway, sin identidad, como el catálogo.

## Resultado

- **Suites:** Pagos con 63 pruebas y gateway con 15, en verde. Las pruebas usan un servidor falso y
  nunca llaman a Libélula.
- **Prueba real con Libélula (02/11/2026), en Docker con la appkey del usuario:** deuda de Bs 0,01
  registrada con QR y página de pago; el usuario pagó desde su banco y la pantalla lo detectó en unos
  40 s. Reserva confirmada, una entrada activa y los dos correos de la compra en el buzón del
  Cliente. Sin errores en los logs.
- **Visto en la prueba:** Libélula no mandó una `fecha_pago` legible, así que vale la hora en que el
  sistema aplicó el pago (segundos después).
- **Pendiente:** revisiones y CI, que necesitan el commit.
