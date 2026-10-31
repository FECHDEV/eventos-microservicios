package com.sarao.eventos.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.service.EventoDelCatalogo;

// El detalle de un evento del catálogo (CU-05 RF-14 y RF-15). La imagen se pide aparte, en /api/catalogo/{id}/imagen
public record EventoCatalogoResponse(Long idEvento, String nombre, String descripcion, LocalDate fecha,
		LocalDate fechaOriginal, LocalTime horaInicio, LocalTime horaFin, String lugar, Integer restriccionEdad,
		Integer limiteCompra, boolean ventaAbierta, boolean tieneImagen,
		List<TipoEntradaCatalogoResponse> tiposEntrada) {

	public static EventoCatalogoResponse from(EventoDelCatalogo detalle) {
		Evento evento = detalle.evento();
		return new EventoCatalogoResponse(evento.getIdEvento(), evento.getNombre(), evento.getDescripcion(),
				evento.getFecha(), evento.getFechaOriginal(), evento.getHoraInicio(), evento.getHoraFin(),
				evento.getLugar(), evento.getRestriccionEdad(), evento.getLimiteCompra(), evento.isVentaAbierta(),
				evento.tieneImagen(), detalle.tipos().stream().map(TipoEntradaCatalogoResponse::from).toList());
	}
}
