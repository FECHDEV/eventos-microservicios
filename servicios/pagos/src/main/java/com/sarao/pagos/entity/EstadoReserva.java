package com.sarao.pagos.entity;

// Cómo ve el Cliente su reserva desde el pago. La reserva vive en Eventos y Venta: acá se deduce del pago
public enum EstadoReserva {
	VIGENTE, EXPIRADA, CONFIRMADA
}
