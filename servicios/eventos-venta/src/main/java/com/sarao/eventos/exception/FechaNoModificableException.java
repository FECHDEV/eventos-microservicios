package com.sarao.eventos.exception;

import org.springframework.http.HttpStatus;

// decisiones.md → Edición de un evento según su estado
public class FechaNoModificableException extends ErrorNegocioException {

	public FechaNoModificableException() {
		super("FECHA_NO_MODIFICABLE", HttpStatus.CONFLICT,
				"La fecha y el horario de un evento activo o postergado solo cambian al postergarlo.");
	}
}
