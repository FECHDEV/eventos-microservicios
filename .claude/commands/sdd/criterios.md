---
description: Genera los criterios de aceptación en notación EARS de un caso de uso, a partir de docs/casos-de-uso.md
argument-hint: [CU-XX]
---

Generar `docs/criterios-aceptacion/$0.md` para el caso de uso `$0`, adaptado
del prompt "Spec (entrevista)" y la plantilla `spec.md` de
[hello-sdd](https://github.com/mouredev/hello-sdd).

## Si el archivo ya existe: ampliar, no reescribir

Si `docs/criterios-aceptacion/$0.md` ya existe, el CU está cerrado y se le
agrega algo (el paso «Cambio» de SDD: primero la spec, después el código):

- La decisión que origina el cambio ya tiene que estar en `docs/decisiones.md`,
  con su fecha. Si no está, se toma con el usuario y se anota antes de seguir.
- Los RF nuevos **siguen la numeración** del archivo (si el último es RF-18, el
  primero nuevo es RF-19). **Nunca se renumeran** los anteriores: las pruebas
  ya los citan.
- Agregar una nota en el Contexto con la fecha y la decisión, y sumar los
  casos límite, lo que queda fuera de alcance y los criterios de finalización
  del agregado.
- Lo que cambie la ficha del CU se actualiza en `docs/casos-de-uso.md`.

## Fuente — no inventar nada que no esté acá

1. Leer la ficha de `$0` en `docs/casos-de-uso.md` (flujo principal,
   alternativos, precondiciones, postcondiciones).
2. Leer `docs/decisiones.md` y `docs/modelo-diseno-y-datos.md` por si tienen
   algo específico de `$0` o de su subsistema.
3. Leer las reglas de negocio de `CLAUDE.md` que apliquen a `$0`.

Estos tres documentos son la única fuente de requisitos. Este comando no
crea comportamiento nuevo — solo traduce lo que ya está documentado a
criterios verificables uno por uno.

## Si algo queda ambiguo

Preguntar de a una pregunta por vez (máximo 6, con `AskUserQuestion`) sobre
casos límite, errores y alcance — nunca asumir. Si una respuesta implicaría
cambiar un artefacto ya cerrado (la ficha del CU, el modelo de datos, una
decisión de `decisiones.md`, el alcance definido), no resolverlo
acá: señalarlo explícitamente y parar, igual que pide `CLAUDE.md` para el
resto del repositorio. Una ampliación **ya decidida con el usuario y anotada en
`decisiones.md`** (ver «Si el archivo ya existe») no es un cambio a resolver
acá: se traduce a RF como cualquier otra fuente.

## Formato de salida

Escribir `docs/criterios-aceptacion/$0.md` con esta estructura:

```markdown
# Criterios de aceptación — $0

## Contexto
<Qué hace este CU y para quién, en una frase — resumen de la ficha de casos-de-uso.md.
Los RF del documento académico que cubre se citan como «RF-45 del documento».>

Fuentes:
- <Ficha del CU, secciones de decisiones.md, clases y tablas de modelo-diseno-y-datos.md
  y reglas de CLAUDE.md usadas.>

<Decisiones tomadas con el usuario al generar estos criterios, si las hubo.>

## Requisitos funcionales (EARS)
- RF-1: CUANDO <evento>, EL SISTEMA <respuesta esperada>.
- RF-2: SI <condición no deseada>, ENTONCES EL SISTEMA <respuesta>.
- RF-3: MIENTRAS <estado>, EL SISTEMA <comportamiento>.
- RF-4: EL SISTEMA <comportamiento permanente>.

## Casos límite
<Vacíos, duplicados, datos corruptos, concurrencia, límites de negocio...>

## Fuera de alcance
<Lo que la ficha de casos-de-uso.md o CLAUDE.md excluyen explícitamente.>

## Criterios de finalización
<Ej.: todos los RF con test en verde + demo manual del flujo principal.>

## Dudas abiertas
- [NECESITA ACLARACIÓN] <duda, si quedó alguna sin resolver>
```

**Numeración:** «RF-N» es siempre el RF **de este CU** (RF-1, RF-2…). Los RF
del documento académico nunca se escriben solos: «RF-45 del documento». Las
pruebas citan «$0 RF-N», o «RF-N» en un archivo cuyo encabezado o `describe`
nombra a `$0`.

Cada RF debe poder rastrearse a una frase concreta de `docs/casos-de-uso.md`,
`docs/decisiones.md` o `CLAUDE.md`. No agregar requisitos no funcionales
salvo que la ficha o `CLAUDE.md` los mencionen para este CU.

Este comando solo escribe en `docs/criterios-aceptacion/`, salvo la
actualización de la ficha que pida un cambio ya decidido.

## Siguiente paso

Con este archivo ya escrito, avisar que el spec está listo y que el paso
siguiente es usar la skill `writing-plans`, pasándole
`docs/criterios-aceptacion/$0.md` como spec, para generar el plan de tareas en
el formato corto de `docs/flujo-sdd.md` → «Formato de los planes» (gana sobre
el formato largo del skill).
Después del plan, y antes de programar, va `/sdd:analizar $0`.
