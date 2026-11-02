package com.sarao.eventos.dto;

import java.time.LocalDate;
import java.util.List;

import com.sarao.eventos.service.reporte.OpcionesDeReporte;
import com.sarao.eventos.service.reporte.Textos;

// CU-14 RF-2: las opciones de los filtros, y el dominio al que se puede mandar un reporte por correo (RF-19)
public record OpcionesDeReporteResponse(List<EventoOpcion> eventos, List<PersonalOpcion> personal,
		String dominioDeCorreo) {

	public record EventoOpcion(Long idEvento, String nombre, LocalDate fecha, List<TipoOpcion> tipos) {
	}

	public record TipoOpcion(Long idTipoEntrada, String nombre) {
	}

	public record PersonalOpcion(Long idUsuario, String nombre) {
	}

	public static OpcionesDeReporteResponse from(OpcionesDeReporte opciones, String dominioDeCorreo) {
		return new OpcionesDeReporteResponse(opciones.eventos().stream()
				.map(conTipos -> new EventoOpcion(conTipos.evento().getIdEvento(), conTipos.evento().getNombre(),
						conTipos.evento().getFecha(), conTipos.tipos().stream()
								.map(tipo -> new TipoOpcion(tipo.getIdTipoEntrada(), tipo.getNombre())).toList()))
				.toList(),
				opciones.personal().stream()
						.map(cuenta -> new PersonalOpcion(cuenta.idUsuario(), Textos.nombre(cuenta))).toList(),
				dominioDeCorreo);
	}
}
