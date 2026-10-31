package com.sarao.eventos.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// CU-06 paso 5 y FA-04 (RF-12, RF-16, RF-18)
class ReservaTest {

	private static final LocalDateTime AHORA = LocalDateTime.of(2026, 9, 21, 20, 0);
	private static final Long ANA = 7L;

	private final TipoEntrada general = TipoEntrada.registrar(
			Evento.registrar(1L, "Noche de jazz", null, LocalDate.of(2026, 10, 4), LocalTime.of(22, 0),
					LocalTime.of(3, 0), "Teatro Municipal", null, null),
			"General", new BigDecimal("50"), 100);

	@Test
	void naceVigenteConElPrecioDelTipoYDiezMinutosDePlazo() {
		Reserva reserva = Reserva.crear(ANA, general, 3, AHORA);

		assertThat(reserva.getIdCliente()).isEqualTo(ANA);
		assertThat(reserva.getTipoEntrada()).isSameAs(general);
		assertThat(reserva.getCantidad()).isEqualTo(3);
		assertThat(reserva.getPrecioUnitario()).isEqualTo(new BigDecimal("50.00"));
		assertThat(reserva.getFechaCreacion()).isEqualTo(AHORA);
		assertThat(reserva.getFechaExpiracion()).isEqualTo(AHORA.plusMinutes(10));
		assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.VIGENTE);
		assertThat(reserva.total()).isEqualTo(new BigDecimal("150.00"));
	}

	// RF-16: cambiar el precio del tipo después no mueve el de la reserva
	@Test
	void elPrecioQuedaCongelado() {
		Reserva reserva = Reserva.crear(ANA, general, 1, AHORA);

		general.actualizarPrecio(new BigDecimal("80"));

		assertThat(reserva.getPrecioUnitario()).isEqualTo(new BigDecimal("50.00"));
	}

	@Test
	void unaCantidadNoPositivaNoSeReserva() {
		assertThatThrownBy(() -> Reserva.crear(ANA, general, 0, AHORA)).isInstanceOf(IllegalArgumentException.class);
	}

	// RF-18: vence en el instante exacto de su expiración, aunque nadie la haya marcado
	@Test
	void venceEnElInstanteExactoDeSuExpiracion() {
		Reserva reserva = Reserva.crear(ANA, general, 1, AHORA);

		assertThat(reserva.estaVigente(AHORA.plusMinutes(10).minusNanos(1))).isTrue();
		assertThat(reserva.estaVigente(AHORA.plusMinutes(10))).isFalse();
		assertThat(reserva.estadoAl(AHORA.plusMinutes(10))).isEqualTo(EstadoReserva.EXPIRADA);
		assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.VIGENTE);
	}

	@Test
	void losSegundosRestantesLleganACero() {
		Reserva reserva = Reserva.crear(ANA, general, 1, AHORA);

		assertThat(reserva.segundosRestantes(AHORA)).isEqualTo(600);
		assertThat(reserva.segundosRestantes(AHORA.plusSeconds(599))).isEqualTo(1);
		assertThat(reserva.segundosRestantes(AHORA.plusMinutes(10))).isZero();
		assertThat(reserva.segundosRestantes(AHORA.plusHours(1))).isZero();
	}

	// Una que el barrido ya marcó sigue expirada, y no vuelve a contar como vigente
	@Test
	void unaExpiradaNoEsVigenteNiAntesDeSuPlazo() {
		Reserva reserva = Reserva.crear(ANA, general, 1, AHORA);
		ReflectionTestUtils.setField(reserva, "estado", EstadoReserva.EXPIRADA);

		assertThat(reserva.estaVigente(AHORA)).isFalse();
		assertThat(reserva.estadoAl(AHORA)).isEqualTo(EstadoReserva.EXPIRADA);
		assertThat(reserva.segundosRestantes(AHORA)).isZero();
	}
}
