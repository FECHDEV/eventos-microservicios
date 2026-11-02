package com.sarao.pagos.exception;

import org.springframework.http.HttpStatus;

// La reserva todavía no tiene cobro, o es de otro Cliente: la pantalla ofrece «Pagar»
public class PagoNoEncontradoException extends ErrorNegocioException {

	public PagoNoEncontradoException() {
		super("PAGO_NO_ENCONTRADO", HttpStatus.NOT_FOUND, "La reserva todavía no tiene un cobro.");
	}
}
