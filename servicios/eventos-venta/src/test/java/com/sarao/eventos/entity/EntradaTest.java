package com.sarao.eventos.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EntradaTest {

	private static final LocalDateTime AHORA = LocalDateTime.of(2026, 9, 22, 20, 0);

	private static Reserva reserva() {
		TipoEntrada general = TipoEntrada.registrar(Evento.registrar(1L, "Noche de jazz", null,
				LocalDate.of(2026, 10, 4), LocalTime.of(22, 0), LocalTime.of(3, 0), "Teatro Municipal", null, null),
				"General", new BigDecimal("50"), 100);
		return Reserva.crear(7L, general, 2, AHORA);
	}

	private static Entrada emitida() {
		return Entrada.emitir(reserva(), 30L, "codigo-de-prueba", AHORA);
	}

	@Test
	void seEmiteActivaConElTipoYElClienteDeLaReserva() {
		Reserva reserva = reserva();

		Entrada entrada = Entrada.emitir(reserva, 30L, "codigo-de-prueba", AHORA);

		assertThat(entrada.getReserva()).isSameAs(reserva);
		assertThat(entrada.getTipoEntrada()).isSameAs(reserva.getTipoEntrada());
		assertThat(entrada.getIdCliente()).isEqualTo(7L);
		assertThat(entrada.getIdPago()).isEqualTo(30L);
		assertThat(entrada.getCodigoQrEntrada()).isEqualTo("codigo-de-prueba");
		assertThat(entrada.getEstado()).isEqualTo(EstadoEntrada.ACTIVA);
		assertThat(entrada.getFechaEmision()).isEqualTo(AHORA);
	}

	// CU-08 RF-9: el código se muestra solo si puede servir para entrar: activa y de un evento no cancelado. Uno
	// postergado conserva la validez de sus entradas (CLAUDE.md → Postergación)
	@Test
	void muestraSuCodigoSoloSiPuedeServir() {
		assertThat(emitida().muestraSuCodigo()).isTrue();

		for (EstadoEntrada estado : List.of(EstadoEntrada.UTILIZADA, EstadoEntrada.DEVUELTA)) {
			Entrada usada = emitida();
			ReflectionTestUtils.setField(usada, "estado", estado);
			assertThat(usada.muestraSuCodigo()).as(estado.name()).isFalse();
		}

		Entrada deCancelado = emitida();
		deCancelado.getTipoEntrada().getEvento().cancelar();
		assertThat(deCancelado.muestraSuCodigo()).isFalse();

		Entrada dePostergado = emitida();
		dePostergado.getTipoEntrada().getEvento().postergar(LocalDate.of(2026, 11, 8));
		assertThat(dePostergado.muestraSuCodigo()).isTrue();
	}
}
