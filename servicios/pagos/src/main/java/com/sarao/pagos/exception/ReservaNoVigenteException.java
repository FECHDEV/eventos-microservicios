package com.sarao.pagos.exception;

import org.springframework.http.HttpStatus;

// CU-07 RF-5: una reserva vencida o ya confirmada no genera un cobro nuevo
public class ReservaNoVigenteException extends ErrorNegocioException {

	public ReservaNoVigenteException() {
		super("RESERVA_NO_VIGENTE", HttpStatus.CONFLICT, "La reserva ya no está vigente.");
	}
}
