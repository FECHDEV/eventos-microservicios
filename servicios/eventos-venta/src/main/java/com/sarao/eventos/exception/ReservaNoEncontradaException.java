package com.sarao.eventos.exception;

import org.springframework.http.HttpStatus;

// CU-06 RF-20: la de otro Cliente responde igual que una inexistente
public class ReservaNoEncontradaException extends ErrorNegocioException {

	public ReservaNoEncontradaException() {
		super("RESERVA_NO_ENCONTRADA", HttpStatus.NOT_FOUND, "No existe la reserva indicada.");
	}
}
