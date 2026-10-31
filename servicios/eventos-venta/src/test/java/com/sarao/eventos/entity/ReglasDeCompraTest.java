package com.sarao.eventos.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

// CU-06 pasos 2 a 4: disponibilidad (TipoEntrada), edad y límite (Evento)
class ReglasDeCompraTest {

	private Evento evento(Integer restriccionEdad, Integer limiteCompra) {
		return Evento.registrar(1L, "Noche de jazz", null, LocalDate.of(2026, 10, 4), LocalTime.of(22, 0),
				LocalTime.of(3, 0), "Teatro Municipal", restriccionEdad, limiteCompra);
	}

	// --- Disponibilidad (paso 2) ---

	@Test
	void hayDisponibilidadHastaLaCantidadExacta() {
		TipoEntrada general = TipoEntrada.registrar(evento(null, null), "General", new BigDecimal("50"), 3);

		assertThat(general.verificarDisponibilidad(3)).isTrue();
		assertThat(general.verificarDisponibilidad(4)).isFalse();
	}

	@Test
	void reducirDescuentaDeLaDisponibleSinTocarLaTotal() {
		TipoEntrada general = TipoEntrada.registrar(evento(null, null), "General", new BigDecimal("50"), 3);

		general.reducirDisponibilidad(3);

		assertThat(general.getCantidadDisponible()).isZero();
		assertThat(general.getCantidadTotal()).isEqualTo(3);
		assertThat(general.cantidadComprometida()).isEqualTo(3);
	}

	@Test
	void noSeReduceMasDeLoDisponibleNiUnaCantidadNoPositiva() {
		TipoEntrada general = TipoEntrada.registrar(evento(null, null), "General", new BigDecimal("50"), 3);

		assertThatThrownBy(() -> general.reducirDisponibilidad(4)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> general.reducirDisponibilidad(0)).isInstanceOf(IllegalArgumentException.class);
		assertThat(general.getCantidadDisponible()).isEqualTo(3);
	}

	// --- Edad a la fecha del evento (paso 3; decisiones.md → Restricción de edad contra la fecha del evento) ---

	@Test
	void cumplirLosAnosElMismoDiaDelEventoAlcanza() {
		Evento jazz = evento(18, null);

		assertThat(jazz.verificarRestriccionEdad(LocalDate.of(2008, 10, 4))).isTrue();
		assertThat(jazz.verificarRestriccionEdad(LocalDate.of(2008, 10, 5))).isFalse();
	}

	@Test
	void sinRestriccionCualquierClientePasa() {
		assertThat(evento(null, null).verificarRestriccionEdad(LocalDate.of(2020, 1, 1))).isTrue();
	}

	// Si se posterga, cuenta la fecha nueva: una postergación puede cambiar el resultado
	@Test
	void unPostergadoUsaLaFechaNueva() {
		Evento jazz = evento(18, null);
		jazz.activar();
		jazz.postergar(LocalDate.of(2026, 10, 10));

		assertThat(jazz.verificarRestriccionEdad(LocalDate.of(2008, 10, 8))).isTrue();
	}

	// --- Límite acumulado (paso 4; decisiones.md → Límite de compra acumulado) ---

	@Test
	void elLimiteCuentaLoYaComprometidoMasLoPedido() {
		Evento jazz = evento(null, 4);

		assertThat(jazz.verificarLimiteCompra(3, 1)).isTrue();
		assertThat(jazz.verificarLimiteCompra(3, 2)).isFalse();
		assertThat(jazz.cuantasPuedeAgregar(3)).isEqualTo(1);
		assertThat(jazz.cuantasPuedeAgregar(5)).isZero();
	}

	@Test
	void sinLimiteNoHayTope() {
		assertThat(evento(null, null).verificarLimiteCompra(1_000, 1_000)).isTrue();
	}
}
