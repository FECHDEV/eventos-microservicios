package com.sarao.eventos.exception;

import org.springframework.http.HttpStatus;

public class EventoCanceladoException extends ErrorNegocioException {

	public EventoCanceladoException() {
		super("EVENTO_CANCELADO", HttpStatus.CONFLICT, "Un evento cancelado no se puede modificar.");
	}
}
