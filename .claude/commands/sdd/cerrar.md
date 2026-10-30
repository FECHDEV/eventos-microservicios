---
description: Cierra un CU implementado - cobertura RF contra el código, suites completas, revisiones que le tocan y resultado en el plan
argument-hint: [CU-XX]
---

Cerrar `$0` después de implementarlo y antes de abrir el PR. Sigue, en orden,
lo que pide `docs/flujo-sdd.md`. No saltear pasos ni hacerlos «a ojo»:
cada revisión se corre de verdad.

## 1. Cobertura RF → prueba, contra el código

- **Cada RF tiene su prueba.** Para cada RF de `docs/criterios-aceptacion/$0.md`,
  buscar en las pruebas del backend (`servicios/*/src/test/`) y del frontend
  (`*.spec.ts`) las que lo citan. Cuentan:
  - «$0 RF-N»;
  - «RF-N» suelto dentro de un archivo de pruebas cuyo encabezado o `describe`
    nombra a `$0`;
  - un rango o una lista que lo incluya, como «RF-19 a RF-24» o «RF-19, RF-20 y
    RF-24».

  «RF-N» es siempre el del CU, no el del documento académico. Si el CU se
  amplió, alcanza con los RF nuevos y los viejos que el cambio tocó. Si fue
  **solo frontend**, los RF sin comportamiento en pantalla cuentan como
  cubiertos con su prueba del backend: listarlos así en el resultado.
- **Los «Criterios de finalización» del archivo de criterios se cumplen**, uno
  por uno.
- **Lo planeado se cumplió.** Comparar con el plan del CU: anotar lo que se
  implementó distinto de lo planeado, o lo que no se implementó.
- **Un RF sin prueba es bloqueante:** se escribe la prueba antes de seguir.

## 2. Suites completas (`verification-before-completion`)

- Backend: `./mvnw test` en cada servicio tocado.
- Frontend: `npx ng test --watch=false` y `npx ng build` en `frontend/`, sin
  avisos.
- Anotar los números exactos. Si algo falla, `systematic-debugging` y se vuelve
  a correr todo.

## 3. Revisiones que le tocan a este CU

Las que pide `docs/flujo-sdd.md` → «Revisión antes del PR de cada CU». La
lista vive solo ahí: leerla en lugar de suponerla. Hoy son:

- `/code-review`, siempre;
- `/security-review`, según el subsistema o la superficie nueva;
- `web-design-guidelines`, si hay frontend, bajando las reglas actualizadas.

Cada hallazgo real se corrige con su prueba y se vuelve al paso 2. Los
descartados se anotan con su motivo.

## 4. Prueba manual

Pedirle al usuario la prueba manual del flujo principal y de los casos límite
más importantes. Indicarle cómo hacerla: `docker compose up`. No dar el CU
por cerrado sin su confirmación.

## 5. Resultado en el plan

Agregar al plan del CU una sección **«Resultado»** con:

- la tabla de cobertura RF → pruebas;
- los números de las suites;
- los hallazgos de cada revisión: los corregidos y los descartados, con su
  motivo;
- el resultado de la prueba manual;
- lo que quedó distinto de lo planeado.

Las reglas técnicas nuevas que hayan surgido van a `docs/decisiones.md`.

## 6. PR

Preguntar al usuario si se commitea y se abre el PR. `CLAUDE.md`: nada de
commits, pushes ni PRs sin pedido explícito. La descripción del PR resume la
función, las revisiones y las pruebas.

Con el PR abierto, esperar el resultado del CI (`gh pr checks <número>
--watch`) y avisarle al usuario si salió **verde o rojo antes de que mergee**. Si sale
rojo, se corrige en la misma rama.

Al terminar, actualizar la historia en `docs/scrum/product-backlog.md` y el
resultado en `docs/scrum/sprints.md`.
