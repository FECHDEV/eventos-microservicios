package com.sarao.eventos.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import com.sarao.eventos.service.EventoActivado;

// CU-12: el evento, con los mismos campos que las demás operaciones, y cuántas entradas usadas se reactivaron
// (FA-02, RF-31)
public record ActivacionResponse(@JsonUnwrapped EventoResponse evento, int entradasReactivadas) {

	public static ActivacionResponse from(EventoActivado activado) {
		return new ActivacionResponse(EventoResponse.from(activado.evento()), activado.entradasReactivadas());
	}
}
