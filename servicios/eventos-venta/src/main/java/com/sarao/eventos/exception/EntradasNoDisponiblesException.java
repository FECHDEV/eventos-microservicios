package com.sarao.eventos.exception;

import org.springframework.http.HttpStatus;

// CU-06 FA-01
public class EntradasNoDisponiblesException extends ErrorNegocioException {

	public EntradasNoDisponiblesException() {
		super("ENTRADAS_NO_DISPONIBLES", HttpStatus.CONFLICT,
				"No hay entradas suficientes de ese tipo para la cantidad pedida.");
	}
}
