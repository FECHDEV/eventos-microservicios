package com.sarao.eventos.exception;

import org.springframework.http.HttpStatus;

// CU-08 RF-2: la de otro Cliente responde igual que una inexistente
public class EntradaNoEncontradaException extends ErrorNegocioException {

	public EntradaNoEncontradaException() {
		super("ENTRADA_NO_ENCONTRADA", HttpStatus.NOT_FOUND, "No existe la entrada indicada.");
	}
}
