package com.sarao.pagos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.sarao.pagos.entity.Pago;

// CU-14 RF-7 y RF-8: lo que los reportes de Eventos y Venta necesitan de un pago. Sin la referencia ni el QR
public record PagoCobradoResponse(Long idPago, Long idReserva, BigDecimal monto, LocalDateTime fechaPago,
		boolean requiereDevolucion) {

	public static PagoCobradoResponse from(Pago pago) {
		return new PagoCobradoResponse(pago.getIdPago(), pago.getIdReserva(), pago.getMonto(), pago.getFechaPago(),
				pago.isRequiereDevolucion());
	}
}
