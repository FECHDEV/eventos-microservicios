package com.sarao.pagos.dto;

import java.time.LocalDateTime;

import com.sarao.pagos.entity.EstadoPago;
import com.sarao.pagos.entity.EstadoReserva;
import com.sarao.pagos.entity.Pago;

// El cobro de una reserva y cómo va (CU-07 RF-3, RF-7). No expone la referencia del sistema de pago ni su nombre
// (CLAUDE.md → Pago). simulador avisa a la pantalla que puede mostrar sus botones de prueba (RF-27)
public record PagoResponse(Long idReserva, EstadoPago estado, EstadoReserva estadoReserva, String codigoQr,
		String urlPasarela, long segundosRestantes, boolean requiereDevolucion, boolean simulador) {

	public static PagoResponse from(Pago pago, LocalDateTime ahora, boolean simulador) {
		return new PagoResponse(pago.getIdReserva(), pago.estadoAl(ahora), pago.estadoReservaAl(ahora),
				pago.getCodigoQrPago(), pago.getUrlPasarela(), pago.segundosRestantes(ahora),
				pago.isRequiereDevolucion(), simulador);
	}
}
