package com.sarao.eventos.service.reporte;

public record Columna(String titulo, TipoDato tipo) {

	public static Columna texto(String titulo) {
		return new Columna(titulo, TipoDato.TEXTO);
	}

	public static Columna entero(String titulo) {
		return new Columna(titulo, TipoDato.ENTERO);
	}

	public static Columna monto(String titulo) {
		return new Columna(titulo, TipoDato.MONTO);
	}

	public static Columna fecha(String titulo) {
		return new Columna(titulo, TipoDato.FECHA);
	}

	public static Columna fechaHora(String titulo) {
		return new Columna(titulo, TipoDato.FECHA_HORA);
	}
}
