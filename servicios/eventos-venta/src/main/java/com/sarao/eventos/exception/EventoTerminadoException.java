package com.sarao.eventos.exception;

import org.springframework.http.HttpStatus;

// CU-12 RF-33: un postergado cuya fecha ya pasó se vuelve a postergar antes de activarse
public class EventoTerminadoException extends ErrorNegocioException {

	public EventoTerminadoException() {
		super("EVENTO_TERMINADO", HttpStatus.CONFLICT,
				"La fecha de este evento ya pasó. Postérguelo a una fecha futura antes de activarlo.");
	}
}
