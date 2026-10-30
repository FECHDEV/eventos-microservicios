package com.sarao.seguridad.service;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.sarao.seguridad.exception.ErrorNegocioException;
import com.sarao.seguridad.exception.RegistrosExcedidosException;

// Cuentas nuevas por dirección IP (CU-01 RF-17). El correo no cuenta: cada registro usa uno distinto
@Component
public class LimiteRegistros extends LimiteDeFrecuencia {

	private static final String TODOS_LOS_CORREOS = "";

	public LimiteRegistros(Clock clock, @Value("${eventos.registro.maximo-por-hora:20}") int maximoPorHora) {
		super(clock, maximoPorHora, Duration.ofHours(1), Duration.ofHours(1));
	}

	public void registrarIntento(String ip) {
		registrarIntento(TODOS_LOS_CORREOS, ip);
	}

	@Override
	protected ErrorNegocioException limiteExcedido() {
		return new RegistrosExcedidosException();
	}
}
