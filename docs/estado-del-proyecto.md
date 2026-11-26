# Estado del proyecto

Resumen para responder preguntas sobre el proyecto desde una sesión que no
tiene la carpeta local. Al 26/11/2026.

## Qué está terminado

- **El sistema:** las 25 historias del `docs/scrum/product-backlog.md` están
  Hechas (141 puntos en 5 sprints: 128 del plan y 13 de la predefensa). El resultado de cada sprint, con pruebas y
  fechas, está en `docs/scrum/sprints.md`.
- **El despliegue:** probado en un VPS con `scripts/subir-al-vps.sh`, con una
  compra real, validación de entradas y envío de reportes por correo.
- **La documentación final** (HU-23, Sprint 5): un Word y un PDF de 179
  páginas, actualizados el 26/11 con HU-24 y HU-25. **No está en este repositorio** (ver
  abajo).

## Después de la predefensa (25/11/2026)

- **HU-24, roles del personal:** cinco roles fijos (Administrador, Organizador de eventos, Contador,
  Control de acceso y Cliente, que ya no se muestra como rol del personal). El Administrador da de
  alta al personal. Detalle en `docs/decisiones.md` → «Roles del personal».
- **HU-25, datos de demostración:** `scripts/datos-de-demostracion.sh [DIA]` llena el sistema como
  si una empresa ya lo usara; `scripts/cuentas-de-prueba.sh` deja una cuenta de prueba por rol
  (correos del tipo `nombre.apellido.NN@dominio`) con su buzón.
- El documento final ya los incluye: en el Sprint 5 están las dos historias nuevas, los requisitos
  RF-59 y RF-60, la tabla «Roles del sistema», los casos de uso con los roles nuevos, los diagramas
  de clases y de secuencia del alta de personal y las capturas de cada rol.

## La documentación final

Está fuera del repositorio, en la carpeta `documentacion/` (en `.gitignore`)
de la computadora del usuario. Sigue la guía Scrum de la materia:

- **Parte I, perfil:** problema, objetivos, alcance, cuatro justificaciones
  (técnica, social, teórica y metodológica), herramientas.
- **Parte II, desarrollo con Scrum:** Sprint 0 (product backlog, roles,
  definición de hecho, herramientas) y Sprints 1 a 5. Cada sprint tiene
  objetivo, backlog, registro diario, scrumboards inicial, intermedio y final,
  diagramas (clases, secuencia, estados), capturas de pantalla, pruebas,
  burndown, revisión y retrospectiva.
- **Artefactos:** product backlog final, componentes, paquetes, modelos de
  datos conceptual, lógico y físico, despliegue, burndown y burnup del
  proyecto.
- Conclusiones, recomendaciones, bibliografía en APA 7 (13 referencias, todas
  citadas en el texto) y anexos (estructura del repositorio, criterios EARS de
  ejemplo).

Decisiones tomadas para el documento:

- Imita el formato del Word del proyecto anterior: este proyecto es la
  «versión 2», que toma como base el sistema en capas (monolito).
- Sin agradecimientos, dedicatoria ni capítulo de la organización: la empresa
  es una empresa de eventos genérica.
- Plan de sprints: S1 30/10–05/11, S2 06/11–11/11, S3 12/11–18/11,
  S4 19/11–25/11, S5 26/11–02/12. El registro diario y los burndown usan el
  trabajo real del historial de git, sin registros inventados.
- Puntos por sprint: S1 41, S2 32, S3 18, S4 29, S5 8.
- Diagramas escritos en PlantUML; capturas tomadas de un entorno aparte con el
  simulador de pago y cuentas ficticias.

El documento se genera en Windows con `documentacion/generador-perfil/generar.sh`
(arma el docx con Python y lo termina con Word por COM), así que solo se puede
regenerar en la computadora del usuario.

## Defensa: 02/12/2026

- No habrá diapositivas.
- **El día antes:** crear un VPS nuevo con `scripts/subir-al-vps.sh` (cada VPS
  tiene otra IP), cargar los datos de demostración con el día de la defensa y
  hacer una compra de prueba para confirmar pago y correos.
- **En la demo:** el pago es real, así que se usan entradas de Bs 0,10. Orden
  sugerido: compra con QR, correo con las entradas, validación con el celular,
  reporte y devolución.
- **Al terminar:** destruir el VPS, que se cobra por hora.

## Cuidados

- Las pruebas con el proveedor de pago cobran dinero real. No pagar deudas
  pendientes que hayan quedado de pruebas anteriores.
- Nunca escribir IPs, contraseñas ni claves en este repositorio, que es
  público.
