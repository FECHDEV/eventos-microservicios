package com.sarao.pagos.service.pago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

// CU-07 RF-22: cobros ficticios que se pueden forzar
class SimuladorDePagoTest {

	private static final ZoneId LA_PAZ = ZoneId.of("America/La_Paz");
	private final Clock reloj = Clock.fixed(Instant.parse("2026-11-02T23:00:00Z"), LA_PAZ);
	private final SimuladorDePago simulador = new SimuladorDePago(reloj);

	@Test
	void generaCobrosDistintosYPendientes() {
		CobroGenerado uno = simulador.generarCobro(40L, new BigDecimal("100.00"), "2 × General");
		CobroGenerado otro = simulador.generarCobro(40L, new BigDecimal("100.00"), "2 × General");

		assertThat(uno.referencia()).isNotEqualTo(otro.referencia());
		assertThat(uno.codigoQr()).isEqualTo("SIMULADOR|" + uno.referencia() + "|100.00");
		assertThat(uno.urlPasarela()).isNull();
		assertThat(simulador.consultar(uno.referencia()).estado()).isEqualTo(EstadoDelCobro.PENDIENTE);
	}

	@Test
	void fuerzaPagoOErrorAunDeUnCobroOlvidado() {
		simulador.forzar("SIM-olvidado", EstadoDelCobro.PAGADO);
		ResultadoDelCobro pagado = simulador.consultar("SIM-olvidado");
		assertThat(pagado.estado()).isEqualTo(EstadoDelCobro.PAGADO);
		assertThat(pagado.fechaPago()).isEqualTo(LocalDateTime.now(reloj));

		simulador.forzar("SIM-otro", EstadoDelCobro.ERROR);
		assertThat(simulador.consultar("SIM-otro").estado()).isEqualTo(EstadoDelCobro.ERROR);
		assertThat(simulador.consultar("SIM-desconocido").estado()).isEqualTo(EstadoDelCobro.PENDIENTE);
		assertThatThrownBy(() -> simulador.forzar("SIM-1", EstadoDelCobro.PENDIENTE))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
