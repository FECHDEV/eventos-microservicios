package com.sarao.eventos.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.sarao.eventos.entity.EstadoReserva;
import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.entity.Reserva;
import com.sarao.eventos.service.DetalleReserva;

// La reserva para el Cliente (CU-06 RF-15 y RF-20). segundosRestantes sale del servidor: la cuenta regresiva de la
// pantalla no depende del reloj ni de la zona del navegador. El estado es el visible: una vigente vencida sale EXPIRADA
public record ReservaResponse(Long idReserva, Long idEvento, String nombreEvento, LocalDate fecha,
		LocalTime horaInicio, LocalTime horaFin, String lugar, Long idTipoEntrada, String tipoEntrada, int cantidad,
		BigDecimal precioUnitario, BigDecimal total, LocalDateTime fechaExpiracion, long segundosRestantes,
		EstadoReserva estado) {

	public static ReservaResponse from(DetalleReserva detalle) {
		Reserva reserva = detalle.reserva();
		Evento evento = detalle.evento();
		LocalDateTime ahora = detalle.consultadaEn();
		return new ReservaResponse(reserva.getIdReserva(), evento.getIdEvento(), evento.getNombre(), evento.getFecha(),
				evento.getHoraInicio(), evento.getHoraFin(), evento.getLugar(), detalle.tipo().getIdTipoEntrada(),
				detalle.tipo().getNombre(), reserva.getCantidad(), reserva.getPrecioUnitario(), reserva.total(),
				reserva.getFechaExpiracion(), reserva.segundosRestantes(ahora), reserva.estadoAl(ahora));
	}
}
