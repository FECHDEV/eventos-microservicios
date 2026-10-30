package com.sarao.eventos.exception;

import org.springframework.http.HttpStatus;

public class EventoNoEncontradoException extends ErrorNegocioException {

	public EventoNoEncontradoException() {
		super("EVENTO_NO_ENCONTRADO", HttpStatus.NOT_FOUND, "No existe el evento indicado.");
	}
}
