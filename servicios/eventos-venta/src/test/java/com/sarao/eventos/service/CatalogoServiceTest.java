package com.sarao.eventos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import com.sarao.eventos.entity.EstadoEvento;
import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.entity.TipoEntrada;
import com.sarao.eventos.exception.CamposInvalidosException;
import com.sarao.eventos.exception.CamposInvalidosException.CampoInvalido;
import com.sarao.eventos.exception.EventoNoEncontradoException;
import com.sarao.eventos.exception.ImagenNoEncontradaException;
import com.sarao.eventos.repository.EventoRepository;
import com.sarao.eventos.repository.TipoEntradaRepository;

@ExtendWith(MockitoExtension.class)
class CatalogoServiceTest {

	// 18/09/2026 a las 23:30 en La Paz (ya es 19/09 en UTC): "hoy" es el 18
	private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-09-19T03:30:00Z"),
			ZoneId.of("America/La_Paz"));
	private static final LocalDate HOY = LocalDate.of(2026, 9, 18);
	private static final Sort ORDEN = Sort.by(Sort.Direction.ASC, "fecha", "horaInicio", "idEvento");
	private static final EnumSet<EstadoEvento> VISIBLES = EnumSet.of(EstadoEvento.ACTIVO, EstadoEvento.POSTERGADO);

	@Mock
	private EventoRepository eventoRepository;

	@Mock
	private TipoEntradaRepository tipoEntradaRepository;

	@Mock
	private AlmacenImagenes almacenImagenes;

	private CatalogoService servicio;

	@BeforeEach
	void crearServicio() {
		servicio = new CatalogoService(eventoRepository, tipoEntradaRepository, almacenImagenes, RELOJ);
	}

	private static Evento evento(EstadoEvento estado, LocalDate fecha) {
		Evento evento = Evento.registrar(1L, "Noche de jazz", "Cuarteto", fecha, LocalTime.of(22, 0),
				LocalTime.of(3, 0), "Teatro Municipal", 18, 4);
		ReflectionTestUtils.setField(evento, "idEvento", 5L);
		ReflectionTestUtils.setField(evento, "estado", estado);
		return evento;
	}

	private void catalogoVacio() {
		given(eventoRepository.buscarEnElCatalogo(any(), any(), any(), any(), any(), any(), any(), any()))
				.willReturn(Page.empty());
	}

	// --- Buscar (RF-3 a RF-11) ---

	@Test
	void buscaLosVisiblesConElRelojDeLaPaz() {
		catalogoVacio();

		servicio.buscar(null, null, null, 0, 12);

		then(eventoRepository).should().buscarEnElCatalogo(VISIBLES, HOY, HOY.minusDays(1), LocalTime.of(23, 30), null,
				null, null, PageRequest.of(0, 12, ORDEN));
	}

	@Test
	void elTextoSeRecortaPasaAMinusculasYSeEscapa() {
		catalogoVacio();

		servicio.buscar("  100% Jazz_Club!  ", null, null, 0, 12);

		then(eventoRepository).should().buscarEnElCatalogo(VISIBLES, HOY, HOY.minusDays(1), LocalTime.of(23, 30),
				"%100!% jazz!_club!!%", null, null, PageRequest.of(0, 12, ORDEN));
	}

	@Test
	void unTextoVacioNoFiltra() {
		catalogoVacio();

		servicio.buscar("   ", null, null, 0, 12);

		then(eventoRepository).should().buscarEnElCatalogo(VISIBLES, HOY, HOY.minusDays(1), LocalTime.of(23, 30), null,
				null, null, PageRequest.of(0, 12, ORDEN));
	}

	@Test
	void pasaElRangoDeFechas() {
		catalogoVacio();
		LocalDate desde = LocalDate.of(2026, 10, 1);
		LocalDate hasta = LocalDate.of(2026, 10, 31);

		servicio.buscar(null, desde, hasta, 0, 12);

		then(eventoRepository).should().buscarEnElCatalogo(VISIBLES, HOY, HOY.minusDays(1), LocalTime.of(23, 30), null,
				desde, hasta, PageRequest.of(0, 12, ORDEN));
	}

	@Test
	void desdeIgualAHastaEsValido() {
		catalogoVacio();
		LocalDate dia = LocalDate.of(2026, 10, 4);

		servicio.buscar(null, dia, dia, 0, 12);

		then(eventoRepository).should().buscarEnElCatalogo(VISIBLES, HOY, HOY.minusDays(1), LocalTime.of(23, 30), null,
				dia, dia, PageRequest.of(0, 12, ORDEN));
	}

	// RF-10
	@Test
	void rechazaUnRangoInvertido() {
		assertThatThrownBy(() -> servicio.buscar(null, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 4), 0, 12))
				.isInstanceOfSatisfying(CamposInvalidosException.class, error -> assertThat(error.getErrores())
						.containsExactly(new CampoInvalido("hasta", "no puede ser anterior a la fecha desde")));
		then(eventoRepository).shouldHaveNoInteractions();
	}

	@Test
	void rechazaUnTextoDeMasDe200Caracteres() {
		assertThatThrownBy(() -> servicio.buscar("a".repeat(201), null, null, 0, 12))
				.isInstanceOfSatisfying(CamposInvalidosException.class, error -> assertThat(error.getErrores())
						.containsExactly(new CampoInvalido("texto", "admite hasta 200 caracteres")));
	}

	@Test
	void aceptaUnTextoDe200Caracteres() {
		catalogoVacio();

		servicio.buscar("a".repeat(200), null, null, 0, 12);

		then(eventoRepository).should().buscarEnElCatalogo(any(), any(), any(), any(), any(), any(), any(), any());
	}

	// Un año que MySQL no guarda haría fallar la consulta de una ruta pública
	@Test
	void rechazaFechasFueraDeLoQueLaBaseGuarda() {
		assertThatThrownBy(() -> servicio.buscar(null, LocalDate.of(999, 12, 31), LocalDate.of(10000, 1, 1), 0, 12))
				.isInstanceOfSatisfying(CamposInvalidosException.class, error -> assertThat(error.getErrores())
						.containsExactly(new CampoInvalido("desde", "no es una fecha válida"),
								new CampoInvalido("hasta", "no es una fecha válida")));
		then(eventoRepository).shouldHaveNoInteractions();
	}

	@Test
	void unaPaginaEnormeNoDesbordaElOffset() {
		catalogoVacio();

		servicio.buscar(null, null, null, Integer.MAX_VALUE, 12);

		then(eventoRepository).should().buscarEnElCatalogo(VISIBLES, HOY, HOY.minusDays(1), LocalTime.of(23, 30), null,
				null, null, PageRequest.of(Integer.MAX_VALUE / 12, 12, ORDEN));
	}

	// Igual que la lista de CU-10: una página negativa es la primera, un tamaño inválido el por defecto
	@Test
	void corrigeLaPaginaYElTamano() {
		catalogoVacio();

		servicio.buscar(null, null, null, -3, 0);
		servicio.buscar(null, null, null, 2, 500);

		then(eventoRepository).should().buscarEnElCatalogo(VISIBLES, HOY, HOY.minusDays(1), LocalTime.of(23, 30), null,
				null, null, PageRequest.of(0, 12, ORDEN));
		then(eventoRepository).should().buscarEnElCatalogo(VISIBLES, HOY, HOY.minusDays(1), LocalTime.of(23, 30), null,
				null, null, PageRequest.of(2, 48, ORDEN));
	}

	@Test
	void devuelveLaPaginaDelRepositorio() {
		Evento jazz = evento(EstadoEvento.ACTIVO, HOY.plusDays(10));
		given(eventoRepository.buscarEnElCatalogo(any(), any(), any(), any(), any(), any(), any(), any()))
				.willReturn(new PageImpl<>(List.of(jazz)));

		assertThat(servicio.buscar(null, null, null, 0, 12).getContent()).containsExactly(jazz);
	}

	// --- Detalle (RF-14 a RF-16) ---

	@Test
	void elDetalleTraeElEventoYSusTipos() {
		Evento jazz = evento(EstadoEvento.ACTIVO, HOY.plusDays(10));
		TipoEntrada general = TipoEntrada.registrar(jazz, "General", new BigDecimal("50.00"), 100);
		given(eventoRepository.findById(5L)).willReturn(Optional.of(jazz));
		given(tipoEntradaRepository.findByEvento_IdEventoOrderByIdTipoEntrada(5L)).willReturn(List.of(general));

		EventoDelCatalogo detalle = servicio.obtener(5L);

		assertThat(detalle.evento()).isSameAs(jazz);
		assertThat(detalle.tipos()).containsExactly(general);
	}

	@Test
	void unPostergadoTambienSeMuestra() {
		given(eventoRepository.findById(5L)).willReturn(Optional.of(evento(EstadoEvento.POSTERGADO, HOY.plusDays(3))));

		assertThat(servicio.obtener(5L).evento().getEstado()).isEqualTo(EstadoEvento.POSTERGADO);
	}

	@Test
	void unEventoQueNoSeMuestraEsComoSiNoExistiera() {
		given(eventoRepository.findById(5L)).willReturn(Optional.of(evento(EstadoEvento.BORRADOR, HOY.plusDays(10))));
		given(eventoRepository.findById(6L)).willReturn(Optional.of(evento(EstadoEvento.CANCELADO, HOY.plusDays(10))));
		// Del 16/09 de 22:00 al 17/09 a las 03:00: ya terminó
		given(eventoRepository.findById(7L)).willReturn(Optional.of(evento(EstadoEvento.ACTIVO, HOY.minusDays(2))));
		given(eventoRepository.findById(8L)).willReturn(Optional.empty());

		for (long id = 5; id <= 8; id++) {
			long pedido = id;
			assertThatThrownBy(() -> servicio.obtener(pedido)).isInstanceOf(EventoNoEncontradoException.class);
		}
		then(tipoEntradaRepository).shouldHaveNoInteractions();
	}

	// --- Imagen (RF-17) ---

	@Test
	void sirveLaImagenDeUnEventoVisible() {
		Evento jazz = evento(EstadoEvento.ACTIVO, HOY.plusDays(10));
		jazz.cambiarImagen("abc.png");
		byte[] png = { (byte) 0x89, 'P', 'N', 'G' };
		given(eventoRepository.findById(5L)).willReturn(Optional.of(jazz));
		given(almacenImagenes.leer("abc.png")).willReturn(Optional.of(png));

		ImagenEvento imagen = servicio.leerImagen(5L);

		assertThat(imagen.contenido()).isEqualTo(png);
		assertThat(imagen.formato()).isEqualTo(FormatoImagen.PNG);
	}

	@Test
	void noSirveLaImagenDeUnEventoQueNoSeMuestra() {
		Evento borrador = evento(EstadoEvento.BORRADOR, HOY.plusDays(10));
		borrador.cambiarImagen("abc.png");
		given(eventoRepository.findById(5L)).willReturn(Optional.of(borrador));

		assertThatThrownBy(() -> servicio.leerImagen(5L)).isInstanceOf(EventoNoEncontradoException.class);
		then(almacenImagenes).shouldHaveNoInteractions();
	}

	@Test
	void unEventoVisibleSinImagenResponde404() {
		given(eventoRepository.findById(5L)).willReturn(Optional.of(evento(EstadoEvento.ACTIVO, HOY.plusDays(10))));

		assertThatThrownBy(() -> servicio.leerImagen(5L)).isInstanceOf(ImagenNoEncontradaException.class);
	}
}
