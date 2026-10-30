package com.sarao.seguridad.exception;

import org.springframework.http.HttpStatus;

public class CuentaPropiaNoDesactivableException extends ErrorNegocioException {

	public CuentaPropiaNoDesactivableException() {
		super("CUENTA_PROPIA_NO_DESACTIVABLE", HttpStatus.CONFLICT, "No puede desactivar su propia cuenta.");
	}
}
