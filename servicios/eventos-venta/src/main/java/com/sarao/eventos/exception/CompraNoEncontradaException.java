package com.sarao.eventos.exception;

import org.springframework.http.HttpStatus;

// CU-13: una reserva sin entradas no es una compra
public class CompraNoEncontradaException extends ErrorNegocioException {

	public CompraNoEncontradaException() {
		super("COMPRA_NO_ENCONTRADA", HttpStatus.NOT_FOUND, "No existe la compra indicada.");
	}
}
