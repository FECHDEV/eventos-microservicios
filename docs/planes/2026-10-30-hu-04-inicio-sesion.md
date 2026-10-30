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

- **Suites:** 103 pruebas de `seguridad` y 6 de `gateway` en verde.
- **Prueba de punta a punta** por el gateway contra MySQL en Docker: los casos de los
  criterios de finalización, más identidad falsa del cliente (401) y `/interno` (404).
- **Pendiente:** `/code-review`, `/security-review` y CI, que necesitan el commit.
