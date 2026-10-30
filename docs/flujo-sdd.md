# Flujo SDD para implementar una historia

Cómo se implementa una historia del backlog, de la ficha de
`docs/casos-de-uso.md` al PR. **Leer este archivo antes de empezar una historia.**
Cada historia, o grupo de historias de un mismo CU, es una unidad de trabajo con
su rama `feature/hu-XX-nombre-corto` y su PR. Los comandos `/sdd:*` reciben el CU
(`.claude/commands/sdd/`); las reglas generales están en `CLAUDE.md`.

Es el flujo de [hello-sdd](https://github.com/mouredev/hello-sdd) adaptado a
Scrum, con el paso de análisis de Spec Kit. Los criterios de los 15 CU ya existen:
se copiaron del sistema base. Lo que los microservicios cambian se agrega con la
variante «Cambio a un CU ya cerrado».

## CU nuevo

1. `/sdd:criterios <CU-XX>`: genera `docs/criterios-aceptacion/<CU-XX>.md`
   en notación EARS a partir de `docs/casos-de-uso.md`.
2. Skill `writing-plans`, con ese archivo como spec, en el formato de abajo.
   El plan va en `docs/planes/AAAA-MM-DD-cu-xx-nombre-corto.md`.
3. `/sdd:analizar <CU-XX>`: solo lee y revisa la coherencia de los criterios,
   el plan, `CLAUDE.md` y `decisiones.md`, como `/speckit.analyze`. Con un
   hallazgo crítico no se empieza a programar.
4. `executing-plans` (o `subagent-driven-development` si se piden subagentes),
   con TDD: una tarea a la vez, la prueba primero. En el frontend se carga
   también `angular-developer` (signals, `httpResource`, Signal Forms y
   pruebas); en el backend, los skills de Spring de la capa que se toca.
5. `/sdd:cerrar <CU-XX>`: cobertura RF → prueba contra el código, suites
   completas, las revisiones que le tocan, prueba manual y el «Resultado» en el
   plan.
6. PR. El CI de GitHub Actions corre las mismas pruebas: **no se mergea hasta
   verlo en verde** (`gh pr checks`).

No reemplaza estas skills ni el resto de `docs/`: solo les da criterios
numerados y verificables como entrada.

## Variantes

- **Cambio a un CU ya cerrado** (primero la spec, después el código):
  1. La decisión, con fecha, en `decisiones.md`.
  2. `/sdd:criterios <CU-XX>`, que **amplía** el archivo existente: los RF
     nuevos siguen la numeración y no se renumera nada.
  3. Un **plan nuevo** en formato corto. El plan viejo no se toca: es el
     historial.
  4. `/sdd:analizar` → implementación → `/sdd:cerrar` → PR.
- **Solo frontend de un CU cuyo backend ya existe:** no se vuelve a correr
  `/sdd:criterios`, porque el archivo de criterios ya cubre el CU completo. Se
  sigue desde el plan. Al cerrar, se actualiza en los criterios la nota de
  alcance que decía «solo backend».

## Numeración de los RF

«RF-N» es siempre el RF **del CU**, según su archivo de criterios (RF-1,
RF-2…). Los RF del documento académico se escriben «RF-45 del documento», nunca
solos. Las pruebas citan «CU-XX RF-N», o «RF-N» en un archivo cuyo encabezado o
`describe` nombra al CU. Los RF ya numerados no se renumeran.

## Formato de los planes (desde el 07/10/2026)

Cortos, con el mismo nivel en backend y frontend. Esta regla gana sobre el
formato largo de `writing-plans`. Cada tarea lleva:

- los archivos que toca y qué hace;
- las firmas de lo nuevo: clases, métodos públicos, DTO y endpoints;
- los nombres de sus pruebas, cada una con el RF que cubre (ningún RF sin
  prueba), más los casos borde: respuestas tardías, dos operaciones a la vez,
  datos basura en la URL o en la respuesta;
- las decisiones tomadas y su motivo.

**Código completo en el plan solo para lo delicado:** bloqueos y concurrencia,
transacciones, pagos y dinero, seguridad (tokens, contraseñas, autorización).
Lo rutinario (DTO, mapeos, pantallas, estilos, pruebas obvias) se escribe una
sola vez, en el repositorio. Si el plan lo va a ejecutar otra herramienta u otra
sesión sin contexto, se vuelve al formato largo.

**Motivo:** el código del plan se reescribía casi igual en el repositorio y
quedaba desactualizado (los planes llegaron a unas 3.300 líneas por CU). La
calidad la cuidan TDD, la suite completa y las revisiones antes del PR.

## Revisión antes del PR de cada CU

- `/code-review` sobre los cambios de la rama: siempre.
- `/security-review` en los CU de **Seguridad** (CU-01, 02, 03, 04, 09) y
  **Pagos** (CU-07, 13), por RNF-01 a RNF-03. Fuera de esos, también cuando el
  CU abre una superficie nueva para datos que vienen de afuera: **subida de
  archivos** (la imagen del evento en CU-10), lectura de códigos QR, exportación
  de reportes o cualquier endpoint público.
- `web-design-guidelines` sobre los componentes, si el CU tiene frontend.

## Dónde va cada commit de un CU

- **A `main`:** los criterios y sus decisiones en `docs/decisiones.md`.
- **Primer commit de la rama del CU:** el plan.
- **Antes de abrir el PR:** el resultado de las revisiones y de la prueba
  manual se anota en el plan, y las reglas técnicas nuevas en `decisiones.md`.

## Prueba manual desde el celular

La cámara solo funciona en `localhost` o con HTTPS. Para probar desde el
celular hay que servir el sitio por HTTPS.
