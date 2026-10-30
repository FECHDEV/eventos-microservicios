package com.sarao.eventos.dto;

import java.math.BigDecimal;

import com.sarao.eventos.entity.TipoEntrada;

// Un tipo de entrada con su disponibilidad (RF-3)
public record TipoEntradaResponse(Long idTipoEntrada, String nombre, BigDecimal precio, int cantidadTotal,
		int cantidadDisponible) {

	public static TipoEntradaResponse from(TipoEntrada tipo) {
		return new TipoEntradaResponse(tipo.getIdTipoEntrada(), tipo.getNombre(), tipo.getPrecio(),
				tipo.getCantidadTotal(), tipo.getCantidadDisponible());
	}
}
