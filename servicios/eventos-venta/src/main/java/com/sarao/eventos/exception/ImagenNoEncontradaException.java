package com.sarao.eventos.exception;

import org.springframework.http.HttpStatus;

public class ImagenNoEncontradaException extends ErrorNegocioException {

	public ImagenNoEncontradaException() {
		super("IMAGEN_NO_ENCONTRADA", HttpStatus.NOT_FOUND, "El evento no tiene imagen.");
	}
}
