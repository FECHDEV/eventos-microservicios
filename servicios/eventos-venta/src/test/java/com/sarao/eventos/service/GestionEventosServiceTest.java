package com.sarao.eventos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import com.sarao.eventos.dto.EventoRequest;
import com.sarao.eventos.entity.EstadoEvento;
import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.exception.CamposInvalidosException;
import com.sarao.eventos.exception.CamposInvalidosException.CampoInvalido;
import com.sarao.eventos.exception.EventoCanceladoException;
import com.sarao.eventos.exception.EventoNoEncontradoException;
import com.sarao.eventos.exception.FechaNoModificableException;
import com.sarao.eventos.repository.EventoRepository;

@ExtendWith(MockitoExtension.class)
class GestionEventosServiceTest {

	// 18/09/2026 a las 23:30 en La Paz (ya es 19/09 en UTC): "hoy" es el 18
	static final Clock RELOJ = Clock.fixed(Instant.parse("2026-09-19T03:30:00Z"), ZoneId.of("America/La_Paz"));
	static final LocalDate HOY = LocalDate.of(2026, 9, 18);
	private static final Sort ORDEN = Sort.by(Sort.Direction.ASC, "fecha", "horaInicio", "idEvento");

	@Mock
	private EventoRepository eventoRepository;

	@Mock
	private AlmacenImagenes almacenImagenes;

	private GestionEventosService servicio;

	private final Long admin = 1L;

	@BeforeEach
	void crearServicio() {
		servicio = new GestionEventosService(eventoRepository, almacenImagenes, RELOJ);
	}

	static EventoRequest solicitud(LocalDate fecha, LocalTime inicio, LocalTime fin) {
		return new EventoRequest("Noche de jazz", "Cuarteto", fecha, inicio, fin, "Teatro Municipal", 18, 4);
	}

	static EventoRequest valida() {
		return solicitud(LocalDate.of(2026, 10, 4), LocalTime.of(22, 0), LocalTime.of(3, 0));
	}

	private Evento existente(EstadoEvento estado, LocalDate fecha) {
		Evento evento = Evento.registrar(admin, "Noche de jazz", null, fecha, LocalTime.of(22, 0), LocalTime.of(3, 0),
				"Teatro Municipal", null, null);
		ReflectionTestUtils.setField(evento, "idEvento", 5L);
		ReflectionTestUtils.setField(evento, "estado", estado);
		given(eventoRepository.findConBloqueoByIdEvento(5L)).willReturn(Optional.of(evento));
		return evento;
	}

	// --- Consultar ---

	// RF-4: del más próximo al más lejano, páginas de 20 y como mucho 100
	@Test
	void listarPideLaPaginaOrdenadaPorFechaYHora() {
		given(eventoRepository.buscar(any(), any())).willReturn(new PageImpl<>(List.of()));

		servicio.listar(EstadoEvento.ACTIVO, 2, 500);

		then(eventoRepository).should().buscar(EstadoEvento.ACTIVO, PageRequest.of(2, 100, ORDEN));
	}

	@Test
	void unaPaginaEnormeNoDesbordaElOffset() {
		given(eventoRepository.buscar(any(), any())).willReturn(new PageImpl<>(List.of()));

		servicio.listar(null, Integer.MAX_VALUE, 100);

		then(eventoRepository).should().buscar(null, PageRequest.of(Integer.MAX_VALUE / 100, 100, ORDEN));
	}

	@Test
	void listarConTamanoInvalidoUsaElPorDefecto() {
		given(eventoRepository.buscar(any(), any())).willReturn(new PageImpl<>(List.of()));

		servicio.listar(null, -1, 0);

		then(eventoRepository).should().buscar(null, PageRequest.of(0, 20, ORDEN));
	}

	// RF-6
	@Test
	void obtenerUnEventoInexistenteEsNoEncontrado() {
		given(eventoRepository.findById(9L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.obtener(9L)).isInstanceOf(EventoNoEncontradoException.class);
	}

	// --- Registrar ---

	// RF-7: en borrador, con la venta cerrada y asociado al Administrador de la sesión
	@Test
	void registrarGuardaUnBorradorDelAdministrador() {
		given(eventoRepository.save(any())).willAnswer(invocacion -> invocacion.getArgument(0));

		Evento registrado = servicio.registrar(1L, valida());

		assertThat(registrado.getEstado()).isEqualTo(EstadoEvento.BORRADOR);
		assertThat(registrado.isVentaAbierta()).isFalse();
		assertThat(registrado.getIdCreador()).isEqualTo(admin);
		assertThat(registrado.getRestriccionEdad()).isEqualTo(18);
		assertThat(registrado.getLimiteCompra()).isEqualTo(4);
	}

	// RF-10: hoy se acepta aunque en UTC ya sea mañana; ayer no
	@Test
	void registrarAceptaHoyYRechazaUnaFechaPasada() {
		given(eventoRepository.save(any())).willAnswer(invocacion -> invocacion.getArgument(0));

		assertThat(servicio.registrar(1L, solicitud(HOY, LocalTime.of(10, 0), LocalTime.of(12, 0))).getFecha())
				.isEqualTo(HOY);
		assertThatThrownBy(() -> servicio.registrar(1L, solicitud(HOY.minusDays(1), LocalTime.of(10, 0),
				LocalTime.of(12, 0))))
				.isInstanceOfSatisfying(CamposInvalidosException.class, error -> assertThat(error.getErrores())
						.containsExactly(new CampoInvalido("fecha", "no puede ser anterior a hoy")));
	}

	// Un año que MySQL no guarda sería un 500
	@Test
	void registrarRechazaUnaFechaFueraDeLoQueLaBaseGuarda() {
		assertThatThrownBy(() -> servicio.registrar(1L, solicitud(LocalDate.of(10000, 1, 1), LocalTime.of(10, 0),
				LocalTime.of(12, 0))))
				.isInstanceOfSatisfying(CamposInvalidosException.class, error -> assertThat(error.getErrores())
						.containsExactly(new CampoInvalido("fecha", "no es una fecha válida")));
	}

	// RF-11: horas iguales no; fin menor que inicio sí (cruza la medianoche)
	@Test
	void registrarRechazaHorasIgualesYAceptaCruzarLaMedianoche() {
		given(eventoRepository.save(any())).willAnswer(invocacion -> invocacion.getArgument(0));

		assertThat(servicio.registrar(1L, valida()).getHoraFin()).isEqualTo(LocalTime.of(3, 0));
		assertThatThrownBy(() -> servicio.registrar(1L, solicitud(LocalDate.of(2026, 10, 4), LocalTime.of(20, 0),
				LocalTime.of(20, 0))))
				.isInstanceOfSatisfying(CamposInvalidosException.class, error -> assertThat(error.getErrores())
						.containsExactly(new CampoInvalido("horaFin", "debe ser distinta de la hora de inicio")));
	}

	// FA-01: todos los campos a corregir en una sola respuesta
	@Test
	void registrarInformaFechaYHorasJuntas() {
		assertThatThrownBy(() -> servicio.registrar(1L, solicitud(HOY.minusDays(3), LocalTime.of(8, 0),
				LocalTime.of(8, 0))))
				.isInstanceOfSatisfying(CamposInvalidosException.class,
						error -> assertThat(error.getErrores()).extracting(CampoInvalido::campo)
								.containsExactly("fecha", "horaFin"));
		then(eventoRepository).should(never()).save(any());
	}

	// --- Modificar ---

	// RF-15: en borrador cambia todo, también la fecha y las horas
	@Test
	void modificarUnBorradorCambiaTodo() {
		Evento evento = existente(EstadoEvento.BORRADOR, LocalDate.of(2026, 10, 4));

		servicio.modificar(5L, new EventoRequest("Jazz al aire libre", "Otra", LocalDate.of(2026, 11, 1),
				LocalTime.of(19, 0), LocalTime.of(23, 0), "Parque Urbano", 16, 2));

		assertThat(evento.getNombre()).isEqualTo("Jazz al aire libre");
		assertThat(evento.getFecha()).isEqualTo(LocalDate.of(2026, 11, 1));
		assertThat(evento.getHoraInicio()).isEqualTo(LocalTime.of(19, 0));
		assertThat(evento.getLugar()).isEqualTo("Parque Urbano");
		assertThat(evento.getRestriccionEdad()).isEqualTo(16);
		assertThat(evento.getLimiteCompra()).isEqualTo(2);
	}

	// RF-16: un activo cambia sus datos si la fecha y las horas son las mismas
	@Test
	void modificarUnActivoSinTocarElHorario() {
		Evento evento = existente(EstadoEvento.ACTIVO, LocalDate.of(2026, 10, 4));

		servicio.modificar(5L, new EventoRequest("Noche de jazz", "Nueva descripción", LocalDate.of(2026, 10, 4),
				LocalTime.of(22, 0), LocalTime.of(3, 0), "Teatro Municipal", null, 6));

		assertThat(evento.getDescripcion()).isEqualTo("Nueva descripción");
		assertThat(evento.getLimiteCompra()).isEqualTo(6);
		assertThat(evento.getEstado()).isEqualTo(EstadoEvento.ACTIVO);
	}

	// RF-17: cambiar la fecha o las horas de un activo o postergado es postergarlo (CU-12)
	@Test
	void modificarElHorarioDeUnActivoOPostergadoSeRechaza() {
		Evento activo = existente(EstadoEvento.ACTIVO, LocalDate.of(2026, 10, 4));

		assertThatThrownBy(() -> servicio.modificar(5L, solicitud(LocalDate.of(2026, 10, 5), LocalTime.of(22, 0),
				LocalTime.of(3, 0)))).isInstanceOf(FechaNoModificableException.class);
		ReflectionTestUtils.setField(activo, "estado", EstadoEvento.POSTERGADO);
		assertThatThrownBy(() -> servicio.modificar(5L, solicitud(LocalDate.of(2026, 10, 4), LocalTime.of(21, 0),
				LocalTime.of(3, 0)))).isInstanceOf(FechaNoModificableException.class);
		assertThat(activo.getNombre()).isEqualTo("Noche de jazz");
		assertThat(activo.getFecha()).isEqualTo(LocalDate.of(2026, 10, 4));
	}

	// RF-18
	@Test
	void modificarUnCanceladoSeRechaza() {
		Evento evento = existente(EstadoEvento.CANCELADO, LocalDate.of(2026, 10, 4));

		assertThatThrownBy(() -> servicio.modificar(5L, valida())).isInstanceOf(EventoCanceladoException.class);
		assertThat(evento.getDescripcion()).isNull();
	}

	// RF-19
	@Test
	void modificarUnoInexistenteEsNoEncontrado() {
		given(eventoRepository.findConBloqueoByIdEvento(9L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.modificar(9L, valida())).isInstanceOf(EventoNoEncontradoException.class);
	}

	// RF-21: un borrador cuya fecha ya pasó se puede corregir sin cambiar la fecha
	@Test
	void unaFechaQueNoCambiaNoSeValidaContraHoy() {
		Evento evento = existente(EstadoEvento.BORRADOR, HOY.minusDays(2));

		servicio.modificar(5L, new EventoRequest("Nombre corregido", null, HOY.minusDays(2), LocalTime.of(22, 0),
				LocalTime.of(3, 0), "Teatro Municipal", null, null));

		assertThat(evento.getNombre()).isEqualTo("Nombre corregido");
	}

	@Test
	void unaFechaNuevaEnUnBorradorNoPuedeSerPasada() {
		existente(EstadoEvento.BORRADOR, HOY.plusDays(10));

		assertThatThrownBy(() -> servicio.modificar(5L, solicitud(HOY.minusDays(1), LocalTime.of(22, 0),
				LocalTime.of(3, 0)))).isInstanceOf(CamposInvalidosException.class);
	}

	// Revisión de código: dos modificaciones simultáneas del mismo evento se esperan en vez de pisarse. Sin el
	// bloqueo, la que termina última reescribe todas las columnas, incluida una imagen que la otra ya reemplazó
	@Test
	void modificarBloqueaLaFilaDelEvento() {
		existente(EstadoEvento.BORRADOR, LocalDate.of(2026, 10, 4));

		servicio.modificar(5L, valida());

		then(eventoRepository).should().findConBloqueoByIdEvento(5L);
		then(eventoRepository).should(never()).findById(any());
	}

	// El estado, la venta y el creador no se tocan al modificar (RF-20)
	@Test
	void modificarNoCambiaEstadoVentaNiCreador() {
		Evento evento = existente(EstadoEvento.ACTIVO, LocalDate.of(2026, 10, 4));
		ReflectionTestUtils.setField(evento, "ventaAbierta", true);

		servicio.modificar(5L, valida());

		assertThat(evento.getEstado()).isEqualTo(EstadoEvento.ACTIVO);
		assertThat(evento.isVentaAbierta()).isTrue();
		assertThat(evento.getIdCreador()).isEqualTo(admin);
		then(eventoRepository).should(never()).save(eq(evento));
	}
}
