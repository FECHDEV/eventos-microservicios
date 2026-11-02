package com.sarao.eventos.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.sarao.eventos.entity.Evento;

// CU-15 RF-2: un evento en el que se puede validar ahora
public record EventoParaValidarResponse(Long idEvento, String nombre, LocalDate fecha, LocalTime horaInicio,
		LocalTime horaFin, String lugar) {

	public static EventoParaValidarResponse from(Evento evento) {
		return new EventoParaValidarResponse(evento.getIdEvento(), evento.getNombre(), evento.getFecha(),
				evento.getHoraInicio(), evento.getHoraFin(), evento.getLugar());
	}
}
