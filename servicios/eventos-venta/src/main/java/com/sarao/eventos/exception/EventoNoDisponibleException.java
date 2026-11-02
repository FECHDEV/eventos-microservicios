package com.sarao.eventos.exception;

import org.springframework.http.HttpStatus;

// CU-15 RF-4: el evento elegido no está activo o no es hoy
public class EventoNoDisponibleException extends ErrorNegocioException {

	public EventoNoDisponibleException() {
		super("EVENTO_NO_DISPONIBLE", HttpStatus.CONFLICT, "El evento no admite validar entradas ahora.");
	}
}
