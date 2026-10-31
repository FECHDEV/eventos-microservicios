# Activar un postergado con la fecha ya pasada (CU-12 RF-33)

Cambio a un CU cerrado, decidido con el usuario el 31/10/2026 (`decisiones.md` → «Reactivar entradas
utilizadas»).

## Tareas

1. **Regla:** `GestionEstadoEventosService.activar` rechaza con `EventoTerminadoException`, 409
   `EVENTO_TERMINADO`, antes de activar. Prueba: `GestionEstadoEventosServiceTest`.
2. **Frontend:** el mensaje del código en `mensajes-error.ts`.

## Decisiones

- Solo aplica a un POSTERGADO: un borrador sin vender no tiene entradas que reactivar.
- El evento queda POSTERGADO, y postergarlo a una fecha futura lo destraba.
