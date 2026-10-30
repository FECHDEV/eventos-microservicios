package com.sarao.eventos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
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

import com.sarao.eventos.dto.TipoEntradaRequest;
import com.sarao.eventos.entity.EstadoEvento;
import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.entity.TipoEntrada;
import com.sarao.eventos.exception.CamposInvalidosException;
import com.sarao.eventos.exception.CamposInvalidosException.CampoInvalido;
import com.sarao.eventos.exception.EventoCanceladoException;
import com.sarao.eventos.exception.EventoNoEncontradoException;
import com.sarao.eventos.exception.TipoEntradaDuplicadoException;
import com.sarao.eventos.exception.TipoEntradaNoEncontradoException;
import com.sarao.eventos.repository.EventoRepository;
import com.sarao.eventos.repository.TipoEntradaRepository;

// El evento y el tipo solo se stubbean por los métodos con bloqueo: si el servicio los buscara con findById, estos
// tests darían 404 (RF-21, RF-22)
@ExtendWith(MockitoExtension.class)
class GestionTiposEntradaServiceTest {

	@Mock
	private EventoRepository eventoRepository;

	@Mock
	private TipoEntradaRepository tipoEntradaRepository;

	// 21/09/2026 a las 20:00 en La Paz

	private GestionTiposEntradaService servicio;

	private final Long admin = 1L;

	@BeforeEach
	void crearServicio() {
		servicio = new GestionTiposEntradaService(eventoRepository, tipoEntradaRepository);
	}

	private Evento evento(EstadoEvento estado) {
		Evento evento = Evento.registrar(admin, "Noche de jazz", null, LocalDate.of(2026, 10, 4), LocalTime.of(22, 0),
				LocalTime.of(3, 0), "Teatro Municipal", null, null);
		ReflectionTestUtils.setField(evento, "idEvento", 5L);
		ReflectionTestUtils.setField(evento, "estado", estado);
		given(eventoRepository.findConBloqueoByIdEvento(5L)).willReturn(Optional.of(evento));
		return evento;
	}

	// General a 50 Bs, con 100 en total y la disponible que se indique
	private TipoEntrada general(Evento evento, int disponible) {
		TipoEntrada tipo = TipoEntrada.registrar(evento, "General", new BigDecimal("50"), 100);
		ReflectionTestUtils.setField(tipo, "idTipoEntrada", 3L);
		ReflectionTestUtils.setField(tipo, "cantidadDisponible", disponible);
		given(tipoEntradaRepository.findConBloqueoByIdTipoEntradaAndEvento_IdEvento(3L, 5L))
				.willReturn(Optional.of(tipo));
		return tipo;
	}

	private static TipoEntradaRequest solicitud(String nombre, String precio, int cantidadTotal) {
		return new TipoEntradaRequest(nombre, new BigDecimal(precio), cantidadTotal);
	}

	// --- Consultar (RF-3 a RF-6) ---

	@Test
	void listarDevuelveLosTiposDelEvento() {
		Evento evento = Evento.registrar(admin, "Noche de jazz", null, LocalDate.of(2026, 10, 4), LocalTime.of(22, 0),
				LocalTime.of(3, 0), "Teatro Municipal", null, null);
		List<TipoEntrada> tipos = List.of(TipoEntrada.registrar(evento, "General", new BigDecimal("50"), 100));
		given(eventoRepository.existsById(5L)).willReturn(true);
		given(tipoEntradaRepository.findByEvento_IdEventoOrderByIdTipoEntrada(5L)).willReturn(tipos);

		assertThat(servicio.listar(5L)).isEqualTo(tipos);
	}

	@Test
	void listarDeUnEventoInexistenteEs404() {
		given(eventoRepository.existsById(9L)).willReturn(false);

		assertThatThrownBy(() -> servicio.listar(9L)).isInstanceOf(EventoNoEncontradoException.class);
		then(tipoEntradaRepository).shouldHaveNoInteractions();
	}

	// --- Registrar (RF-7, RF-12, RF-19, RF-20) ---

	@Test
	void registrarAsociaAlEventoConLaDisponibleIgualALaTotal() {
		Evento evento = evento(EstadoEvento.BORRADOR);
		given(tipoEntradaRepository.save(any())).willAnswer(invocacion -> invocacion.getArgument(0));

		TipoEntrada registrado = servicio.registrar(5L, solicitud("  VIP ", "120.5", 40));

		assertThat(registrado.getEvento()).isSameAs(evento);
		assertThat(registrado.getNombre()).isEqualTo("VIP");
		assertThat(registrado.getPrecio()).isEqualTo(new BigDecimal("120.50"));
		assertThat(registrado.getCantidadTotal()).isEqualTo(40);
		assertThat(registrado.getCantidadDisponible()).isEqualTo(40);
		then(tipoEntradaRepository).should().existsByEvento_IdEventoAndNombreIgnoreCase(5L, "VIP");
	}

	@ParameterizedTest
	@EnumSource(value = EstadoEvento.class, names = { "ACTIVO", "POSTERGADO" })
	void registrarEnUnEventoQueYaVendeSePuede(EstadoEvento estado) {
		evento(estado);
		given(tipoEntradaRepository.save(any())).willAnswer(invocacion -> invocacion.getArgument(0));

		assertThat(servicio.registrar(5L, solicitud("VIP", "120", 40)).getNombre()).isEqualTo("VIP");
	}

	@Test
	void registrarEnUnEventoInexistenteEs404() {
		given(eventoRepository.findConBloqueoByIdEvento(9L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.registrar(9L, solicitud("VIP", "120", 40)))
				.isInstanceOf(EventoNoEncontradoException.class);
		then(tipoEntradaRepository).shouldHaveNoInteractions();
	}

	@Test
	void registrarEnUnCanceladoEs409() {
		evento(EstadoEvento.CANCELADO);

		assertThatThrownBy(() -> servicio.registrar(5L, solicitud("VIP", "120", 40)))
				.isInstanceOf(EventoCanceladoException.class);
		then(tipoEntradaRepository).shouldHaveNoInteractions();
	}

	@Test
	void registrarConUnNombreRepetidoEs409() {
		evento(EstadoEvento.ACTIVO);
		given(tipoEntradaRepository.existsByEvento_IdEventoAndNombreIgnoreCase(5L, "vip")).willReturn(true);

		assertThatThrownBy(() -> servicio.registrar(5L, solicitud(" vip ", "120", 40)))
				.isInstanceOf(TipoEntradaDuplicadoException.class);
		then(tipoEntradaRepository).should(never()).save(any());
	}

	// --- Modificar (RF-13 a RF-17, RF-20) ---

	@Test
	void modificarCambiaNombrePrecioYCantidad() {
		TipoEntrada tipo = general(evento(EstadoEvento.ACTIVO), 70);

		TipoEntrada modificado = servicio.modificar(5L, 3L, solicitud("Preferencial", "65", 120));

		assertThat(modificado).isSameAs(tipo);
		assertThat(tipo.getNombre()).isEqualTo("Preferencial");
		assertThat(tipo.getPrecio()).isEqualTo(new BigDecimal("65.00"));
		assertThat(tipo.getCantidadTotal()).isEqualTo(120);
		assertThat(tipo.getCantidadDisponible()).isEqualTo(90);
	}

	@Test
	void modificarNoChocaConSuPropioNombre() {
		TipoEntrada tipo = general(evento(EstadoEvento.BORRADOR), 100);

		servicio.modificar(5L, 3L, solicitud("general", "50", 100));

		assertThat(tipo.getNombre()).isEqualTo("general");
		then(tipoEntradaRepository).should().existsByEvento_IdEventoAndNombreIgnoreCaseAndIdTipoEntradaNot(5L,
				"general", 3L);
	}

	@Test
	void modificarConElNombreDeOtroTipoEs409() {
		TipoEntrada tipo = general(evento(EstadoEvento.BORRADOR), 100);
		given(tipoEntradaRepository.existsByEvento_IdEventoAndNombreIgnoreCaseAndIdTipoEntradaNot(5L, "VIP", 3L))
				.willReturn(true);

		assertThatThrownBy(() -> servicio.modificar(5L, 3L, solicitud("VIP", "50", 100)))
				.isInstanceOf(TipoEntradaDuplicadoException.class);
		assertThat(tipo.getNombre()).isEqualTo("General");
	}

	// RF-17: un tipo de otro evento no se encuentra desde este
	@Test
	void modificarUnTipoQueNoEsDelEventoEs404() {
		evento(EstadoEvento.BORRADOR);
		given(tipoEntradaRepository.findConBloqueoByIdTipoEntradaAndEvento_IdEvento(3L, 5L))
				.willReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.modificar(5L, 3L, solicitud("General", "50", 100)))
				.isInstanceOf(TipoEntradaNoEncontradoException.class);
	}

	// RF-15: 30 vendidas o reservadas; 29 se rechaza con el mínimo y no cambia nada
	@Test
	void modificarPorDebajoDeLoVendidoIndicaElMinimo() {
		TipoEntrada tipo = general(evento(EstadoEvento.ACTIVO), 70);

		assertThatThrownBy(() -> servicio.modificar(5L, 3L, solicitud("General", "80", 29)))
				.isInstanceOfSatisfying(CamposInvalidosException.class, error -> assertThat(error.getErrores())
						.containsExactly(new CampoInvalido("cantidadTotal",
								"no puede ser menor que 30, lo ya vendido y reservado")));
		assertThat(tipo.getCantidadTotal()).isEqualTo(100);
		assertThat(tipo.getPrecio()).isEqualTo(new BigDecimal("50.00"));
	}

	@Test
	void modificarJustoEnElMinimoDejaLaDisponibleEnCero() {
		TipoEntrada tipo = general(evento(EstadoEvento.POSTERGADO), 70);

		servicio.modificar(5L, 3L, solicitud("General", "50", 30));

		assertThat(tipo.getCantidadTotal()).isEqualTo(30);
		assertThat(tipo.getCantidadDisponible()).isZero();
	}

	@Test
	void modificarEnUnCanceladoEs409() {
		evento(EstadoEvento.CANCELADO);

		assertThatThrownBy(() -> servicio.modificar(5L, 3L, solicitud("General", "50", 100)))
				.isInstanceOf(EventoCanceladoException.class);
		then(tipoEntradaRepository).shouldHaveNoInteractions();
	}
}
