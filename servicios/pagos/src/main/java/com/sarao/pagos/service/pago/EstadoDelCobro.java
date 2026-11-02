package com.sarao.pagos.service.pago;

// Lo que puede informar el sistema de pago. EXPIRADO es nuestro: el vencimiento de 10 minutos lo controla el sistema
// (decisiones.md → El vencimiento de 10 minutos lo controla el sistema)
public enum EstadoDelCobro {
	PENDIENTE, PAGADO, ERROR
}
