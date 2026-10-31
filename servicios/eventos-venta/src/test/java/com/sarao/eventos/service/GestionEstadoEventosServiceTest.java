package com.sarao.eventos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.sarao.eventos.dto.PostergacionRequest;
import com.sarao.eventos.entity.EstadoEvento;
import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.exception.CambioDeEstadoNoPermitidoException;
import com.sarao.eventos.exception.CamposInvalidosException;
import com.sarao.eventos.exception.CamposInvalidosException.CampoInvalido;
import com.sarao.eventos.exception.EventoCanceladoException;
import com.sarao.eventos.exception.EventoNoEncontradoException;
import com.sarao.eventos.exception.EventoSinTiposDeEntradaException;
import com.sarao.eventos.exception.EventoTerminadoException;
import com.sarao.eventos.exception.VentaYaCerradaException;
import com.sarao.eventos.repository.EntradaRepository;
import com.sarao.eventos.repository.EventoRepository;
import com.sarao.eventos.repository.TipoEntradaRepository;

// El evento solo se stubbea por el método con bloqueo: si el servicio lo buscara con findById, estos tests darían 404
// (RF-24)
@ExtendWith(MockitoExtension.class)
class GestionEstadoEventosServiceTest {

	// 18/09/2026 a las 23:30 en La Paz: "hoy" es el 18
	private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-09-19T03:30:00Z"), ZoneId.of("America/La_Paz"));
	private static final LocalDate HOY = LocalDate.of(2026, 9, 18);
	private static final LocalDate FECHA = LocalDate.of(2026, 10, 4);

	@Mock
	private EventoRepository eventoRepository;

	@Mock
	private TipoEntradaRepository tipoEntradaRepository;

	@Mock
	private EntradaRepository entradaRepository;

	private GestionEstadoEventosService servicio;

	private final Long admin = 1L;

	@BeforeEach
	void crearServicio() {
		servicio = new GestionEstadoEventosService(eventoRepository, tipoEntradaRepository, entradaRepository, RELOJ);
	}

	private Evento evento(EstadoEvento estado) {
		Evento evento = Evento.registrar(admin, "Noche de jazz", null, FECHA, LocalTime.of(22, 0), LocalTime.of(3, 0),
				"Teatro Municipal", null, null);
		ReflectionTestUtils.setField(evento, "idEvento", 5L);
		ReflectionTestUtils.setField(evento, "estado", estado);
		ReflectionTestUtils.setField(evento, "ventaAbierta", estado == EstadoEvento.ACTIVO
				|| estado == EstadoEvento.POSTERGADO);
		given(eventoRepository.findConBloqueoByIdEvento(5L)).willReturn(Optional.of(evento));
		return evento;
	}

	private static PostergacionRequest para(LocalDate fecha) {
		return new PostergacionRequest(fecha);
	}

	// --- Activar (RF-5 a RF-8) ---

	@Test
	void activarUnBorradorConTiposLoDejaActivoConLaVentaAbierta() {
		Evento borrador = evento(EstadoEvento.BORRADOR);
		given(tipoEntradaRepository.existsByEvento_IdEvento(5L)).willReturn(true);

		EventoActivado activado = servicio.activar(5L);

		assertThat(activado.evento()).isSameAs(borrador);
		assertThat(borrador.getEstado()).isEqualTo(EstadoEvento.ACTIVO);
		assertThat(borrador.isVentaAbierta()).isTrue();
		assertThat(activado.entradasReactivadas()).isZero();
		// RF-29: desde borrador no hay nada que reactivar
		then(entradaRepository).shouldHaveNoInteractions();
	}

	@Test
	void activarUnBorradorSinTiposEs409() {
		Evento borrador = evento(EstadoEvento.BORRADOR);
		given(tipoEntradaRepository.existsByEvento_IdEvento(5L)).willReturn(false);

		assertThatThrownBy(() -> servicio.activar(5L)).isInstanceOf(EventoSinTiposDeEntradaException.class);
		assertThat(borrador.getEstado()).isEqualTo(EstadoEvento.BORRADOR);
	}

	// RF-7 y FA-02 (RF-26, RF-30, RF-31): un postergado vuelve a ACTIVO sin revisar los tipos y reactiva sus entradas
	// usadas. La reactivación va después de activar: el update en bloque escribe primero la activación
	@Test
	void activarUnPostergadoReactivaSusEntradasUsadas() {
		Evento postergado = evento(EstadoEvento.POSTERGADO);
		given(entradaRepository.reactivarUtilizadasDelEvento(5L)).willAnswer(invocacion -> {
			assertThat(postergado.getEstado()).isEqualTo(EstadoEvento.ACTIVO);
			return 2;
		});

		EventoActivado activado = servicio.activar(5L);

		assertThat(activado.evento()).isSameAs(postergado);
		assertThat(postergado.getEstado()).isEqualTo(EstadoEvento.ACTIVO);
		assertThat(postergado.isVentaAbierta()).isTrue();
		assertThat(activado.entradasReactivadas()).isEqualTo(2);
		then(tipoEntradaRepository).shouldHaveNoInteractions();
	}

	// RF-30: si no se puede activar, no se reactiva nada
	@Test
	void activarUnEventoYaActivoEs409YNoReactivaEntradas() {
		evento(EstadoEvento.ACTIVO);

		assertThatThrownBy(() -> servicio.activar(5L)).isInstanceOf(CambioDeEstadoNoPermitidoException.class);
		then(entradaRepository).shouldHaveNoInteractions();
	}

	// RF-33: con la fecha nueva ya pasada no se activa ni se reactiva nada
	@Test
	void activarUnPostergadoQueYaTerminoEs409YNoReactivaEntradas() {
		Evento postergado = evento(EstadoEvento.POSTERGADO);
		ReflectionTestUtils.setField(postergado, "fecha", LocalDate.of(2026, 9, 1));

		assertThatThrownBy(() -> servicio.activar(5L)).isInstanceOf(EventoTerminadoException.class);

		assertThat(postergado.getEstado()).isEqualTo(EstadoEvento.POSTERGADO);
		then(entradaRepository).shouldHaveNoInteractions();
	}

	@Test
	void activarUnEventoInexistenteEs404() {
		given(eventoRepository.findConBloqueoByIdEvento(9L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.activar(9L)).isInstanceOf(EventoNoEncontradoException.class);
	}

	// --- Postergar (RF-9 a RF-13) ---

	@ParameterizedTest
	@EnumSource(value = EstadoEvento.class, names = { "ACTIVO", "POSTERGADO" })
	void postergarGuardaLaFechaNuevaYLaOriginal(EstadoEvento estado) {
		Evento evento = evento(estado);

		servicio.postergar(5L, para(LocalDate.of(2026, 11, 1)));

		assertThat(evento.getEstado()).isEqualTo(EstadoEvento.POSTERGADO);
		assertThat(evento.getFecha()).isEqualTo(LocalDate.of(2026, 11, 1));
		assertThat(evento.getFechaOriginal()).isEqualTo(FECHA);
		assertThat(evento.isVentaAbierta()).isTrue();
	}

	// RF-11: hoy se acepta; ayer no
	@Test
	void postergarAHoySePuedeYAAyerNo() {
		Evento evento = evento(EstadoEvento.ACTIVO);

		servicio.postergar(5L, para(HOY));

		assertThat(evento.getFecha()).isEqualTo(HOY);
		assertThatThrownBy(() -> servicio.postergar(5L, para(HOY.minusDays(1))))
				.isInstanceOfSatisfying(CamposInvalidosException.class, error -> assertThat(error.getErrores())
						.containsExactly(new CampoInvalido("fecha", "no puede ser anterior a hoy")));
	}

	@Test
	void postergarAUnaFechaQueLaBaseNoGuardaEs400() {
		evento(EstadoEvento.ACTIVO);

		assertThatThrownBy(() -> servicio.postergar(5L, para(LocalDate.of(10000, 1, 1))))
				.isInstanceOfSatisfying(CamposInvalidosException.class, error -> assertThat(error.getErrores())
						.containsExactly(new CampoInvalido("fecha", "no es una fecha válida")));
	}

	// RF-11: postergar a la misma fecha no es postergar
	@Test
	void postergarALaMismaFechaEs400() {
		Evento evento = evento(EstadoEvento.ACTIVO);

		assertThatThrownBy(() -> servicio.postergar(5L, para(FECHA)))
				.isInstanceOfSatisfying(CamposInvalidosException.class, error -> assertThat(error.getErrores())
						.containsExactly(new CampoInvalido("fecha", "debe ser distinta de la fecha actual del evento")));
		assertThat(evento.getFechaOriginal()).isNull();
	}

	@Test
	void postergarUnBorradorEs409() {
		Evento borrador = evento(EstadoEvento.BORRADOR);

		assertThatThrownBy(() -> servicio.postergar(5L, para(LocalDate.of(2026, 11, 1))))
				.isInstanceOf(CambioDeEstadoNoPermitidoException.class);
		assertThat(borrador.getFecha()).isEqualTo(FECHA);
	}

	// --- Cancelar (RF-14 a RF-16) ---

	@ParameterizedTest
	@EnumSource(value = EstadoEvento.class, names = { "BORRADOR", "ACTIVO", "POSTERGADO" })
	void cancelarCierraLaVentaDesdeCualquierEstado(EstadoEvento estado) {
		Evento evento = evento(estado);

		servicio.cancelar(5L);

		assertThat(evento.getEstado()).isEqualTo(EstadoEvento.CANCELADO);
		assertThat(evento.isVentaAbierta()).isFalse();
	}

	// RF-16: cancelado es definitivo para las cuatro operaciones
	@Test
	void unCanceladoRechazaTodasLasOperaciones() {
		evento(EstadoEvento.CANCELADO);

		assertThatThrownBy(() -> servicio.activar(5L)).isInstanceOf(EventoCanceladoException.class);
		assertThatThrownBy(() -> servicio.postergar(5L, para(LocalDate.of(2026, 11, 1))))
				.isInstanceOf(EventoCanceladoException.class);
		assertThatThrownBy(() -> servicio.cancelar(5L)).isInstanceOf(EventoCanceladoException.class);
		assertThatThrownBy(() -> servicio.cerrarVenta(5L)).isInstanceOf(EventoCanceladoException.class);
	}

	// --- Cerrar la venta (RF-17, RF-18) ---

	@Test
	void cerrarLaVentaDejaElEstadoComoEstaba() {
		Evento activo = evento(EstadoEvento.ACTIVO);

		servicio.cerrarVenta(5L);

		assertThat(activo.isVentaAbierta()).isFalse();
		assertThat(activo.getEstado()).isEqualTo(EstadoEvento.ACTIVO);
	}

	@Test
	void cerrarUnaVentaYaCerradaEs409() {
		evento(EstadoEvento.BORRADOR);

		assertThatThrownBy(() -> servicio.cerrarVenta(5L)).isInstanceOf(VentaYaCerradaException.class);
	}

	// --- Cierre automático (RF-20 a RF-23), la parte del servicio ---

	@Test
	void cierraSoloLasVentasQueYaVencieron() {
		Evento vencido = Evento.registrar(admin, "Ya terminó", null, HOY.minusDays(1), LocalTime.of(10, 0),
				LocalTime.of(18, 0), "Fexpocruz", null, null);
		vencido.activar();
		Evento enCurso = Evento.registrar(admin, "Termina de madrugada", null, HOY, LocalTime.of(22, 0),
				LocalTime.of(3, 0), "Teatro Municipal", null, null);
		enCurso.activar();
		ReflectionTestUtils.setField(vencido, "idEvento", 1L);
		ReflectionTestUtils.setField(enCurso, "idEvento", 2L);
		given(eventoRepository.idsConLaVentaAbiertaHasta(HOY)).willReturn(List.of(1L, 2L));
		given(eventoRepository.findConBloqueoByIdEvento(1L)).willReturn(Optional.of(vencido));
		given(eventoRepository.findConBloqueoByIdEvento(2L)).willReturn(Optional.of(enCurso));

		assertThat(servicio.cerrarVentasVencidas()).isEqualTo(1);
		assertThat(vencido.isVentaAbierta()).isFalse();
		assertThat(vencido.getEstado()).isEqualTo(EstadoEvento.ACTIVO);
		assertThat(enCurso.isVentaAbierta()).isTrue();
	}

	// RF-24: entre la consulta y el cierre el administrador puede postergar el evento. Sin releerlo con bloqueo, el
	// cierre escribiría la fila con los valores viejos y la postergación se perdería
	@Test
	void noCierraUnEventoQueSeAcabaDePostergar() {
		Evento postergado = Evento.registrar(admin, "Ya terminó", null, HOY.minusDays(1), LocalTime.of(10, 0),
				LocalTime.of(18, 0), "Fexpocruz", null, null);
		postergado.activar();
		ReflectionTestUtils.setField(postergado, "idEvento", 1L);
		given(eventoRepository.idsConLaVentaAbiertaHasta(HOY)).willReturn(List.of(1L));
		// La fila que devuelve el bloqueo ya tiene la fecha nueva
		postergado.postergar(HOY.plusDays(30));
		given(eventoRepository.findConBloqueoByIdEvento(1L)).willReturn(Optional.of(postergado));

		assertThat(servicio.cerrarVentasVencidas()).isZero();
		assertThat(postergado.isVentaAbierta()).isTrue();
		assertThat(postergado.getFecha()).isEqualTo(HOY.plusDays(30));
	}
}
