package com.sarao.eventos.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.IntStream;

import com.sarao.eventos.entity.Entrada;
import com.sarao.eventos.entity.EstadoEvento;
import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.service.CompraDelCliente;

// Una compra del listado (CU-08 RF-3). La fecha es la vigente: si se postergó, la nueva (RF-6)
public record CompraDelClienteResponse(Long idReserva, Long idEvento, String nombreEvento, LocalDate fecha,
		LocalTime horaInicio, LocalTime horaFin, String lugar, EstadoEvento estadoEvento, String tipoEntrada,
		int cantidad, List<EntradaDeLaCompraResponse> entradas) {

	public static CompraDelClienteResponse from(CompraDelCliente compra) {
		Evento evento = compra.evento();
		List<Entrada> entradas = compra.entradas();
		return new CompraDelClienteResponse(compra.reserva().getIdReserva(), evento.getIdEvento(), evento.getNombre(),
				evento.getFecha(), evento.getHoraInicio(), evento.getHoraFin(), evento.getLugar(), evento.getEstado(),
				compra.tipo().getNombre(), entradas.size(),
				IntStream.range(0, entradas.size())
						.mapToObj(i -> new EntradaDeLaCompraResponse(entradas.get(i).getIdEntrada(), i + 1,
								entradas.get(i).getEstado()))
						.toList());
	}
}
