package com.sarao.eventos.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

// CU-06 FA-02. La edad se mide a la fecha del evento (decisiones.md → Restricción de edad contra la fecha del evento)
public class EdadNoPermitidaException extends ErrorNegocioException {

	private final int edadMinima;

	public EdadNoPermitidaException(int edadMinima) {
		super("EDAD_NO_PERMITIDA", HttpStatus.CONFLICT,
				"Este evento es para mayores de " + edadMinima + " años a la fecha en que se realiza.");
		this.edadMinima = edadMinima;
	}

	@Override
	public Map<String, Object> propiedades() {
		return Map.of("edadMinima", edadMinima);
	}
}
