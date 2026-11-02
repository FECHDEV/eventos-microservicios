package com.sarao.eventos.exception;

import org.springframework.http.HttpStatus;

// CU-13 RF-6 y RF-12: una entrada utilizada corresponde a alguien que ya asistió
public class EntradaNoDevolvibleException extends ErrorNegocioException {

	public EntradaNoDevolvibleException() {
		super("ENTRADA_NO_DEVOLVIBLE", HttpStatus.CONFLICT, "Una entrada utilizada no se devuelve.");
	}
}
