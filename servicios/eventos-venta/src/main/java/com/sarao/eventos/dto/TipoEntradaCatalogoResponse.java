package com.sarao.eventos.dto;

import java.math.BigDecimal;

import com.sarao.eventos.entity.TipoEntrada;

// Un tipo de entrada en el catálogo: si quedan, no cuántas (decisiones.md → Catálogo de eventos)
public record TipoEntradaCatalogoResponse(Long idTipoEntrada, String nombre, BigDecimal precio, boolean disponible) {

	public static TipoEntradaCatalogoResponse from(TipoEntrada tipo) {
		return new TipoEntradaCatalogoResponse(tipo.getIdTipoEntrada(), tipo.getNombre(), tipo.getPrecio(),
				tipo.getCantidadDisponible() > 0);
	}
}
