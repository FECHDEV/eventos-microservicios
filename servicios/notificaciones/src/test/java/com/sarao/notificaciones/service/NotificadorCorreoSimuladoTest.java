package com.sarao.notificaciones.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.sarao.notificaciones.dto.CorreoDeCompra;

@ExtendWith(OutputCaptureExtension.class)
class NotificadorCorreoSimuladoTest {

	private final NotificadorCorreo notificador = new NotificadorCorreoSimulado();

	@Test
	void registraLaRecuperacionSinElToken(CapturedOutput salida) {
		notificador.enviarRecuperacion("ana@mail.com", "Ana", "token-de-prueba");

		assertThat(salida.getOut()).contains("ana@mail.com").doesNotContain("token-de-prueba");
	}

	@Test
	void registraLaCompraSinLosCodigos(CapturedOutput salida) {
		CorreoDeCompra compra = new CorreoDeCompra("ana@mail.com", "Ana", "Noche de jazz", LocalDate.of(2026, 10, 4),
				LocalTime.of(22, 0), "Teatro Municipal", "General", 2, new BigDecimal("50.00"), new BigDecimal("100.00"));

		notificador.enviarDetalleDeCompra(compra);
		notificador.enviarEntradas(compra, List.of("codigo-a", "codigo-b"));

		assertThat(salida.getOut()).contains("ana@mail.com").contains("Noche de jazz").contains("100.00")
				.doesNotContain("codigo-a").doesNotContain("codigo-b");
	}
}
