package com.sarao.seguridad.service;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.sarao.seguridad.exception.ErrorNegocioException;
import com.sarao.seguridad.exception.SolicitudesExcedidasException;

// Menos margen que el inicio de sesión porque cada solicitud aceptada dispara un correo real
// (decisiones.md → Recuperación de contraseña)
@Component
public class LimiteSolicitudesRecuperacion extends LimiteDeFrecuencia {

	public LimiteSolicitudesRecuperacion(Clock clock) {
		super(clock, 3, Duration.ofMinutes(15), Duration.ofMinutes(15));
	}

	@Override
	protected ErrorNegocioException limiteExcedido() {
		return new SolicitudesExcedidasException();
	}
}
