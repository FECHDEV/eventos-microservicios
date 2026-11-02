package com.sarao.eventos.exception;

import org.springframework.http.HttpStatus;

// CU-14 FA-02: no se generó ningún reporte, así que no se registra
public class ReporteSinResultadosException extends ErrorNegocioException {

	public ReporteSinResultadosException() {
		super("REPORTE_SIN_RESULTADOS", HttpStatus.NOT_FOUND, "No hay datos que cumplan los criterios indicados.");
	}
}
