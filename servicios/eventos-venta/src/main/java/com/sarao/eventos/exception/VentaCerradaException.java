package com.sarao.eventos.exception;

import org.springframework.http.HttpStatus;

// CU-06, precondición 2. Distinto de VENTA_YA_CERRADA, que es cerrar dos veces la venta (CU-12)
public class VentaCerradaException extends ErrorNegocioException {

	public VentaCerradaException() {
		super("VENTA_CERRADA", HttpStatus.CONFLICT, "La venta de entradas de ese evento está cerrada.");
	}
}
