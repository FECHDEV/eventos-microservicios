package com.sarao.eventos.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotNull;

// La fecha nueva de una postergación (CU-12). Las horas no cambian: postergar solo mueve la fecha
// (decisiones.md → Fecha nueva al postergar)
@JsonIgnoreProperties(ignoreUnknown = true)
public record PostergacionRequest(

		@NotNull(message = "es obligatoria")
		LocalDate fecha) {
}
