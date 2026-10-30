package com.sarao.eventos.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public abstract class ErrorNegocioException extends RuntimeException {

	private final String codigo;
	private final HttpStatus estadoHttp;

	protected ErrorNegocioException(String codigo, HttpStatus estadoHttp, String mensaje) {
		super(mensaje);
		this.codigo = codigo;
		this.estadoHttp = estadoHttp;
	}
}
