package com.sarao.eventos.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.service.DevolucionesDelEvento;

// CU-13 RF-4 y RF-5: las compras pendientes de devolución y, aparte, las devueltas
public record DevolucionesDelEventoResponse(Long idEvento, String nombre, LocalDate fecha, LocalTime horaInicio,
		LocalTime horaFin, String lugar, List<CompraParaDevolucionResponse> pendientes,
		List<CompraParaDevolucionResponse> devueltas) {

	public static DevolucionesDelEventoResponse from(DevolucionesDelEvento devoluciones) {
		Evento evento = devoluciones.evento();
		return new DevolucionesDelEventoResponse(evento.getIdEvento(), evento.getNombre(), evento.getFecha(),
				evento.getHoraInicio(), evento.getHoraFin(), evento.getLugar(),
				devoluciones.pendientes().stream().map(CompraParaDevolucionResponse::from).toList(),
				devoluciones.devueltas().stream().map(CompraParaDevolucionResponse::from).toList());
	}
}
