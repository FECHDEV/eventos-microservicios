package com.sarao.pagos.entity;

// decisiones.md → Estados del pago. No hay «rechazado»: un fallo en el banco del cliente nunca llega al sistema
public enum EstadoPago {
	PENDIENTE, PAGADO, EXPIRADO, ERROR
}
