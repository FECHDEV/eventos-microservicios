package com.sarao.seguridad.dto;

import java.util.Set;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// CU-14: las cuentas de un reporte, en una sola llamada (decisiones.md → Microservicios: datos de otro servicio en lote)
public record LoteDeCuentasRequest(@NotNull @Size(max = 5000) Set<@NotNull Long> ids) {
}
