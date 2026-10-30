package com.sarao.eventos.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.sarao.eventos.entity.EstadoEvento;
import com.sarao.eventos.entity.Evento;

// Una fila de la lista de eventos (RF-3)
public record EventoResumenResponse(Long idEvento, String nombre, LocalDate fecha, LocalTime horaInicio,
		LocalTime horaFin, String lugar, EstadoEvento estado, boolean ventaAbierta, boolean tieneImagen) {

	public static EventoResumenResponse from(Evento evento) {
		return new EventoResumenResponse(evento.getIdEvento(), evento.getNombre(), evento.getFecha(),
				evento.getHoraInicio(), evento.getHoraFin(), evento.getLugar(), evento.getEstado(),
				evento.isVentaAbierta(), evento.tieneImagen());
	}
}
