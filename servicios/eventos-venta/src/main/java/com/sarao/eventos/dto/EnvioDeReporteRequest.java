package com.sarao.eventos.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.sarao.eventos.service.reporte.FormatoDeEnvio;
import jakarta.validation.constraints.NotNull;

// CU-14 RF-19 y RF-24. Solo se valida que los campos estén: qué direcciones sirven y si pertenecen al dominio lo sabe
// EnvioDeReporte, que lo reporta bajo este mismo campo para que la pantalla muestre un solo error
@JsonIgnoreProperties(ignoreUnknown = true)
public record EnvioDeReporteRequest(@NotNull(message = "es obligatorio") List<String> destinatarios,
		@NotNull(message = "es obligatorio") FormatoDeEnvio formato) {
}
