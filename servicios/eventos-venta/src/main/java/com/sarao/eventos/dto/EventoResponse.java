package com.sarao.eventos.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.sarao.eventos.entity.EstadoEvento;
import com.sarao.eventos.entity.Evento;

// Detalle de un evento (RF-6). La imagen se pide aparte, en /api/eventos/{id}/imagen
public record EventoResponse(Long idEvento, String nombre, String descripcion, LocalDate fecha,
		LocalDate fechaOriginal, LocalTime horaInicio, LocalTime horaFin, String lugar, Integer restriccionEdad,
		Integer limiteCompra, EstadoEvento estado, boolean ventaAbierta, boolean tieneImagen) {

	public static EventoResponse from(Evento evento) {
		return new EventoResponse(evento.getIdEvento(), evento.getNombre(), evento.getDescripcion(),
				evento.getFecha(), evento.getFechaOriginal(), evento.getHoraInicio(), evento.getHoraFin(),
				evento.getLugar(), evento.getRestriccionEdad(), evento.getLimiteCompra(), evento.getEstado(),
				evento.isVentaAbierta(), evento.tieneImagen());
	}
}
