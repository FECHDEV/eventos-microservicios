package com.sarao.eventos.service;

import java.util.List;

import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.entity.TipoEntrada;

// El detalle de un evento del catálogo (CU-05 RF-14 y RF-15): el evento y sus tipos de entrada
public record EventoDelCatalogo(Evento evento, List<TipoEntrada> tipos) {

	public EventoDelCatalogo {
		tipos = List.copyOf(tipos);
	}
}
