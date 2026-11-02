package com.sarao.pagos.exception;

import org.springframework.http.HttpStatus;

// CU-07 RF-6: el sistema de pago no generó el cobro; la reserva se liberó (CU-06 FA-05)
public class ErrorSistemaPagoException extends ErrorNegocioException {

	public ErrorSistemaPagoException() {
		super("ERROR_SISTEMA_PAGO", HttpStatus.BAD_GATEWAY, "No se pudo generar el cobro. Las entradas reservadas se liberaron.");
	}
}
