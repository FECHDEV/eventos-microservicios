package com.sarao.seguridad.exception;

import org.springframework.http.HttpStatus;

public class FechaNacimientoRequeridaException extends ErrorNegocioException {

	public FechaNacimientoRequeridaException() {
		super("FECHA_NACIMIENTO_REQUERIDA", HttpStatus.CONFLICT,
				"La cuenta no tiene fecha de nacimiento, que es obligatoria para el rol Cliente.");
	}
}
