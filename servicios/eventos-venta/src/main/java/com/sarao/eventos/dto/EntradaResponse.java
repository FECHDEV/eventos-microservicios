package com.sarao.eventos.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.sarao.eventos.entity.Entrada;
import com.sarao.eventos.entity.EstadoEntrada;
import com.sarao.eventos.entity.EstadoEvento;
import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.service.DetalleEntrada;

// El detalle de una entrada (CU-08 RF-8). codigoQr solo si la entrada puede servir para entrar (RF-9); si no, null y
// la pantalla explica por qué con el estado de la entrada y el del evento
public record EntradaResponse(Long idEntrada, int numero, int cantidad, EstadoEntrada estado,
		LocalDateTime fechaEmision, String codigoQr, Long idEvento, String nombreEvento, LocalDate fecha,
		LocalTime horaInicio, LocalTime horaFin, String lugar, EstadoEvento estadoEvento, String tipoEntrada) {

	public static EntradaResponse from(DetalleEntrada detalle) {
		Entrada entrada = detalle.entrada();
		Evento evento = entrada.getTipoEntrada().getEvento();
		return new EntradaResponse(entrada.getIdEntrada(), detalle.numero(), detalle.cantidad(), entrada.getEstado(),
				entrada.getFechaEmision(), entrada.muestraSuCodigo() ? entrada.getCodigoQrEntrada() : null,
				evento.getIdEvento(), evento.getNombre(), evento.getFecha(), evento.getHoraInicio(),
				evento.getHoraFin(), evento.getLugar(), evento.getEstado(), entrada.getTipoEntrada().getNombre());
	}
}
