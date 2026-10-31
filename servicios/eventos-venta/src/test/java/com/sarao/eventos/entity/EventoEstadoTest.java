package com.sarao.eventos.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// Transiciones de CU-12 (decisiones.md → Transiciones de estado del evento)
class EventoEstadoTest {

	private static final LocalDate FECHA = LocalDate.of(2026, 10, 4);

	private static Evento evento(LocalTime inicio, LocalTime fin) {
			return Evento.registrar(1L, "Noche de jazz", null, FECHA, inicio, fin, "Teatro Municipal", null, null);
	}

	private static Evento enEstado(EstadoEvento estado) {
		Evento evento = evento(LocalTime.of(22, 0), LocalTime.of(3, 0));
		ReflectionTestUtils.setField(evento, "estado", estado);
		return evento;
	}

	// RF-5 y RF-7: deja ACTIVO y abre la venta
	@Test
	void activarAbreLaVenta() {
		Evento borrador = enEstado(EstadoEvento.BORRADOR);

		borrador.activar();

		assertThat(borrador.getEstado()).isEqualTo(EstadoEvento.ACTIVO);
		assertThat(borrador.isVentaAbierta()).isTrue();
	}

	// RF-9 y RF-10: la fecha nueva, y la original solo la primera vez
	@Test
	void postergarGuardaLaPrimeraFechaOriginal() {
		Evento activo = enEstado(EstadoEvento.ACTIVO);
		activo.activar();

		activo.postergar(LocalDate.of(2026, 11, 1));

		assertThat(activo.getEstado()).isEqualTo(EstadoEvento.POSTERGADO);
		assertThat(activo.getFecha()).isEqualTo(LocalDate.of(2026, 11, 1));
		assertThat(activo.getFechaOriginal()).isEqualTo(FECHA);
		// RF-12: las horas y la venta no cambian
		assertThat(activo.getHoraInicio()).isEqualTo(LocalTime.of(22, 0));
		assertThat(activo.isVentaAbierta()).isTrue();

		activo.postergar(LocalDate.of(2026, 12, 1));

		assertThat(activo.getFechaOriginal()).isEqualTo(FECHA);
		assertThat(activo.getFecha()).isEqualTo(LocalDate.of(2026, 12, 1));
	}

	// RF-14: cancelar cierra la venta
	@Test
	void cancelarCierraLaVenta() {
		Evento activo = enEstado(EstadoEvento.ACTIVO);
		activo.activar();

		activo.cancelar();

		assertThat(activo.getEstado()).isEqualTo(EstadoEvento.CANCELADO);
		assertThat(activo.isVentaAbierta()).isFalse();
		assertThat(activo.estaCancelado()).isTrue();
	}

	// RF-17: cerrar la venta no toca el estado
	@Test
	void cerrarLaVentaDejaElEstado() {
		Evento activo = enEstado(EstadoEvento.BORRADOR);
		activo.activar();

		activo.cerrarVenta();

		assertThat(activo.isVentaAbierta()).isFalse();
		assertThat(activo.getEstado()).isEqualTo(EstadoEvento.ACTIVO);
	}

	// RF-8 y RF-13: desde qué estados sale cada operación
	@Test
	void desdeQueEstadosSePuedeActivarYPostergar() {
		assertThat(enEstado(EstadoEvento.BORRADOR).puedeActivarse()).isTrue();
		assertThat(enEstado(EstadoEvento.POSTERGADO).puedeActivarse()).isTrue();
		assertThat(enEstado(EstadoEvento.ACTIVO).puedeActivarse()).isFalse();

		assertThat(enEstado(EstadoEvento.ACTIVO).puedePostergarse()).isTrue();
		assertThat(enEstado(EstadoEvento.POSTERGADO).puedePostergarse()).isTrue();
		assertThat(enEstado(EstadoEvento.BORRADOR).puedePostergarse()).isFalse();

		assertThat(enEstado(EstadoEvento.BORRADOR).esBorrador()).isTrue();
		assertThat(enEstado(EstadoEvento.POSTERGADO).esBorrador()).isFalse();
	}

	// RF-21: si la hora de fin es menor que la de inicio, el evento termina al día siguiente
	@Test
	void elFinDeLaVentaCruzaLaMedianoche() {
		Evento nocturno = evento(LocalTime.of(22, 0), LocalTime.of(3, 0));
		Evento diurno = evento(LocalTime.of(10, 0), LocalTime.of(18, 0));

		assertThat(nocturno.finDeLaVenta()).isEqualTo(LocalDateTime.of(2026, 10, 5, 3, 0));
		assertThat(diurno.finDeLaVenta()).isEqualTo(LocalDateTime.of(2026, 10, 4, 18, 0));
	}

	// RF-20: vencida cuando ya se cumplió la hora de fin, no antes
	@Test
	void laVentaVenceAlLlegarLaHoraDeFin() {
		Evento diurno = evento(LocalTime.of(10, 0), LocalTime.of(18, 0));

		assertThat(diurno.termino(LocalDateTime.of(2026, 10, 4, 17, 59))).isFalse();
		assertThat(diurno.termino(LocalDateTime.of(2026, 10, 4, 18, 0))).isTrue();
		assertThat(diurno.termino(LocalDateTime.of(2026, 10, 5, 0, 1))).isTrue();
	}

	// CU-08 RF-5: terminó al llegar su hora de fin, al día siguiente si cruza la medianoche
	@Test
	void terminaAlLlegarLaHoraDeFin() {
		Evento nocturno = evento(LocalTime.of(22, 0), LocalTime.of(3, 0));

		assertThat(nocturno.termino(LocalDateTime.of(2026, 10, 4, 23, 0))).isFalse();
		assertThat(nocturno.termino(LocalDateTime.of(2026, 10, 5, 2, 59))).isFalse();
		assertThat(nocturno.termino(LocalDateTime.of(2026, 10, 5, 3, 0))).isTrue();
	}

	// --- Catálogo (CU-05 RF-3 y RF-4) ---

	private static Evento visibleDe(LocalTime inicio, LocalTime fin) {
		Evento evento = evento(inicio, fin);
		ReflectionTestUtils.setField(evento, "estado", EstadoEvento.ACTIVO);
		return evento;
	}

	// --- Validación de entradas (CU-15) ---

	// --- Catálogo (CU-05 RF-3 y RF-4) ---

	@Test
	void seMuestranLosActivosYLosPostergados() {
		LocalDateTime antes = FECHA.minusDays(1).atTime(12, 0);

		assertThat(enEstado(EstadoEvento.ACTIVO).seMuestraEnElCatalogo(antes)).isTrue();
		assertThat(enEstado(EstadoEvento.POSTERGADO).seMuestraEnElCatalogo(antes)).isTrue();
		assertThat(enEstado(EstadoEvento.BORRADOR).seMuestraEnElCatalogo(antes)).isFalse();
		assertThat(enEstado(EstadoEvento.CANCELADO).seMuestraEnElCatalogo(antes)).isFalse();
	}

	// Se muestra hasta el instante en que termina, no incluido: la misma cuenta que termino
	@Test
	void dejaDeMostrarseAlTerminar() {
		Evento deTarde = visibleDe(LocalTime.of(18, 0), LocalTime.of(20, 0));

		assertThat(deTarde.seMuestraEnElCatalogo(FECHA.atTime(19, 59))).isTrue();
		assertThat(deTarde.seMuestraEnElCatalogo(FECHA.atTime(20, 0))).isFalse();
	}

	// De 22:00 a 03:00 termina al día siguiente (decisiones.md → Fecha y horario)
	@Test
	void unEventoQueCruzaLaMedianocheSeMuestraHastaElDiaSiguiente() {
		Evento nocturno = visibleDe(LocalTime.of(22, 0), LocalTime.of(3, 0));

		assertThat(nocturno.seMuestraEnElCatalogo(FECHA.plusDays(1).atTime(2, 59))).isTrue();
		assertThat(nocturno.seMuestraEnElCatalogo(FECHA.plusDays(1).atTime(3, 0))).isFalse();
	}
}
