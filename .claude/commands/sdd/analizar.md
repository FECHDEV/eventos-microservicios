---
description: Revisa, antes de programar, que los criterios, el plan y las reglas del proyecto de un CU sean coherentes (como /speckit.analyze)
argument-hint: [CU-XX]
---

Analizar la coherencia de `$0` **antes de implementarlo**, entre:

- sus criterios, en `docs/criterios-aceptacion/$0.md`;
- su plan, el archivo más reciente de `docs/planes/` que nombre a `$0`;
- las reglas del proyecto: `CLAUDE.md` (la constitución) y `docs/decisiones.md`.

Adaptado del paso `/speckit.analyze` de Spec Kit. Va entre `writing-plans` y
`executing-plans`.

## Solo lectura

Este comando **no modifica ningún archivo**. Entrega un informe; las
correcciones las decide el usuario y se hacen después, en el plan o en los
criterios.

## Qué revisar

1. **Cobertura RF → plan:** cada RF de los criterios tiene al menos una tarea
   **y** al menos una prueba con nombre en el plan. Un RF sin prueba es
   **crítico**. «RF-N» es el RF del CU, no el del documento académico
   (`docs/flujo-sdd.md` → «Numeración de los RF»).
   - Si el CU se está **ampliando**, se revisan solo los RF nuevos, más los
     viejos que el cambio toque.
   - Si el plan es **solo de frontend** (el backend del CU ya existe), se exigen
     tarea y prueba en el plan solo para los RF con comportamiento en pantalla:
     lo que el usuario ve o hace. Los demás se dan por cubiertos **si ya tienen
     su prueba en el backend**, y el informe los lista así, sin callarlos.
2. **Tareas sin RF:** cada tarea responde a algún RF o caso límite. Si no, tiene
   que estar marcada como técnica (infraestructura, refactor) o sobra.
3. **Casos límite:** cada caso límite de los criterios tiene su prueba en el plan.
4. **Contradicciones con lo decidido:** el plan no contradice ninguna decisión
   de `docs/decisiones.md` ni una regla de negocio de `CLAUDE.md`. Revisar en
   especial:
   - no inventar comportamiento;
   - capas;
   - nombres de proveedores solo en su adaptador;
   - sin cuarto correo a clientes;
   - el control de acceso en el backend.
5. **Dudas abiertas:** no queda ningún `[NECESITA ACLARACIÓN]` sin resolver en
   los criterios.
6. **Nombres coherentes:** entidades, estados, endpoints y códigos de error se
   llaman igual en los criterios, el plan y `docs/modelo-diseno-y-datos.md`.
7. **Formato del plan:** respeta `docs/flujo-sdd.md` → «Formato de los
   planes». Lleva código completo solo en lo delicado, y en lo delicado
   (bloqueos, transacciones, pagos, seguridad) **no falta**.
8. **Revisiones que le tocan:** el plan termina con las que pide
   `docs/flujo-sdd.md` → «Revisión antes del PR de cada CU» para este CU. La
   lista vive solo ahí: no copiarla.

## Formato del informe

Una tabla, de lo más grave a lo menos grave:

| Gravedad | Revisión | Dónde | Qué pasa | Qué corregir |
|---|---|---|---|---|

- **Crítico:** un RF sin prueba, una contradicción con `decisiones.md` o
  `CLAUDE.md`, o una duda abierta. **No se empieza a programar** hasta
  corregirlo.
- **Medio:** un caso límite sin prueba, una tarea sin RF, nombres que no
  coinciden, o falta código completo en algo delicado.
- **Menor:** formato, redacción.

Al final, la tabla de cobertura **RF → tarea → pruebas** completa. Si no hay
hallazgos críticos, avisar que se puede seguir con `executing-plans`.
