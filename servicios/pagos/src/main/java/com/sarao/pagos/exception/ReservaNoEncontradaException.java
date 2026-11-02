package com.sarao.pagos.exception;

import org.springframework.http.HttpStatus;

// CU-07 RF-2: la de otro Cliente responde igual que una inexistente
public class ReservaNoEncontradaException extends ErrorNegocioException {

	public ReservaNoEncontradaException() {
		super("RESERVA_NO_ENCONTRADA", HttpStatus.NOT_FOUND, "No existe la reserva indicada.");
	}
}
