package com.sarao.eventos.service;

import java.util.List;

import com.sarao.eventos.entity.Entrada;
import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.entity.Reserva;
import com.sarao.eventos.entity.TipoEntrada;

// Una compra confirmada del Cliente (CU-08 RF-3): la reserva que se pagó, con sus entradas en el orden en que se
// emitieron. Todo viene cargado (EntradaRepository.delCliente)
public record CompraDelCliente(Reserva reserva, TipoEntrada tipo, Evento evento, List<Entrada> entradas) {

	// Las entradas de una misma reserva
	static CompraDelCliente de(List<Entrada> entradas) {
		Entrada primera = entradas.get(0);
		return new CompraDelCliente(primera.getReserva(), primera.getTipoEntrada(),
				primera.getTipoEntrada().getEvento(), List.copyOf(entradas));
	}
}
