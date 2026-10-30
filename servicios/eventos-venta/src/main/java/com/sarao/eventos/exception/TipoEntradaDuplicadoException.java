package com.sarao.eventos.exception;

import org.springframework.http.HttpStatus;

public class TipoEntradaDuplicadoException extends ErrorNegocioException {

	public TipoEntradaDuplicadoException() {
		super("TIPO_ENTRADA_DUPLICADO", HttpStatus.CONFLICT, "El evento ya tiene un tipo de entrada con ese nombre.");
	}
}
