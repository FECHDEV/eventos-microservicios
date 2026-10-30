# Modelo de diseño y modelo de datos

Diagrama de clases de diseño (2.11.3) y modelo físico de la base de datos
(2.11.4) en su versión final, con las correcciones hechas al empezar la Fase 5.
Es la referencia al escribir las entidades JPA. El motivo de cada corrección
está en `decisiones.md`.

---

# 1. Diagrama de clases de diseño (2.11.3)

## 1.1. Clases

### Usuario `«abstract»`

**Atributos**

| Visibilidad | Nombre | Tipo |
|---|---|---|
| `-` | idUsuario | Long |
| `-` | nombre | String |
| `-` | apellido | String |
| `-` | correo | String |
| `-` | contrasena | String |
| `-` | telefono | String |
| `-` | estado | EstadoCuenta |
| `-` | fechaRegistro | LocalDateTime |

**Operaciones**

| Visibilidad | Firma | Retorno |
|---|---|---|
| `+` | `autenticar(correo : String, contrasena : String)` | Boolean |
| `+` | `actualizarDatos(datos : DatosPersonales)` | void |
| `+` | `recuperarContrasena(correo : String)` | void |
| `+` | `obtenerRol()` `«abstract»` | String |

### Cliente

Hereda de `Usuario`.

**Atributos**

| Visibilidad | Nombre | Tipo |
|---|---|---|
| `-` | fechaNacimiento | LocalDate |

**Operaciones**

| Visibilidad | Firma | Retorno |
|---|---|---|
| `+` | `obtenerRol()` | String |
| `+` | `consultarEntradas()` | List\<Entrada\> |
| `+` | `calcularEdad()` | int |

### Administrador

Hereda de `Usuario`. Sin atributos propios.

**Operaciones**

| Visibilidad | Firma | Retorno |
|---|---|---|
| `+` | `obtenerRol()` | String |
| `+` | `generarReporte(tipo : TipoReporte, filtros : Filtro)` | Reporte |

### PersonalAutorizado

Hereda de `Usuario`. Sin atributos propios.

**Operaciones**

| Visibilidad | Firma | Retorno |
|---|---|---|
| `+` | `obtenerRol()` | String |
| `+` | `validarEntrada(codigoQR : String, evento : Evento)` | ResultadoValidacion |

### Evento

**Atributos**

| Visibilidad | Nombre | Tipo |
|---|---|---|
| `-` | idEvento | Long |
| `-` | nombre | String |
| `-` | descripcion | String |
| `-` | fecha | LocalDate |
| `-` | horaInicio | LocalTime |
| `-` | horaFin | LocalTime |
| `-` | lugar | String |
| `-` | restriccionEdad | Integer |
| `-` | limiteCompra | Integer |
| `-` | estado | EstadoEvento |
| `-` | ventaAbierta | Boolean |
| `-` | fechaOriginal | LocalDate |

**Operaciones**

| Visibilidad | Firma | Retorno |
|---|---|---|
| `+` | `activar()` | void |
| `+` | `postergar(nuevaFecha : LocalDate)` | void |
| `+` | `cancelar()` | void |
| `+` | `cerrarVenta()` | void |
| `+` | `verificarRestriccionEdad(cliente : Cliente)` | Boolean |
| `+` | `verificarLimiteCompra(cliente : Cliente, cantidad : int)` | Boolean |

### TipoEntrada

**Atributos**

| Visibilidad | Nombre | Tipo |
|---|---|---|
| `-` | idTipoEntrada | Long |
| `-` | nombre | String |
| `-` | precio | BigDecimal |
| `-` | cantidadTotal | int |
| `-` | cantidadDisponible | int |

**Operaciones**

| Visibilidad | Firma | Retorno |
|---|---|---|
| `+` | `verificarDisponibilidad(cantidad : int)` | Boolean |
| `+` | `reducirDisponibilidad(cantidad : int)` | void |
| `+` | `liberarDisponibilidad(cantidad : int)` | void |
| `+` | `actualizarPrecio(precio : BigDecimal)` | void |

### Entrada

**Atributos**

| Visibilidad | Nombre | Tipo |
|---|---|---|
| `-` | idEntrada | Long |
| `-` | codigoQR | String |
| `-` | estado | EstadoEntrada |
| `-` | fechaEmision | LocalDateTime |

**Operaciones**

| Visibilidad | Firma | Retorno |
|---|---|---|
| `+` | `generarCodigoQR()` | String |
| `+` | `marcarUtilizada()` | void |
| `+` | `marcarDevuelta()` | void |
| `+` | `reactivar()` | void |
| `+` | `verificarVigencia()` | Boolean |

### Reserva

**Atributos**

| Visibilidad | Nombre | Tipo |
|---|---|---|
| `-` | idReserva | Long |
| `-` | cantidad | int |
| `-` | precioUnitario | BigDecimal |
| `-` | fechaCreacion | LocalDateTime |
| `-` | fechaExpiracion | LocalDateTime |
| `-` | estado | EstadoReserva |

**Operaciones**

| Visibilidad | Firma | Retorno |
|---|---|---|
| `+` | `estaVigente()` | Boolean |
| `+` | `liberar()` | void |
| `+` | `confirmar()` | void |

### Pago

**Atributos**

| Visibilidad | Nombre | Tipo |
|---|---|---|
| `-` | idPago | Long |
| `-` | monto | BigDecimal |
| `-` | codigoQR | String |
| `-` | urlPasarela | String |
| `-` | estado | EstadoPago |
| `-` | fechaPago | LocalDateTime |
| `-` | referenciaExterna | String |
| `-` | requiereDevolucion | Boolean |

**Operaciones**

| Visibilidad | Firma | Retorno |
|---|---|---|
| `+` | `verificarResultado()` | EstadoPago |
| `+` | `registrarPago(referencia : String)` | void |
| `+` | `marcarExpirado()` | void |
| `+` | `registrarError()` | void |
| `+` | `marcarParaDevolucion()` | void |

### Acceso

**Atributos**

| Visibilidad | Nombre | Tipo |
|---|---|---|
| `-` | idAcceso | Long |
| `-` | fechaHora | LocalDateTime |

**Operaciones**

| Visibilidad | Firma | Retorno |
|---|---|---|
| `+` | `registrar()` | void |

### Reporte

**Atributos**

| Visibilidad | Nombre | Tipo |
|---|---|---|
| `-` | idReporte | Long |
| `-` | tipo | TipoReporte |
| `-` | criteriosFiltro | String |
| `-` | fechaGeneracion | LocalDateTime |

**Operaciones**

| Visibilidad | Firma | Retorno |
|---|---|---|
| `+` | `generar(filtros : Filtro)` | void |
| `+` | `exportarPDF()` | Archivo |
| `+` | `exportarExcel()` | Archivo |

## 1.2. Enumeraciones

| Enumeración | Valores |
|---|---|
| EstadoCuenta | ACTIVA, DESACTIVADA |
| EstadoEvento | BORRADOR, ACTIVO, POSTERGADO, CANCELADO |
| EstadoEntrada | ACTIVA, UTILIZADA, DEVUELTA |
| EstadoReserva | VIGENTE, EXPIRADA, CONFIRMADA |
| EstadoPago | PENDIENTE, PAGADO, EXPIRADO, ERROR |
| TipoReporte | VENTAS, INGRESOS, ENTRADAS, CLIENTES, ACCESOS |

## 1.3. Relaciones

### Herencia

| Superclase | Subclase |
|---|---|
| Usuario | Cliente |
| Usuario | Administrador |
| Usuario | PersonalAutorizado |

En código no hay subclases Java: `Usuario` es una sola entidad con un campo
`rol` enumerado. Ver `decisiones.md`.

### Composición

| Todo | Mult. | Parte | Mult. |
|---|---|---|---|
| Evento | 1 | TipoEntrada | 1..* |

### Agregación

| Agregado | Mult. | Elemento | Mult. |
|---|---|---|---|
| TipoEntrada | 1 | Entrada | 0..* |
| Pago | 1 | Entrada | 0..* |

### Asociación

| Clase A | Mult. A | Clase B | Mult. B |
|---|---|---|---|
| Cliente | 1 | Entrada | 0..* |
| Cliente | 1 | Reserva | 0..* |
| TipoEntrada | 1 | Reserva | 0..* |
| Reserva | 1 | Pago | 0..1 |
| Entrada | 1 | Acceso | 0..* |
| PersonalAutorizado | 1 | Acceso | 0..* |
| Administrador | 1 | Reporte | 0..* |
| Administrador | 1 | Evento | 0..* |

Todas las asociaciones se dibujaron sin navegabilidad explícita. La dirección
de navegación se decide al escribir cada entidad.

### Dependencia (uso de enumeraciones)

| Clase | Enumeración |
|---|---|
| Usuario | EstadoCuenta |
| Evento | EstadoEvento |
| Entrada | EstadoEntrada |
| Reserva | EstadoReserva |
| Pago | EstadoPago |
| Reporte | TipoReporte |

---

# 2. Modelo físico de la base de datos (2.11.4) — MySQL

## 2.1. Tabla `usuario`

| Columna | Tipo | Modificadores |
|---|---|---|
| id_usuario | BIGINT | PK, AUTO_INCREMENT |
| nombre | VARCHAR(80) | NOT NULL |
| apellido | VARCHAR(80) | NOT NULL |
| correo | VARCHAR(120) | NOT NULL, UNIQUE |
| contrasena | VARCHAR(255) | NOT NULL |
| telefono | VARCHAR(20) | — |
| rol | ENUM | NOT NULL |
| estado | ENUM | NOT NULL |
| fecha_nacimiento | DATE | — |
| fecha_registro | DATETIME | NOT NULL |

- **PK:** `id_usuario`
- **FK:** ninguna
- **ENUM `rol`:** `'CLIENTE'`, `'ADMINISTRADOR'`, `'PERSONAL'`
- **ENUM `estado`:** `'ACTIVA'`, `'DESACTIVADA'`

Aplana la jerarquía Usuario / Cliente / Administrador / PersonalAutorizado
mediante la columna discriminadora `rol` (estrategia de tabla única).

## 2.2. Tabla `evento`

| Columna | Tipo | Modificadores |
|---|---|---|
| id_evento | BIGINT | PK, AUTO_INCREMENT |
| id_usuario | BIGINT | FK, NOT NULL |
| nombre | VARCHAR(150) | NOT NULL |
| descripcion | TEXT | — |
| fecha | DATE | NOT NULL |
| fecha_original | DATE | NULL |
| hora_inicio | TIME | NOT NULL |
| hora_fin | TIME | NOT NULL |
| lugar | VARCHAR(200) | NOT NULL |
| restriccion_edad | INT | NULL |
| limite_compra | INT | NULL |
| estado | ENUM | NOT NULL |
| venta_abierta | BOOLEAN | NOT NULL |

- **PK:** `id_evento`
- **FK:** `id_usuario` → `usuario(id_usuario)`, corresponde al Administrador que creó el evento
- **ENUM `estado`:** `'BORRADOR'`, `'ACTIVO'`, `'POSTERGADO'`, `'CANCELADO'`

`fecha_original` se completa al postergar el evento por primera vez.

## 2.3. Tabla `tipo_entrada`

| Columna | Tipo | Modificadores |
|---|---|---|
| id_tipo_entrada | BIGINT | PK, AUTO_INCREMENT |
| id_evento | BIGINT | FK, NOT NULL |
| nombre | VARCHAR(80) | NOT NULL |
| precio | DECIMAL(10,2) | NOT NULL |
| cantidad_total | INT | NOT NULL |
| cantidad_disponible | INT | NOT NULL |

- **PK:** `id_tipo_entrada`
- **FK:** `id_evento` → `evento(id_evento)`
- **ENUM:** ninguno

## 2.4. Tabla `reserva`

| Columna | Tipo | Modificadores |
|---|---|---|
| id_reserva | BIGINT | PK, AUTO_INCREMENT |
| id_usuario | BIGINT | FK, NOT NULL |
| id_tipo_entrada | BIGINT | FK, NOT NULL |
| cantidad | INT | NOT NULL |
| precio_unitario | DECIMAL(10,2) | NOT NULL |
| fecha_creacion | DATETIME | NOT NULL |
| fecha_expiracion | DATETIME | NOT NULL |
| estado | ENUM | NOT NULL |

- **PK:** `id_reserva`
- **FK:** `id_usuario` → `usuario(id_usuario)`; `id_tipo_entrada` → `tipo_entrada(id_tipo_entrada)`
- **ENUM `estado`:** `'VIGENTE'`, `'EXPIRADA'`, `'CONFIRMADA'`

`precio_unitario` se copia del tipo de entrada al momento de reservar.

## 2.5. Tabla `pago`

| Columna | Tipo | Modificadores |
|---|---|---|
| id_pago | BIGINT | PK, AUTO_INCREMENT |
| id_reserva | BIGINT | FK, NOT NULL, UNIQUE |
| monto | DECIMAL(10,2) | NOT NULL |
| codigo_qr | VARCHAR(255) | NOT NULL |
| url_pasarela | VARCHAR(255) | NULL |
| estado | ENUM | NOT NULL |
| fecha_pago | DATETIME | — |
| referencia_externa | VARCHAR(120) | — |
| requiere_devolucion | BOOLEAN | NOT NULL |

- **PK:** `id_pago`
- **FK:** `id_reserva` → `reserva(id_reserva)` (UNIQUE: relación 1 a 0..1)
- **ENUM `estado`:** `'PENDIENTE'`, `'PAGADO'`, `'EXPIRADO'`, `'ERROR'`

`referencia_externa` guarda el identificador de la transacción en el sistema de
pago; `codigo_qr`, el QR de cobro; y `url_pasarela`, la página de pago del
sistema externo. `requiere_devolucion` marca un pago tardío que llegó sin cupo:
queda sin entradas y se devuelve fuera del sistema.

## 2.6. Tabla `entrada`

| Columna | Tipo | Modificadores |
|---|---|---|
| id_entrada | BIGINT | PK, AUTO_INCREMENT |
| id_tipo_entrada | BIGINT | FK, NOT NULL |
| id_usuario | BIGINT | FK, NOT NULL |
| id_pago | BIGINT | FK, NOT NULL |
| codigo_qr | VARCHAR(255) | NOT NULL, UNIQUE |
| estado | ENUM | NOT NULL |
| fecha_emision | DATETIME | NOT NULL |

- **PK:** `id_entrada`
- **FK:** `id_tipo_entrada` → `tipo_entrada(id_tipo_entrada)`; `id_usuario` → `usuario(id_usuario)`; `id_pago` → `pago(id_pago)`
- **ENUM `estado`:** `'ACTIVA'`, `'UTILIZADA'`, `'DEVUELTA'`

## 2.7. Tabla `acceso`

| Columna | Tipo | Modificadores |
|---|---|---|
| id_acceso | BIGINT | PK, AUTO_INCREMENT |
| id_entrada | BIGINT | FK, NOT NULL |
| id_usuario | BIGINT | FK, NOT NULL |
| fecha_hora | DATETIME | NOT NULL |

- **PK:** `id_acceso`
- **FK:** `id_entrada` → `entrada(id_entrada)` (relación 1 a 0..*); `id_usuario` → `usuario(id_usuario)`, corresponde al Personal autorizado que valida
- **ENUM:** ninguno

`id_entrada` no es UNIQUE: una entrada reactivada tras una postergación se
valida de nuevo en la fecha nueva y genera un segundo acceso.

## 2.8. Tabla `reporte`

| Columna | Tipo | Modificadores |
|---|---|---|
| id_reporte | BIGINT | PK, AUTO_INCREMENT |
| id_usuario | BIGINT | FK, NOT NULL |
| tipo | ENUM | NOT NULL |
| criterios_filtro | TEXT | — |
| fecha_generacion | DATETIME | NOT NULL |

- **PK:** `id_reporte`
- **FK:** `id_usuario` → `usuario(id_usuario)`, corresponde al Administrador que genera el reporte
- **ENUM `tipo`:** `'VENTAS'`, `'INGRESOS'`, `'ENTRADAS'`, `'CLIENTES'`, `'ACCESOS'`

## 2.9. Tabla `recuperacion_contrasena`

| Columna | Tipo | Modificadores |
|---|---|---|
| id_recuperacion | BIGINT | PK, AUTO_INCREMENT |
| id_usuario | BIGINT | FK, NOT NULL |
| token | VARCHAR(255) | NOT NULL, UNIQUE |
| fecha_creacion | DATETIME | NOT NULL |
| fecha_expiracion | DATETIME | NOT NULL |
| usado | BOOLEAN | NOT NULL |

- **PK:** `id_recuperacion`
- **FK:** `id_usuario` → `usuario(id_usuario)`
- **ENUM:** ninguno

No tiene clase en el diagrama de diseño: soporta el plazo de CU-03.

## 2.10. Resumen de cardinalidades entre tablas

| Origen | Cardinalidad | Destino |
|---|---|---|
| usuario | 1 a 0..* | recuperacion_contrasena |
| usuario | 1 a 0..* | evento |
| evento | 1 a 1..* | tipo_entrada |
| tipo_entrada | 1 a 0..* | entrada |
| tipo_entrada | 1 a 0..* | reserva |
| usuario | 1 a 0..* | reserva |
| usuario | 1 a 0..* | entrada |
| usuario | 1 a 0..* | acceso |
| usuario | 1 a 0..* | reporte |
| reserva | 1 a 0..1 | pago |
| pago | 1 a 0..* | entrada |
| entrada | 1 a 0..* | acceso |

---

# 3. Lo que el modelo no define

Se resuelve al implementar, no se asume a partir de este documento.

- `DatosPersonales`, `Filtro`, `ResultadoValidacion` y `Archivo` aparecen en
  firmas pero no son clases del modelo: son DTOs del paquete `dto/`.
- Valores `DEFAULT` de las columnas.
- Índices, más allá de los implícitos por PK y UNIQUE.
- Comportamiento `ON DELETE` / `ON UPDATE` de las claves foráneas.
- Formato concreto del contenido de `codigo_qr`.
- Formato en que se serializan los criterios dentro de `criterios_filtro`.
