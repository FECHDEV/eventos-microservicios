package com.sarao.eventos.exception;

import org.springframework.http.HttpStatus;

public class TipoEntradaNoEncontradoException extends ErrorNegocioException {

	public TipoEntradaNoEncontradoException() {
		super("TIPO_ENTRADA_NO_ENCONTRADO", HttpStatus.NOT_FOUND, "No existe el tipo de entrada indicado en ese evento.");
	}
}
