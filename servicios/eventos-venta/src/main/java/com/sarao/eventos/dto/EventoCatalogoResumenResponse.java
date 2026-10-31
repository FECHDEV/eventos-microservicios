package com.sarao.eventos.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.sarao.eventos.entity.Evento;

// Una tarjeta del catálogo (CU-05 RF-6). Sin el estado: el cliente ve la fecha original si el evento se postergó
public record EventoCatalogoResumenResponse(Long idEvento, String nombre, LocalDate fecha, LocalDate fechaOriginal,
		LocalTime horaInicio, LocalTime horaFin, String lugar, boolean ventaAbierta, boolean tieneImagen) {

	public static EventoCatalogoResumenResponse from(Evento evento) {
		return new EventoCatalogoResumenResponse(evento.getIdEvento(), evento.getNombre(), evento.getFecha(),
				evento.getFechaOriginal(), evento.getHoraInicio(), evento.getHoraFin(), evento.getLugar(),
				evento.isVentaAbierta(), evento.tieneImagen());
	}
}
