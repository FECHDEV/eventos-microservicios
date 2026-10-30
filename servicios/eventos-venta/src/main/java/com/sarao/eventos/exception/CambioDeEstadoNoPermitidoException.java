package com.sarao.eventos.exception;

import org.springframework.http.HttpStatus;

public class CambioDeEstadoNoPermitidoException extends ErrorNegocioException {

	public CambioDeEstadoNoPermitidoException() {
		super("CAMBIO_DE_ESTADO_NO_PERMITIDO", HttpStatus.CONFLICT,
				"Ese cambio no corresponde al estado actual del evento.");
	}
}
