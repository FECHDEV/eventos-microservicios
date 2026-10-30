# HU-04: inicio de sesión y sesión en el gateway (CU-02)

Spec: `docs/criterios-aceptacion/CU-02.md`. Solo backend; la pantalla entra con HU-09.

## Tareas

1. **Token:** `TokenJwt`, `JwtConfig`. Pruebas: `TokenJwtTest`, `JwtConfigTest` (RF-4, RF-5, RF-20, RF-21).
2. **Inicio de sesión:** `AutenticacionService`, `LimiteIntentos`, `AuthController`. Pruebas:
   `AutenticacionServiceTest`, `LimiteIntentosTest`, `AuthControllerTest` (RF-1 a RF-3, RF-6,
   RF-11 a RF-15, RF-22, RF-23).
3. **Administrador inicial:** `AdministradorInicial`. Pruebas: `AdministradorInicialTest`
   (RF-16 a RF-19).
4. **Identidad en Seguridad:** `POST /interno/sesiones` e `IdentidadDelGatewayFiltro`. Pruebas:
   `InicioSesionIntegracionTest` (RF-7 a RF-10, RF-21).
5. **Gateway:** `RutasConfig` e `IdentidadDeSesion`. Pruebas: `EnrutamientoTest` (RF-7 a RF-9,
   encabezados falsos del cliente, Seguridad con error).
6. **Límite de registros:** `LimiteRegistros` (CU-01 RF-17). Prueba: `AuthControllerTest`.

## Decisiones

- Solo Seguridad tiene la clave JWT (`decisiones.md` → «Sesión»).
- `usuarioDelToken` e inicio de sesión sin `@Transactional`: BCrypt no retiene una conexión.
- El rol SERVICIO solo entra a `/interno`; las rutas de usuarios exigen un rol de usuario.

## Revisiones

`/code-review` y `/security-review`, según `docs/flujo-sdd.md`.

## Resultado

- **Suites:** `seguridad` y `gateway` en verde.
- **Prueba de punta a punta** por el gateway contra MySQL en Docker: los casos de los
  criterios de finalización, más identidad falsa del cliente (401) y `/interno` (404).
- **`/security-review`:** sin vulnerabilidades. Detectó que `X-Forwarded-For` no llegaba a
  Seguridad; se confirmó con una prueba y se corrigió con `trusted-proxies`.
- **`/code-review`, corregidos:** tamaño máximo en el inicio de sesión, `toString` sin
  secretos, clave de servicio en blanco no arranca, variables del administrador opcionales
  en compose, límite de registros en el servicio y 503 cuando Seguridad falla.
- **`/code-review`, descartados:** contar el intento antes de comparar (decisión del sistema
  base contra ráfagas), encabezados inválidos con la clave correcta (solo los manda el
  gateway), código para HU-06 a HU-08 del mismo sprint, y `docs/` en la rama (ya acordado).
