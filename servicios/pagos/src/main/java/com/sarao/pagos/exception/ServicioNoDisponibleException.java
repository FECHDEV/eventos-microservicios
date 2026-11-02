package com.sarao.pagos.exception;

import org.springframework.http.HttpStatus;

// Una llamada interna falló o se pasó de plazo: sin saber cómo está la reserva, no se cobra
public class ServicioNoDisponibleException extends ErrorNegocioException {

	public ServicioNoDisponibleException() {
		super("SERVICIO_NO_DISPONIBLE", HttpStatus.SERVICE_UNAVAILABLE,
				"El servicio no está disponible en este momento. Intente de nuevo en unos minutos.");
	}
}
