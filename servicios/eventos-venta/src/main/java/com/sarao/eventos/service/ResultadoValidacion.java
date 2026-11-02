package com.sarao.eventos.service;

// CU-15: lo que la puerta tiene que mostrar. El tipo de entrada solo en una válida; nunca datos del Cliente
public record ResultadoValidacion(Resultado resultado, String tipoEntrada) {

	public enum Resultado {
		VALIDA, NO_CORRESPONDE, NO_VIGENTE, YA_UTILIZADA
	}

	public static ResultadoValidacion valida(String tipoEntrada) {
		return new ResultadoValidacion(Resultado.VALIDA, tipoEntrada);
	}

	public static ResultadoValidacion noCorresponde() {
		return new ResultadoValidacion(Resultado.NO_CORRESPONDE, null);
	}

	public static ResultadoValidacion noVigente() {
		return new ResultadoValidacion(Resultado.NO_VIGENTE, null);
	}

	public static ResultadoValidacion yaUtilizada() {
		return new ResultadoValidacion(Resultado.YA_UTILIZADA, null);
	}
}
