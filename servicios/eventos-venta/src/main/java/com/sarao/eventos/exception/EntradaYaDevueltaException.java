package com.sarao.eventos.exception;

import org.springframework.http.HttpStatus;

// CU-13 FA-02 (RF-11 y RF-13)
public class EntradaYaDevueltaException extends ErrorNegocioException {

	public EntradaYaDevueltaException() {
		super("ENTRADA_YA_DEVUELTA", HttpStatus.CONFLICT, "No quedan entradas pendientes de devolución en lo indicado.");
	}
}
