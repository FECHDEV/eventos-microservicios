# HU-21: gestionar devoluciones (CU-13)

Spec: `docs/criterios-aceptacion/CU-13.md`. Las pantallas ya vinieron con HU-09 (RF-14 y RF-17).

## Tareas

1. **Corte:** CU-13 pasa de Pagos a Eventos y Venta (decidido con el usuario: `CLAUDE.md`,
   `decisiones.md` → Corte en servicios, backlog).
2. **Eventos y Venta:** `GestionDevolucionesService`, `DevolucionesController`
   (`GET /api/devoluciones`, `GET /api/devoluciones/{idEvento}`,
   `POST /api/devoluciones/compras/{idReserva}`, `POST /api/devoluciones/entradas/{idEntrada}`), solo
   ADMINISTRADOR, con las consultas y actualizaciones condicionadas de `EntradaRepository`. Prueba:
   `DevolucionesIntegracionTest` (RF-1 a RF-13).
3. **Gateway:** `/api/devoluciones/**` hacia Eventos y Venta. Prueba: `EnrutamientoTest`.

## Decisiones

- Es la gestión del sistema base: la compra se agrupa por reserva (antes, por pago: es lo mismo, uno
  a uno), el cliente se pide a Seguridad en lote y el monto es el total de la reserva.
- **La fecha de la compra es la de emisión de sus entradas:** el pago vive en Pagos, y las entradas
  se emiten al confirmarse.
- El listado del evento no abre una transacción mientras espera a Seguridad.

## Resultado

- **Suites:** Eventos y Venta con 379 pruebas y gateway con 15, en verde.
- **Prueba en Docker contra MySQL, por nginx y el gateway:** dos compras pagadas con el simulador; un
  evento sin cancelar da 409; cancelado, aparece con sus 3 entradas pendientes y las compras con el
  nombre del Cliente traído de Seguridad, el monto y su fecha; devolver la compra registra 2, y otra
  vez 409; 5 registros a la vez de la misma compra dieron 1 registrado y 4 «ya devuelta»; los pagos
  siguen PAGADO; el reporte de ingresos da 150 bruto, 150 devuelto y 0 neto; Cliente recibe 403. Sin
  errores en el log.
- **Prueba manual en el navegador:** pasada por el usuario.
- **Pendiente:** `/code-review`, `/security-review` y CI, que necesitan el commit.
