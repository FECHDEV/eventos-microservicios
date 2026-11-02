package com.sarao.eventos.dto;

import com.sarao.eventos.service.ResultadoValidacion;

// CU-15 RF-14: el resultado y, si es válida, el tipo de entrada. Sin datos del Cliente
public record ResultadoValidacionResponse(ResultadoValidacion.Resultado resultado, String tipoEntrada) {

	public static ResultadoValidacionResponse from(ResultadoValidacion resultado) {
		return new ResultadoValidacionResponse(resultado.resultado(), resultado.tipoEntrada());
	}
}
