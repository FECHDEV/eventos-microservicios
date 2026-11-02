package com.sarao.eventos.exception;

import org.springframework.http.HttpStatus;

// CU-13 RF-12: solo se devuelven entradas de un evento cancelado
public class EventoNoCanceladoException extends ErrorNegocioException {

	public EventoNoCanceladoException() {
		super("EVENTO_NO_CANCELADO", HttpStatus.CONFLICT, "El evento no está cancelado.");
	}
}
