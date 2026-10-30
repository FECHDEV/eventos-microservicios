package com.sarao.eventos.exception;

import org.springframework.http.HttpStatus;

public class EventoSinTiposDeEntradaException extends ErrorNegocioException {

	public EventoSinTiposDeEntradaException() {
		super("EVENTO_SIN_TIPOS_DE_ENTRADA", HttpStatus.CONFLICT,
				"El evento necesita al menos un tipo de entrada para activarse.");
	}
}
