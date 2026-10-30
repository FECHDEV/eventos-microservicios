# Especificación completa de casos de uso

Las 15 fichas de la sección 2.9 del documento académico, en su versión
final: incluyen las correcciones hechas al empezar la Fase 5, cuyo motivo
está en `decisiones.md`.

---

## Actores

| ID | Actor | Tipo | Descripción |
|---|---|---|---|
| A-01 | Cliente | Humano | Persona que interactúa con el sistema para consultar información sobre los eventos y, mediante una cuenta registrada, acceder a las funcionalidades relacionadas con la adquisición y gestión de entradas. |
| A-02 | Administrador | Humano | Usuario responsable de gestionar la información y las operaciones administrativas relacionadas con eventos, usuarios, entradas y reportes. |
| A-03 | Personal autorizado | Humano | Usuario encargado de verificar y validar las entradas durante el acceso a los eventos. |
| A-04 | Sistema de pago externo | Sistema externo | Sistema encargado de procesar el pago correspondiente a la adquisición de entradas y comunicar el resultado de la operación. |
| A-05 | Servicio de correo electrónico | Sistema externo | Servicio encargado de gestionar el envío de correos electrónicos generados por el sistema. |
| A-06 | Tiempo | Actor temporal | Dispara el cierre automático de ventas y la liberación de reservas expiradas. |
| A-07 | Usuario | Humano, abstracto (padre de A-01, A-02, A-03) | Rol común heredado por Cliente, Administrador y Personal autorizado; captura las relaciones compartidas (iniciar sesión, recuperar contraseña, gestionar datos personales). |

---

## CU-01 — REGISTRARSE

**Breve descripción:** El Cliente proporciona sus datos personales, incluyendo su fecha de nacimiento, para crear una cuenta en el sistema. Esto le permite posteriormente iniciar sesión y acceder a las funcionalidades reservadas a usuarios registrados, como la adquisición de entradas.

**Actor principal:** Cliente
**Actor secundario:** —

**Precondiciones:**
1. El Cliente no cuenta con una cuenta previamente registrada en el sistema con los mismos datos de identificación.

**Flujo principal:**
1. El caso de uso empieza cuando el Cliente selecciona la opción de registrarse.
2. El sistema solicita al Cliente los datos personales requeridos, incluyendo su fecha de nacimiento.
3. El Cliente ingresa los datos solicitados.
4. El sistema valida que los datos ingresados sean correctos, estén completos y no correspondan a una cuenta ya existente.
5. El sistema registra la cuenta del Cliente.
6. El sistema confirma al Cliente que el registro se realizó correctamente.

**Postcondiciones:**
1. La cuenta del Cliente ha quedado registrada en el sistema.
2. La fecha de nacimiento del Cliente ha quedado almacenada para las validaciones de edad correspondientes.
3. La cuenta queda registrada en estado activa.

**Flujos alternativos:**
FA-01 Datos incompletos o inválidos (a partir del paso 4)
4.1 El sistema detecta que uno o más datos están incompletos o no cumplen el formato requerido.
4.2 El sistema indica al Cliente los campos a corregir.
4.3 El caso de uso vuelve al paso 3.
FA-02 Cuenta ya existente (a partir del paso 4)
4.1 El sistema detecta que ya existe una cuenta registrada con los datos de identificación proporcionados.
4.2 El sistema informa al Cliente que la cuenta ya existe.
4.3 El caso de uso finaliza sin completar el registro.

**Requisitos funcionales relacionados:** RF-01, RF-05

---

## CU-02 — INICIAR SESIÓN

**Breve descripción:** El Usuario se autentica mediante sus credenciales para obtener acceso a las funcionalidades del sistema que corresponden a su rol. Este caso de uso es común a los actores Cliente, Administrador y Personal autorizado, que heredan la relación por generalización.

**Actor principal:** Usuario (actor abstracto; heredado por Cliente, Administrador y Personal autorizado)
**Actor secundario:** —

**Precondiciones:**
1. El Usuario cuenta con una cuenta registrada y activa en el sistema.

**Flujo principal:**
1. El caso de uso empieza cuando el Usuario selecciona la opción de iniciar sesión.
2. El sistema solicita las credenciales de acceso.
3. El Usuario ingresa sus credenciales.
4. El sistema valida las credenciales proporcionadas.
5. El sistema concede el acceso a las funcionalidades correspondientes al rol del Usuario.

**Postcondiciones:**
1. El Usuario ha quedado autenticado en el sistema.

**Flujos alternativos:**
FA-01 Credenciales incorrectas (a partir del paso 4)
4.1 El sistema detecta que las credenciales no corresponden a una cuenta registrada.
4.2 El sistema informa al Usuario que las credenciales son incorrectas.
4.3 El caso de uso vuelve al paso 3.
FA-02 Cuenta desactivada (a partir del paso 4)
4.1 El sistema detecta que la cuenta se encuentra desactivada.
4.2 El sistema informa al Usuario que no puede acceder al sistema.
4.3 El caso de uso finaliza sin conceder el acceso.

**Requisitos funcionales relacionados:** RF-02

---

## CU-03 — RECUPERAR CONTRASEÑA

**Breve descripción:** El Usuario que no puede acceder a su cuenta solicita la recuperación de su contraseña. El sistema, a través del servicio de correo electrónico, le proporciona el medio para establecer una nueva contraseña y recuperar el acceso. Este caso de uso es común a los actores Cliente, Administrador y Personal autorizado, que heredan la relación por generalización.

**Actor principal:** Usuario (actor abstracto; heredado por Cliente, Administrador y Personal autorizado)
**Actor secundario:** Servicio de correo electrónico

**Precondiciones:**
1. El Usuario cuenta con una cuenta registrada en el sistema.

**Flujo principal:**
1. El caso de uso empieza cuando el Usuario selecciona la opción de recuperar contraseña.
2. El sistema solicita el correo electrónico registrado.
3. El Usuario ingresa su correo electrónico.
4. El sistema verifica que el correo corresponda a una cuenta registrada.
5. El sistema solicita al Servicio de correo electrónico el envío del mensaje de recuperación.
6. El Usuario accede al proceso de recuperación y establece una nueva contraseña.
7. El sistema registra la nueva contraseña y confirma la operación.

**Postcondiciones:**
1. La contraseña del Usuario ha sido actualizada.
2. El Usuario puede autenticarse utilizando la nueva contraseña.

**Flujos alternativos:**
FA-01 Correo electrónico no registrado (a partir del paso 4)
4.1 El sistema detecta que el correo no corresponde a ninguna cuenta registrada.
4.2 El sistema informa al Usuario que no fue posible continuar con el proceso.
4.3 El caso de uso finaliza.
FA-02 Proceso de recuperación caducado (a partir del paso 6)
6.1 El sistema detecta que el plazo del proceso de recuperación ha expirado.
6.2 El sistema informa al Usuario que debe solicitar nuevamente la recuperación.
6.3 El caso de uso finaliza sin actualizar la contraseña.

**Requisitos funcionales relacionados:** RF-03

---

## CU-04 — GESTIONAR DATOS PERSONALES

**Breve descripción:** El Usuario consulta la información personal asociada a su cuenta y actualiza aquellos datos que requiera modificar. Este caso de uso es común a los actores Cliente, Administrador y Personal autorizado, que heredan la relación por generalización.

**Actor principal:** Usuario (actor abstracto; heredado por Cliente, Administrador y Personal autorizado)
**Actor secundario:** —

**Precondiciones:**
1. El Usuario se encuentra autenticado en el sistema.

**Flujo principal:**
1. El caso de uso empieza cuando el Usuario accede a la sección de sus datos personales.
2. El sistema muestra los datos personales registrados.
3. El Usuario modifica los datos que desea actualizar.
4. El sistema valida que los datos ingresados sean correctos y estén completos.
5. El sistema registra los cambios y confirma la actualización.

**Postcondiciones:**
1. Los datos personales del Usuario han quedado actualizados en el sistema.

**Flujos alternativos:**
FA-01 Datos inválidos (a partir del paso 4)
4.1 El sistema detecta que uno o más datos no cumplen el formato requerido.
4.2 El sistema indica al Usuario los campos a corregir.
4.3 El caso de uso vuelve al paso 3.
FA-02 Consulta sin modificación (a partir del paso 3)
3.1 El Usuario no modifica ningún dato.
3.2 El caso de uso finaliza sin registrar cambios.

**Requisitos funcionales relacionados:** RF-04

---

## CU-05 — CONSULTAR EVENTOS

**Breve descripción:** El Cliente consulta la información de los eventos disponibles, pudiendo aplicar criterios de búsqueda y acceder al detalle de un evento en particular. Esta consulta no requiere que el Cliente se encuentre autenticado.

**Actor principal:** Cliente
**Actor secundario:** —

**Precondiciones:**
Ninguna.

**Flujo principal:**
1. El caso de uso empieza cuando el Cliente accede a la consulta de eventos.
2. El sistema muestra los eventos en estado activo y postergado. Los eventos en estado borrador y cancelado no se incluyen.
3. El Cliente aplica los criterios de búsqueda o filtrado que considere.
4. El sistema muestra los eventos que cumplen con los criterios indicados.
5. El Cliente selecciona un evento.
6. El sistema muestra la información detallada del evento seleccionado.

**Postcondiciones:**
1. El Cliente ha consultado la información de los eventos.

**Flujos alternativos:**
FA-01 Sin eventos disponibles (a partir del paso 2)
2.1 El sistema detecta que no existen eventos disponibles.
2.2 El sistema informa al Cliente que no hay eventos para mostrar.
2.3 El caso de uso finaliza.
FA-02 Sin resultados para los criterios indicados (a partir del paso 4)
4.1 El sistema detecta que ningún evento cumple con los criterios aplicados.
4.2 El sistema informa al Cliente que no se encontraron resultados.
4.3 El caso de uso vuelve al paso 3.

**Requisitos funcionales relacionados:** RF-10, RF-20

---

## CU-06 — ADQUIRIR ENTRADAS

**Breve descripción:** El Cliente selecciona un evento, un tipo de entrada y la cantidad que desea adquirir. El sistema valida las condiciones correspondientes, reserva temporalmente las entradas seleccionadas e incluye el caso de uso Realizar pago para completar la compra. Cada compra corresponde a un único tipo de entrada.

**Actor principal:** Cliente
**Actor secundario:** Tiempo

**Precondiciones:**
1. El Cliente se encuentra autenticado en el sistema.
2. El evento se encuentra activo y con la venta de entradas abierta.

**Flujo principal:**
1. El caso de uso empieza cuando el Cliente selecciona el evento, un tipo de entrada y la cantidad que desea adquirir. Cada compra corresponde a un único tipo de entrada.
2. El sistema verifica la disponibilidad de las entradas solicitadas.
3. El sistema verifica que el Cliente cumpla la restricción de edad del evento a la fecha en que este se realiza, si corresponde.
4. El sistema verifica que la cantidad solicitada, sumada a las entradas que el Cliente ya posee para ese evento y a sus reservas vigentes, no supere el límite de compra establecido, si corresponde.
5. El sistema reserva temporalmente las entradas seleccionadas por un período de 10 minutos.
6. incluir(Realizar pago)
7. El sistema confirma la compra al Cliente.

**Postcondiciones:**
1. La compra ha quedado confirmada y las entradas correspondientes han sido generadas.
2. Las entradas reservadas han dejado de estar disponibles para otros clientes.

**Flujos alternativos:**
FA-01 Entradas no disponibles (a partir del paso 2)
2.1 El sistema detecta que no existe disponibilidad suficiente para la cantidad solicitada.
2.2 El sistema informa al Cliente.
2.3 El caso de uso finaliza.
FA-02 Restricción de edad no cumplida (a partir del paso 3)
3.1 El sistema detecta que el Cliente no cumple con la restricción de edad establecida.
3.2 El sistema informa al Cliente.
3.3 El caso de uso finaliza.
FA-03 Límite de compra excedido (a partir del paso 4)
4.1 El sistema detecta que la cantidad solicitada supera el límite establecido para el evento.
4.2 El sistema informa al Cliente.
4.3 El caso de uso vuelve al paso 1.
FA-04 Vencimiento del tiempo de reserva (a partir del paso 5)
5.1 El actor Tiempo dispara la liberación de las entradas al cumplirse los 10 minutos sin que el Cliente haya confirmado el pago.
5.2 El sistema libera las entradas reservadas.
5.3 El caso de uso finaliza sin completar la compra.
FA-05 Error del sistema de pago (a partir del paso 6)
6.1 El caso de uso incluido Realizar pago informa que el cobro no pudo generarse o que el Sistema de pago externo reportó un error.
6.2 El sistema libera las entradas reservadas.
6.3 El caso de uso finaliza sin completar la compra.

**Requisitos funcionales relacionados:** RF-21, RF-22, RF-23, RF-24, RF-25, RF-26, RF-34

---

## CU-07 — REALIZAR PAGO

**Breve descripción:** El Cliente realiza el pago de las entradas reservadas mediante un código QR a través del Sistema de pago externo. El sistema verifica el resultado del pago, confirma la compra cuando el pago se realiza, genera las entradas correspondientes y envía el detalle de la compra y las entradas al Cliente por correo electrónico. Este caso de uso es una inclusión del caso de uso Adquirir entradas.

**Actor principal:** Cliente
**Actor secundario:** Sistema de pago externo, Servicio de correo electrónico

**Precondiciones:**
1. Existe una reserva temporal de entradas asociada al Cliente, generada por el caso de uso Adquirir entradas.

**Flujo principal:**
1. El caso de uso empieza cuando el sistema presenta al Cliente el código QR para realizar el pago.
2. El Cliente realiza el pago a través del Sistema de pago externo escaneando el código QR.
3. El sistema solicita al Sistema de pago externo la verificación del resultado del pago.
4. El Sistema de pago externo comunica el resultado del pago.
5. El sistema verifica que el pago haya sido realizado.
6. El sistema confirma la compra y genera las entradas correspondientes, cada una con su código QR único.
7. El sistema genera el detalle de la venta.
8. El sistema solicita al Servicio de correo electrónico el envío del detalle de la compra al correo del Cliente.
9. El sistema solicita al Servicio de correo electrónico el envío de las entradas generadas al correo del Cliente.

**Postcondiciones:**
1. La compra ha quedado confirmada.
2. Las entradas han sido generadas con su código QR único y estado activa.
3. El detalle de la compra y las entradas han sido enviados al correo del Cliente.

**Flujos alternativos:**
FA-01 Error del sistema de pago (a partir de los pasos 1 o 4)
1.1 El sistema detecta que el cobro no pudo generarse o que el Sistema de pago externo reportó un error.
1.2 El sistema informa al Cliente.
1.3 El caso de uso finaliza sin confirmar la compra.
FA-02 Pago pendiente (a partir del paso 4)
4.1 El Sistema de pago externo informa que el pago todavía no se realizó.
4.2 El sistema mantiene la reserva de las entradas hasta su vencimiento, a los 10 minutos de haberse creado.
4.3 El sistema repite la verificación hasta que el pago se realice o la reserva venza.
FA-03 Pago recibido después del vencimiento de la reserva (a partir del paso 5)
5.1 El sistema detecta que el pago se realizó después de que venció la reserva.
5.2 Si todavía hay disponibilidad para la cantidad reservada, el sistema vuelve a descontarla y el caso de uso continúa en el paso 6.
5.3 Si no hay disponibilidad, el sistema registra el pago como pendiente de devolución fuera del sistema, informa al Cliente y el caso de uso finaliza sin generar entradas.

**Requisitos funcionales relacionados:** RF-27, RF-28, RF-29, RF-30, RF-31, RF-32, RF-42, RF-43, RF-44, RF-34

---

## CU-08 — CONSULTAR ENTRADAS

**Breve descripción:** El Cliente consulta las entradas correspondientes a sus compras confirmadas, verificando su estado y accediendo al código QR asociado a cada una.

**Actor principal:** Cliente
**Actor secundario:** —

**Precondiciones:**
1. El Cliente se encuentra autenticado en el sistema.
2. El Cliente cuenta con al menos una compra confirmada.

**Flujo principal:**
1. El caso de uso empieza cuando el Cliente accede a la consulta de sus entradas.
2. El sistema muestra las entradas correspondientes a sus compras confirmadas, indicando el estado de cada una.
3. El Cliente selecciona una entrada.
4. El sistema muestra el detalle de la entrada junto con su código QR.

**Postcondiciones:**
1. El Cliente ha consultado la información de sus entradas.

**Flujos alternativos:**
FA-01 Sin entradas registradas (a partir del paso 2)
2.1 El sistema detecta que el Cliente no posee entradas asociadas a compras confirmadas.
2.2 El sistema informa al Cliente que no hay entradas para mostrar.
2.3 El caso de uso finaliza.

**Requisitos funcionales relacionados:** RF-33

---

## CU-09 — GESTIONAR USUARIOS

**Breve descripción:** El Administrador consulta los usuarios registrados en el sistema, asigna los roles que determinan el acceso a las funcionalidades y gestiona el estado de las cuentas mediante su activación o desactivación.

**Actor principal:** Administrador
**Actor secundario:** —

**Precondiciones:**
1. El Administrador se encuentra autenticado en el sistema.

**Flujo principal:**
1. El caso de uso empieza cuando el Administrador accede a la gestión de usuarios.
2. El sistema muestra los usuarios registrados con su rol y estado.
3. El Administrador selecciona un usuario.
4. El Administrador modifica el rol asignado o el estado de la cuenta.
5. El sistema valida la operación solicitada.
6. El sistema registra los cambios y confirma la operación.

**Postcondiciones:**
1. El rol o el estado de la cuenta del usuario ha quedado actualizado.
2. El acceso a las funcionalidades se aplica de acuerdo con el rol asignado.

**Flujos alternativos:**
FA-01 Desactivación de cuenta (a partir del paso 4)
4.1 El Administrador selecciona desactivar la cuenta del usuario.
4.2 El sistema registra la cuenta como desactivada.
4.3 El caso de uso continúa en el paso 6.
FA-02 Operación no permitida (a partir del paso 5)
5.1 El sistema detecta que la operación solicitada corresponde a alguno de los siguientes casos no permitidos:
a) el Administrador intenta modificar su propio rol;
b) el Administrador intenta desactivar su propia cuenta;
c) la operación dejaría al sistema sin ningún administrador activo;
d) se intenta modificar el rol de un usuario cuya cuenta está desactivada.
5.2 El sistema informa al Administrador y no registra los cambios.
5.3 El caso de uso vuelve al paso 3.

**Requisitos funcionales relacionados:** RF-06, RF-07

---

## CU-10 — GESTIONAR EVENTOS

**Breve descripción:** El Administrador registra nuevos eventos y modifica la información de los ya registrados, estableciendo su descripción, fecha y horario, lugar, restricción de edad y límite de compra por cliente.

**Actor principal:** Administrador
**Actor secundario:** —

**Precondiciones:**
1. El Administrador se encuentra autenticado en el sistema.

**Flujo principal:**
1. El caso de uso empieza cuando el Administrador accede a la gestión de eventos.
2. El sistema muestra los eventos registrados.
3. El Administrador selecciona registrar un nuevo evento o modificar uno existente.
4. El sistema solicita la información del evento: descripción, fecha, hora de inicio y finalización, lugar, restricción de edad y límite de compra.
5. El Administrador ingresa la información solicitada.
6. El sistema valida que la información sea correcta y esté completa.
7. El sistema registra el evento y confirma la operación.

**Postcondiciones:**
1. El evento ha quedado registrado o actualizado con su configuración correspondiente. Un evento recién registrado queda en estado borrador hasta que se le definan sus tipos de entrada y el Administrador lo active.

**Flujos alternativos:**
FA-01 Información incompleta o inválida (a partir del paso 6)
6.1 El sistema detecta que uno o más datos están incompletos o no cumplen el formato requerido.
6.2 El sistema indica al Administrador los campos a corregir.
6.3 El caso de uso vuelve al paso 5.
FA-02 Evento sin límite de compra (a partir del paso 5)
5.1 El Administrador determina que el evento no tendrá límite máximo de entradas por cliente.
5.2 El caso de uso continúa en el paso 6.
FA-03 Evento sin restricción de edad (a partir del paso 5)
5.1 El Administrador determina que el evento no tendrá restricción de edad.
5.2 El caso de uso continúa en el paso 6.

**Requisitos funcionales relacionados:** RF-08, RF-09, RF-11, RF-12, RF-15, RF-16

---

## CU-11 — GESTIONAR TIPOS DE ENTRADAS

**Breve descripción:** El Administrador establece los tipos de entrada disponibles para un evento, junto con sus precios y la cantidad disponible de cada uno, y consulta la disponibilidad existente.

**Actor principal:** Administrador
**Actor secundario:** —

**Precondiciones:**
1. El Administrador se encuentra autenticado en el sistema.
2. Existe al menos un evento registrado.

**Flujo principal:**
1. El caso de uso empieza cuando el Administrador selecciona un evento y accede a la gestión de tipos de entradas.
2. El sistema muestra los tipos de entrada registrados con su precio y cantidad disponible.
3. El Administrador registra un nuevo tipo de entrada o modifica uno existente.
4. El Administrador establece el precio y la cantidad disponible correspondientes.
5. El sistema valida la información ingresada.
6. El sistema registra los cambios y confirma la operación.

**Postcondiciones:**
1. Los tipos de entrada, sus precios y su disponibilidad han quedado registrados para el evento.

**Flujos alternativos:**
FA-01 Información inválida (a partir del paso 5)
5.1 El sistema detecta que el precio o la cantidad no cumplen con los valores permitidos.
5.2 El sistema indica al Administrador los campos a corregir.
5.3 El caso de uso vuelve al paso 4.

**Requisitos funcionales relacionados:** RF-13, RF-14

---

## CU-12 — GESTIONAR ESTADO DEL EVENTO

**Breve descripción:** El Administrador gestiona el ciclo de vida del evento, pudiendo activarlo, postergarlo, cancelarlo o cerrar manualmente la venta de entradas. El sistema cierra automáticamente la venta al cumplirse la hora de finalización establecida, y conserva la validez de las entradas ante la postergación o cancelación del evento. La notificación a los clientes ante la postergación o cancelación de un evento se realiza fuera del sistema.

**Actor principal:** Administrador
**Actor secundario:** Tiempo

**Precondiciones:**
1. El evento se encuentra registrado en el sistema.

**Flujo principal:**
1. El caso de uso empieza cuando el Administrador selecciona un evento y el nuevo estado que desea aplicar.
2. Si el Administrador selecciona activar el evento, el sistema verifica que tenga al menos un tipo de entrada definido y establece el evento como activo.
3. Si el Administrador selecciona postergar el evento, el sistema solicita la nueva fecha, la asocia a las entradas existentes y conserva su validez.
4. Si el Administrador selecciona cancelar el evento, el sistema establece el evento como cancelado y conserva el registro de las entradas asociadas para el proceso de devolución.
5. Si el Administrador selecciona cerrar manualmente la venta, el sistema cierra la venta de entradas del evento.
6. El sistema confirma la operación realizada.

**Postcondiciones:**
1. El evento refleja el estado y la configuración correspondientes a la operación realizada.

**Flujos alternativos:**
FA-01 Cierre automático de la venta (durante la vigencia del evento)
1.1 El actor Tiempo dispara el cierre de la venta al cumplirse la hora de finalización establecida para el evento.
1.2 El sistema cierra automáticamente la venta de entradas del evento.
FA-02 Reactivación de entradas utilizadas por postergación (a partir del paso 3)
3.1 El sistema detecta que existen entradas que fueron utilizadas antes de la postergación.
3.2 El sistema permite su reactivación cuando el evento vuelve a estar activo, asociándolas a la nueva fecha.

**Requisitos funcionales relacionados:** RF-17, RF-18, RF-19, RF-37, RF-38

---

## CU-13 — GESTIONAR DEVOLUCIONES

**Breve descripción:** El Administrador identifica las entradas asociadas a eventos cancelados y registra, una vez efectuada la devolución económica fuera del sistema, que la entrada ha sido devuelta.

**Actor principal:** Administrador
**Actor secundario:** —

**Precondiciones:**
1. El Administrador se encuentra autenticado en el sistema.
2. Existe al menos un evento con estado cancelado.

**Flujo principal:**
1. El caso de uso empieza cuando el Administrador accede a la gestión de devoluciones.
2. El sistema muestra los eventos cancelados.
3. El Administrador selecciona un evento cancelado.
4. El sistema muestra las entradas asociadas al evento que se encuentran en estado activa, que son las pendientes de devolución.
5. El Administrador verifica las entradas correspondientes.
6. Una vez realizada la devolución económica fuera del sistema, el Administrador registra que la entrada ha sido devuelta.
7. El sistema actualiza el estado de la entrada a devuelta y confirma la operación.

**Postcondiciones:**
1. La entrada ha quedado registrada con el estado devuelta.
2. El sistema conserva el registro de la devolución realizada.

**Flujos alternativos:**
FA-01 Sin entradas pendientes de devolución (a partir del paso 4)
4.1 El sistema detecta que no existen entradas pendientes de devolución para el evento.
4.2 El sistema informa al Administrador.
4.3 El caso de uso vuelve al paso 3.
FA-02 Entrada ya devuelta (a partir del paso 6)
6.1 El sistema detecta que la entrada ya se encuentra registrada como devuelta.
6.2 El sistema informa al Administrador y no registra la operación.
6.3 El caso de uso vuelve al paso 5.

**Requisitos funcionales relacionados:** RF-39, RF-40, RF-41, RF-34

---

## CU-14 — GESTIONAR REPORTES

**Breve descripción:** El Administrador genera reportes de ventas, ingresos, entradas, clientes o accesos, pudiendo filtrarlos según los criterios definidos para cada tipo y exportarlos en formato PDF o Excel.

**Actor principal:** Administrador
**Actor secundario:** —

**Precondiciones:**
1. El Administrador se encuentra autenticado en el sistema.

**Flujo principal:**
1. El caso de uso empieza cuando el Administrador selecciona el tipo de reporte que desea generar: ventas, ingresos, entradas, clientes o accesos.
2. El sistema solicita los criterios de filtrado disponibles para el tipo de reporte seleccionado:
Ventas: rango de fechas, evento, tipo de entrada.
Ingresos: rango de fechas, evento.
Entradas: evento, tipo de entrada, estado de la entrada.
Clientes: rango de fechas de registro, evento en el que adquirió entradas.
Accesos: evento, rango de fechas, personal autorizado que validó.
3. El Administrador establece los criterios de filtrado que considere.
4. El sistema genera el reporte de acuerdo con los criterios establecidos.
5. El sistema presenta el reporte al Administrador. En el reporte de ingresos se distingue el monto bruto, el monto correspondiente a entradas devueltas y el monto neto resultante.
6. El Administrador selecciona exportar el reporte en formato PDF o Excel.
7. El sistema genera el archivo del reporte en el formato seleccionado.

**Postcondiciones:**
1. El reporte ha sido generado y, si corresponde, exportado en el formato solicitado.

**Flujos alternativos:**
FA-01 Reporte sin filtros (a partir del paso 3)
3.1 El Administrador no establece criterios de filtrado.
3.2 El caso de uso continúa en el paso 4 con toda la información disponible.
FA-02 Sin resultados para los criterios indicados (a partir del paso 4)
4.1 El sistema detecta que no existen datos que cumplan los criterios establecidos.
4.2 El sistema informa al Administrador.
4.3 El caso de uso vuelve al paso 3.
FA-03 Consulta sin exportación (a partir del paso 6)
6.1 El Administrador no selecciona exportar el reporte.
6.2 El caso de uso finaliza.

**Requisitos funcionales relacionados:** RF-45, RF-46, RF-47, RF-48, RF-49, RF-50, RF-51, RF-52, RF-53

---

## CU-15 — VALIDAR ENTRADA

**Breve descripción:** El Personal autorizado escanea el código QR de una entrada durante el acceso al evento. El sistema verifica su correspondencia con el evento, su vigencia y su utilización previa, y registra el acceso cuando la validación es correcta.

**Actor principal:** Personal autorizado
**Actor secundario:** —

**Precondiciones:**
1. El Personal autorizado se encuentra autenticado en el sistema.
2. El evento se encuentra en estado activo.
3. El Personal autorizado ha seleccionado el evento para el cual va a validar entradas.

**Flujo principal:**
1. El caso de uso empieza cuando el Personal autorizado escanea el código QR de una entrada.
2. El sistema verifica que la entrada corresponda al evento.
3. El sistema verifica la vigencia de la entrada: que su estado sea activa, que el evento asociado se encuentre en estado activo y que la fecha del evento corresponda al día en curso.
4. El sistema verifica que la entrada no haya sido utilizada previamente.
5. El sistema registra el acceso y actualiza el estado de la entrada a utilizada.
6. El sistema indica al Personal autorizado que la validación se realizó correctamente.

**Postcondiciones:**
1. La entrada ha quedado registrada con el estado utilizada.
2. El acceso ha quedado registrado en el sistema.

**Flujos alternativos:**
FA-01 Entrada no corresponde al evento (a partir del paso 2)
2.1 El sistema detecta que la entrada no corresponde al evento.
2.2 El sistema indica al Personal autorizado que la entrada no es válida.
2.3 El caso de uso finaliza sin registrar el acceso.
FA-02 Entrada no vigente (a partir del paso 3)
3.1 El sistema detecta que la entrada no se encuentra vigente.
3.2 El sistema indica al Personal autorizado que la entrada no es válida.
3.3 El caso de uso finaliza sin registrar el acceso.
FA-03 Entrada ya utilizada (a partir del paso 4)
4.1 El sistema detecta que la entrada ya fue utilizada previamente.
4.2 El sistema indica al Personal autorizado que la entrada ya fue utilizada.
4.3 El caso de uso finaliza sin registrar el acceso.

**Requisitos funcionales relacionados:** RF-35, RF-36, RF-34

---

## Requisitos funcionales y trazabilidad con los CU

| RF | Nombre | CU | Actor responsable |
|---|---|---|---|
| RF-01 | Registro de usuarios | CU-01 | Cliente |
| RF-02 | Inicio de sesión | CU-02 | Usuario |
| RF-03 | Recuperación de contraseña | CU-03 | Usuario |
| RF-04 | Gestión de datos personales | CU-04 | Usuario |
| RF-05 | Registro de fecha de nacimiento | CU-01 | Cliente |
| RF-06 | Gestión de roles y permisos | CU-09 | Administrador |
| RF-07 | Activación y desactivación de cuentas | CU-09 | Administrador |
| RF-08 | Registro de eventos | CU-10 | Administrador |
| RF-09 | Modificación de eventos | CU-10 | Administrador |
| RF-10 | Consulta de eventos | CU-05 | Cliente |
| RF-11 | Configuración de fecha y horario | CU-10 | Administrador |
| RF-12 | Gestión del lugar del evento | CU-10 | Administrador |
| RF-13 | Gestión de tipos y precios de entradas | CU-11 | Administrador |
| RF-14 | Gestión de disponibilidad de entradas | CU-11 | Administrador |
| RF-15 | Configuración de restricción de edad | CU-10 | Administrador |
| RF-16 | Configuración del límite de compra | CU-10 | Administrador |
| RF-17 | Gestión del estado del evento | CU-12 | Administrador |
| RF-18 | Cierre manual de ventas | CU-12 | Administrador |
| RF-19 | Cierre automático de ventas | CU-12 | Tiempo |
| RF-20 | Consulta de eventos disponibles | CU-05 | Cliente |
| RF-21 | Validación de restricción de edad | CU-06 | Cliente |
| RF-22 | Selección de entradas | CU-06 | Cliente |
| RF-23 | Reserva temporal de entradas | CU-06 | Tiempo |
| RF-24 | Liberación de entradas reservadas | CU-06 | Tiempo |
| RF-25 | Validación del límite de compra | CU-06 | Cliente |
| RF-26 | Validación de edad durante la compra | CU-06 | Cliente |
| RF-27 | Pago mediante código QR | CU-07 | Cliente |
| RF-28 | Verificación del pago | CU-07 | Sistema de pago externo |
| RF-29 | Gestión de pagos pendientes | CU-07 | Sistema de pago externo |
| RF-30 | Confirmación de compra | CU-07 | Cliente |
| RF-31 | Generación de entradas | CU-07 | Cliente |
| RF-32 | Generación de código QR de entrada | CU-07 | Cliente |
| RF-33 | Consulta de entradas adquiridas | CU-08 | Cliente |
| RF-34 | Gestión del estado de las entradas | Transversal (CU-06, CU-07, CU-13, CU-15) | — |
| RF-35 | Validación de entradas mediante código QR | CU-15 | Personal autorizado |
| RF-36 | Registro de acceso | CU-15 | Personal autorizado |
| RF-37 | Gestión de entradas de eventos postergados | CU-12 | Administrador |
| RF-38 | Reactivación de entradas utilizadas por postergación | CU-12 | Administrador |
| RF-39 | Gestión de entradas de eventos cancelados | CU-13 | Administrador |
| RF-40 | Verificación de entradas para devolución | CU-13 | Administrador |
| RF-41 | Registro de devolución | CU-13 | Administrador |
| RF-42 | Generación del detalle de venta | CU-07 | Cliente |
| RF-43 | Envío del detalle de venta por correo electrónico | CU-07 | Servicio de correo electrónico |
| RF-44 | Envío de entradas por correo electrónico | CU-07 | Servicio de correo electrónico |
| RF-45 | Generación de reportes | CU-14 | Administrador |
| RF-46 | Reporte de ventas | CU-14 | Administrador |
| RF-47 | Reporte de ingresos | CU-14 | Administrador |
| RF-48 | Reporte de entradas | CU-14 | Administrador |
| RF-49 | Reporte de clientes | CU-14 | Administrador |
| RF-50 | Reporte de accesos | CU-14 | Administrador |
| RF-51 | Filtrado de reportes | CU-14 | Administrador |
| RF-52 | Exportación de reportes en PDF | CU-14 | Administrador |
| RF-53 | Exportación de reportes en Excel | CU-14 | Administrador |

La descripción completa de cada RF está en prosa dentro de la ficha de su CU,
más arriba en este mismo archivo.

---
