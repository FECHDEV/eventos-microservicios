package com.sarao.eventos.exception;

import org.springframework.http.HttpStatus;

// CU-14 pasos 6 y 7
public class ReporteNoEncontradoException extends ErrorNegocioException {

	public ReporteNoEncontradoException() {
		super("REPORTE_NO_ENCONTRADO", HttpStatus.NOT_FOUND, "No existe el reporte indicado.");
	}
}
