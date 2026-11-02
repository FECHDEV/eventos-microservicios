package com.sarao.eventos.service.reporte;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.sarao.eventos.entity.EstadoEntrada;

class CriteriosDeReporteTest {

	private final CriteriosDeReporte todos = new CriteriosDeReporte(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
			5L, 9L, EstadoEntrada.DEVUELTA, 3L);

	// Se guardan como clave=valor y se leen de vuelta igual (decisiones de implementación)
	@Test
	void seGuardanYSeLeenIgual() {
		String texto = todos.serializar();

		assertThat(texto).isEqualTo(
				"desde=2026-09-01;hasta=2026-09-30;idEvento=5;idTipoEntrada=9;estado=DEVUELTA;idPersonal=3");
		assertThat(CriteriosDeReporte.leer(texto)).isEqualTo(todos);
	}

	// FA-01: sin criterios
	@Test
	void sinCriteriosQuedaVacio() {
		assertThat(CriteriosDeReporte.ninguno().serializar()).isEmpty();
		assertThat(CriteriosDeReporte.leer("")).isEqualTo(CriteriosDeReporte.ninguno());
		assertThat(CriteriosDeReporte.leer(null)).isEqualTo(CriteriosDeReporte.ninguno());
		assertThat(CriteriosDeReporte.ninguno().presentes()).isEmpty();
	}

	@Test
	void sabeQueCriteriosLlegaron() {
		CriteriosDeReporte algunos = new CriteriosDeReporte(null, LocalDate.of(2026, 9, 30), 5L, null, null, null);

		assertThat(algunos.presentes()).containsExactly(Criterio.HASTA, Criterio.EVENTO);
		assertThat(CriteriosDeReporte.leer(algunos.serializar())).isEqualTo(algunos);
	}

	// RF-5: los dos extremos incluidos, por día
	@Test
	void elRangoIncluyeElUltimoDiaEntero() {
		assertThat(todos.inicio()).isEqualTo(LocalDateTime.of(2026, 9, 1, 0, 0));
		assertThat(todos.fin()).isEqualTo(LocalDateTime.of(2026, 10, 1, 0, 0));
		assertThat(CriteriosDeReporte.ninguno().inicio()).isNull();
		assertThat(CriteriosDeReporte.ninguno().fin()).isNull();
	}
}
