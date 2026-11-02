package com.sarao.eventos.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.service.EventoCancelado;

// CU-13 RF-2: un evento cancelado y cuántas entradas tiene pendientes de devolución
public record EventoCanceladoResponse(Long idEvento, String nombre, LocalDate fecha, LocalTime horaInicio,
		LocalTime horaFin, String lugar, int pendientes) {

	public static EventoCanceladoResponse from(EventoCancelado cancelado) {
		Evento evento = cancelado.evento();
		return new EventoCanceladoResponse(evento.getIdEvento(), evento.getNombre(), evento.getFecha(),
				evento.getHoraInicio(), evento.getHoraFin(), evento.getLugar(), cancelado.pendientes());
	}
}
