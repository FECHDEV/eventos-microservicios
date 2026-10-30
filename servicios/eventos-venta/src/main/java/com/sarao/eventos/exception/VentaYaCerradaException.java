package com.sarao.eventos.exception;

import org.springframework.http.HttpStatus;

public class VentaYaCerradaException extends ErrorNegocioException {

	public VentaYaCerradaException() {
		super("VENTA_YA_CERRADA", HttpStatus.CONFLICT, "La venta de entradas de ese evento ya está cerrada.");
	}
}
