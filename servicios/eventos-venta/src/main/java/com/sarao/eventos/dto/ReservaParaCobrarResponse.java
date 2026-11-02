package com.sarao.eventos.dto;

import java.math.BigDecimal;

import com.sarao.eventos.entity.EstadoReserva;
import com.sarao.eventos.entity.Reserva;
import com.sarao.eventos.service.DetalleReserva;

// Lo que Pagos necesita para cobrar una reserva (CU-07 RF-3 y RF-5). Los segundos y no la hora de vencimiento: así no
// dependen del reloj ni de la zona de cada servicio
public record ReservaParaCobrarResponse(Long idReserva, EstadoReserva estado, long segundosRestantes, BigDecimal total,
		String concepto) {

	public static ReservaParaCobrarResponse from(DetalleReserva detalle) {
		Reserva reserva = detalle.reserva();
		return new ReservaParaCobrarResponse(reserva.getIdReserva(), reserva.estadoAl(detalle.consultadaEn()),
				reserva.segundosRestantes(detalle.consultadaEn()), reserva.total(),
				reserva.getCantidad() + " × " + detalle.tipo().getNombre() + " — " + detalle.evento().getNombre());
	}
}
