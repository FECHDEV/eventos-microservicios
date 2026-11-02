package com.sarao.eventos.service.reporte;

import java.util.List;

// Una tabla del reporte (decisiones de implementación de CU-14: una tabla genérica para los cinco). Cada fila tiene un
// valor por columna: String, Long, BigDecimal, LocalDate, LocalDateTime o null (un teléfono vacío, una columna que no
// se suma). Por los null, las filas se arman con Arrays.asList y no con List.of. El detalle no lleva totales (null)
public record Tabla(List<Columna> columnas, List<List<Object>> filas, List<Object> totales) {
}
