package com.sarao.eventos.exception;

import org.springframework.http.HttpStatus;

// Una llamada interna falló o se pasó de plazo: no se sabe si el Cliente cumple las reglas, así que no se reserva
public class ServicioNoDisponibleException extends ErrorNegocioException {

	public ServicioNoDisponibleException() {
		super("SERVICIO_NO_DISPONIBLE", HttpStatus.SERVICE_UNAVAILABLE,
				"El servicio no está disponible en este momento. Intente de nuevo en unos minutos.");
	}
}
