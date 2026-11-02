package com.sarao.eventos.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.sarao.eventos.entity.EstadoEntrada;
import com.sarao.eventos.entity.TipoReporte;
import com.sarao.eventos.service.reporte.CriteriosDeReporte;
import jakarta.validation.constraints.NotNull;

// CU-14 pasos 1 y 3. Quien genera sale del token. Todos los criterios son opcionales (FA-01); cuáles admite cada tipo
// lo valida ReporteService (RF-4)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReporteRequest(@NotNull(message = "es obligatorio") TipoReporte tipo, LocalDate desde,
		LocalDate hasta, Long idEvento, Long idTipoEntrada, EstadoEntrada estado, Long idPersonal) {

	public CriteriosDeReporte criterios() {
		return new CriteriosDeReporte(desde, hasta, idEvento, idTipoEntrada, estado, idPersonal);
	}
}
