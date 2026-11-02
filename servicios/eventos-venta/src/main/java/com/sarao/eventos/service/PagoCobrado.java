package com.sarao.eventos.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Un pago de Pagos, como lo necesitan los reportes de ventas e ingresos
public record PagoCobrado(Long idPago, Long idReserva, BigDecimal monto, LocalDateTime fechaPago,
		boolean requiereDevolucion) {
}
