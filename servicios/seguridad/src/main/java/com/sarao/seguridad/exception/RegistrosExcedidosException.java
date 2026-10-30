package com.sarao.seguridad.exception;

import org.springframework.http.HttpStatus;

public class RegistrosExcedidosException extends ErrorNegocioException {

	public RegistrosExcedidosException() {
		super("REGISTROS_EXCEDIDOS", HttpStatus.TOO_MANY_REQUESTS,
				"Demasiados registros desde esta dirección. Espere antes de volver a intentar.");
	}
}
