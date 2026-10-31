package com.sarao.eventos.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

// CU-06 FA-03: el Cliente vuelve a elegir la cantidad, así que se le dice cuántas puede agregar todavía
public class LimiteCompraExcedidoException extends ErrorNegocioException {

	private final int limite;
	private final int puedeAgregar;

	public LimiteCompraExcedidoException(int limite, int puedeAgregar) {
		super("LIMITE_COMPRA_EXCEDIDO", HttpStatus.CONFLICT, "El límite es de " + limite
				+ " entradas por cliente para este evento; puede agregar " + puedeAgregar + " más.");
		this.limite = limite;
		this.puedeAgregar = puedeAgregar;
	}

	@Override
	public Map<String, Object> propiedades() {
		return Map.of("limite", limite, "puedeAgregar", puedeAgregar);
	}
}
