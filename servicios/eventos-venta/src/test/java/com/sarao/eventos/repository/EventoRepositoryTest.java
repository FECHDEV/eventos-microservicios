package com.sarao.eventos.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import com.sarao.eventos.entity.EstadoEvento;
import com.sarao.eventos.entity.Evento;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EventoRepositoryTest {

	private static final Set<EstadoEvento> VISIBLES = EnumSet.of(EstadoEvento.ACTIVO, EstadoEvento.POSTERGADO);
	private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 4, 20, 0);
	private static final Sort ORDEN = Sort.by(Sort.Direction.ASC, "fecha", "horaInicio", "idEvento");

	@Autowired
	private TestEntityManager em;

	@Autowired
	private EventoRepository eventoRepository;

	private final Long admin = 1L;

	private Evento evento(String nombre, LocalDate fecha, LocalTime inicio) {
		return Evento.registrar(admin, nombre, null, fecha, inicio, inicio.plusHours(2), "Teatro Municipal", null, null);
	}

	@Test
	void guardaYLeeTodasLasColumnas() {
		// La descripción es TEXT: entra una de más de 255 caracteres
		String descripcion = "a".repeat(5000);
		Long id = em.persistAndGetId(Evento.registrar(admin, "Noche de jazz", descripcion, LocalDate.of(2026, 10, 4),
				LocalTime.of(22, 0), LocalTime.of(3, 0), "Teatro Municipal", 18, 4), Long.class);
		em.flush();
		em.clear();

		Evento leido = eventoRepository.findById(id).orElseThrow();

		assertThat(leido.getNombre()).isEqualTo("Noche de jazz");
		assertThat(leido.getDescripcion()).isEqualTo(descripcion);
		assertThat(leido.getFecha()).isEqualTo(LocalDate.of(2026, 10, 4));
		assertThat(leido.getHoraInicio()).isEqualTo(LocalTime.of(22, 0));
		assertThat(leido.getHoraFin()).isEqualTo(LocalTime.of(3, 0));
		assertThat(leido.getLugar()).isEqualTo("Teatro Municipal");
		assertThat(leido.getRestriccionEdad()).isEqualTo(18);
		assertThat(leido.getLimiteCompra()).isEqualTo(4);
		assertThat(leido.getFechaOriginal()).isNull();
		assertThat(leido.getImagen()).isNull();
		assertThat(leido.getIdCreador()).isEqualTo(admin);
	}

	// RF-7: nace en borrador y con la venta cerrada
	@Test
	void unEventoNuevoEstaEnBorradorConLaVentaCerrada() {
		Evento evento = evento("Feria del libro", LocalDate.of(2026, 10, 12), LocalTime.of(10, 0));

		assertThat(evento.getEstado()).isEqualTo(EstadoEvento.BORRADOR);
		assertThat(evento.isVentaAbierta()).isFalse();
	}

	// RF-12: sin restricción y sin límite se guardan vacíos
	@Test
	void sinRestriccionNiLimiteQuedanVacios() {
		Long id = em.persistAndGetId(evento("Feria del libro", LocalDate.of(2026, 10, 12), LocalTime.of(10, 0)),
				Long.class);
		em.flush();
		em.clear();

		Evento leido = eventoRepository.findById(id).orElseThrow();

		assertThat(leido.getRestriccionEdad()).isNull();
		assertThat(leido.getLimiteCompra()).isNull();
	}

	// Una descripción de solo espacios es una descripción vacía
	@Test
	void descripcionEnBlancoSeGuardaVacia() {
		Evento evento = Evento.registrar(admin, "Feria", "   ", LocalDate.of(2026, 10, 12), LocalTime.of(10, 0),
				LocalTime.of(12, 0), "Fexpocruz", null, null);

		assertThat(evento.getDescripcion()).isNull();
	}

	// RF-4 y RF-5: del más próximo al más lejano, y filtro por estado
	@Test
	void buscaPorEstadoOrdenadoPorFechaYHora() {
		Evento tarde = em.persist(evento("Tarde", LocalDate.of(2026, 10, 4), LocalTime.of(18, 0)));
		Evento temprano = em.persist(evento("Temprano", LocalDate.of(2026, 10, 4), LocalTime.of(9, 0)));
		Evento lejano = em.persist(evento("Lejano", LocalDate.of(2026, 12, 1), LocalTime.of(9, 0)));
		ReflectionTestUtils.setField(lejano, "estado", EstadoEvento.ACTIVO);
		em.flush();
		em.clear();

		Page<Evento> todos = eventoRepository.buscar(null, PageRequest.of(0, 20, ORDEN));
		Page<Evento> activos = eventoRepository.buscar(EstadoEvento.ACTIVO, PageRequest.of(0, 20, ORDEN));

		assertThat(todos.getContent()).extracting(Evento::getNombre).containsExactly("Temprano", "Tarde", "Lejano");
		assertThat(activos.getContent()).extracting(Evento::getIdEvento).isEqualTo(List.of(lejano.getIdEvento()));
		assertThat(temprano.getIdEvento()).isNotNull();
		assertThat(tarde.getIdEvento()).isNotNull();
	}

	// Reglas de la entidad que usa el servicio (decisiones.md → Edición según su estado)
	@Test
	void soloEnBorradorSePuedeCambiarElHorario() {
		Evento evento = evento("Feria", LocalDate.of(2026, 10, 12), LocalTime.of(10, 0));
		assertThat(evento.permiteCambiarElHorario()).isTrue();

		ReflectionTestUtils.setField(evento, "estado", EstadoEvento.ACTIVO);
		assertThat(evento.permiteCambiarElHorario()).isFalse();
		ReflectionTestUtils.setField(evento, "estado", EstadoEvento.CANCELADO);
		assertThat(evento.estaCancelado()).isTrue();
	}

	@Test
	void detectaSiCambiaLaFechaOAlgunaHora() {
		Evento evento = evento("Feria", LocalDate.of(2026, 10, 12), LocalTime.of(10, 0));

		assertThat(evento.cambiaElHorario(LocalDate.of(2026, 10, 12), LocalTime.of(10, 0), LocalTime.of(12, 0)))
				.isFalse();
		assertThat(evento.cambiaElHorario(LocalDate.of(2026, 10, 13), LocalTime.of(10, 0), LocalTime.of(12, 0)))
				.isTrue();
		assertThat(evento.cambiaElHorario(LocalDate.of(2026, 10, 12), LocalTime.of(11, 0), LocalTime.of(12, 0)))
				.isTrue();
		assertThat(evento.cambiaElHorario(LocalDate.of(2026, 10, 12), LocalTime.of(10, 0), LocalTime.of(13, 0)))
				.isTrue();
	}

	@Test
	void cambiarYQuitarLaImagenDevuelveLaAnterior() {
		Evento evento = evento("Feria", LocalDate.of(2026, 10, 12), LocalTime.of(10, 0));

		assertThat(evento.cambiarImagen("a.png")).isNull();
		assertThat(evento.cambiarImagen("b.jpg")).isEqualTo("a.png");
		assertThat(evento.tieneImagen()).isTrue();
		assertThat(evento.quitarImagen()).isEqualTo("b.jpg");
		assertThat(evento.tieneImagen()).isFalse();
	}

	// RF-20: candidatos del cierre automático, con la venta abierta y fecha de hoy o anterior
	@Test
	void buscaLosEventosConLaVentaAbiertaHastaUnaFecha() {
		Evento pasado = em.persist(evento("Pasado", LocalDate.of(2026, 10, 4), LocalTime.of(10, 0)));
		pasado.activar();
		Evento futuro = em.persist(evento("Futuro", LocalDate.of(2026, 12, 1), LocalTime.of(10, 0)));
		futuro.activar();
		em.persist(evento("Borrador con la venta cerrada", LocalDate.of(2026, 10, 4), LocalTime.of(10, 0)));
		em.flush();
		em.clear();

		assertThat(eventoRepository.idsConLaVentaAbiertaHasta(LocalDate.of(2026, 10, 4)))
				.containsExactly(pasado.getIdEvento());
		assertThat(eventoRepository.idsConLaVentaAbiertaHasta(LocalDate.of(2026, 12, 1)))
				.containsExactlyInAnyOrder(pasado.getIdEvento(), futuro.getIdEvento());
	}

	// --- Catálogo (CU-05) ---

	private void guardar(String nombre, LocalDate fecha, LocalTime inicio, LocalTime fin, String lugar,
			EstadoEvento estado) {
		Evento evento = Evento.registrar(admin, nombre, null, fecha, inicio, fin, lugar, null, null);
		ReflectionTestUtils.setField(evento, "estado", estado);
		em.persist(evento);
	}

	private void guardarActivo(String nombre, LocalDate fecha, LocalTime inicio, LocalTime fin) {
		guardar(nombre, fecha, inicio, fin, "Teatro Municipal", EstadoEvento.ACTIVO);
	}

	private List<String> catalogo(LocalDateTime ahora, String patron, LocalDate desde, LocalDate hasta) {
		em.flush();
		em.clear();
		return eventoRepository.buscarEnElCatalogo(VISIBLES, ahora.toLocalDate(), ahora.toLocalDate().minusDays(1),
				ahora.toLocalTime(), patron, desde, hasta, PageRequest.of(0, 50, ORDEN)).map(Evento::getNombre)
				.getContent();
	}

	private List<String> catalogo() {
		return catalogo(AHORA, null, null, null);
	}

	@Test
	void elCatalogoTraeSoloActivosYPostergados() {
		LocalDate manana = AHORA.toLocalDate().plusDays(1);
		guardar("Activo", manana, LocalTime.of(10, 0), LocalTime.of(12, 0), "Teatro", EstadoEvento.ACTIVO);
		guardar("Postergado", manana, LocalTime.of(11, 0), LocalTime.of(12, 0), "Teatro", EstadoEvento.POSTERGADO);
		guardar("Borrador", manana, LocalTime.of(12, 0), LocalTime.of(13, 0), "Teatro", EstadoEvento.BORRADOR);
		guardar("Cancelado", manana, LocalTime.of(13, 0), LocalTime.of(14, 0), "Teatro", EstadoEvento.CANCELADO);

		assertThat(catalogo()).containsExactly("Activo", "Postergado");
	}

	// Los mismos bordes que Evento.seMuestraEnElCatalogo, a las 20:00 del 04/10
	@Test
	void elCatalogoNoTraeLosQueYaTerminaron() {
		LocalDate hoy = AHORA.toLocalDate();
		LocalDate ayer = hoy.minusDays(1);
		guardarActivo("Terminó a las 19", hoy, LocalTime.of(17, 0), LocalTime.of(19, 0));
		guardarActivo("Termina justo a las 20", hoy, LocalTime.of(18, 0), LocalTime.of(20, 0));
		guardarActivo("Termina a las 21", hoy, LocalTime.of(19, 0), LocalTime.of(21, 0));
		guardarActivo("Hoy de noche hasta mañana", hoy, LocalTime.of(22, 0), LocalTime.of(3, 0));
		guardarActivo("Ayer de día", ayer, LocalTime.of(10, 0), LocalTime.of(12, 0));
		guardarActivo("Ayer de noche hasta hoy a las 3", ayer, LocalTime.of(22, 0), LocalTime.of(3, 0));
		guardarActivo("Mañana", hoy.plusDays(1), LocalTime.of(10, 0), LocalTime.of(12, 0));

		assertThat(catalogo()).containsExactly("Termina a las 21", "Hoy de noche hasta mañana", "Mañana");
	}

	@Test
	void unEventoDeAyerQueCruzaLaMedianocheSigueHastaQueTermina() {
		LocalDate ayer = AHORA.toLocalDate().minusDays(1);
		guardarActivo("Nocturno", ayer, LocalTime.of(22, 0), LocalTime.of(3, 0));

		assertThat(catalogo(AHORA.toLocalDate().atTime(2, 59), null, null, null)).containsExactly("Nocturno");
		assertThat(catalogo(AHORA.toLocalDate().atTime(3, 0), null, null, null)).isEmpty();
	}

	// El patrón llega en minúsculas y con los comodines del texto escapados con '!' (CatalogoService)
	@Test
	void elTextoSeBuscaEnElNombreYEnElLugarSinDistinguirMayusculas() {
		LocalDate manana = AHORA.toLocalDate().plusDays(1);
		guardar("Noche de JAZZ", manana, LocalTime.of(10, 0), LocalTime.of(12, 0), "Teatro", EstadoEvento.ACTIVO);
		guardar("Feria", manana, LocalTime.of(11, 0), LocalTime.of(12, 0), "Jazz Club", EstadoEvento.ACTIVO);
		guardar("Rock", manana, LocalTime.of(12, 0), LocalTime.of(13, 0), "Estadio", EstadoEvento.ACTIVO);
		guardar("Oferta 100%", manana, LocalTime.of(13, 0), LocalTime.of(14, 0), "Estadio", EstadoEvento.ACTIVO);

		assertThat(catalogo(AHORA, "%jazz%", null, null)).containsExactly("Noche de JAZZ", "Feria");
		assertThat(catalogo(AHORA, "%!%%", null, null)).containsExactly("Oferta 100%");
	}

	@Test
	void elRangoDeFechasIncluyeLosExtremos() {
		LocalDate base = AHORA.toLocalDate().plusDays(10);
		guardarActivo("Antes", base.minusDays(1), LocalTime.of(10, 0), LocalTime.of(12, 0));
		guardarActivo("Desde", base, LocalTime.of(10, 0), LocalTime.of(12, 0));
		guardarActivo("Hasta", base.plusDays(2), LocalTime.of(10, 0), LocalTime.of(12, 0));
		guardarActivo("Después", base.plusDays(3), LocalTime.of(10, 0), LocalTime.of(12, 0));

		assertThat(catalogo(AHORA, null, base, base.plusDays(2))).containsExactly("Desde", "Hasta");
		assertThat(catalogo(AHORA, null, base.plusDays(2), null)).containsExactly("Hasta", "Después");
		assertThat(catalogo(AHORA, null, null, base)).containsExactly("Antes", "Desde");
	}

	// RF-11: un rango hacia atrás no trae terminados
	@Test
	void unRangoConFechasPasadasNoTraeEventosTerminados() {
		LocalDate ayer = AHORA.toLocalDate().minusDays(1);
		guardarActivo("Ayer", ayer, LocalTime.of(10, 0), LocalTime.of(12, 0));

		assertThat(catalogo(AHORA, null, ayer.minusDays(5), ayer)).isEmpty();
	}

	@Test
	void elCatalogoOrdenaPorFechaYHora() {
		LocalDate manana = AHORA.toLocalDate().plusDays(1);
		guardarActivo("Pasado mañana", manana.plusDays(1), LocalTime.of(9, 0), LocalTime.of(10, 0));
		guardarActivo("Mañana tarde", manana, LocalTime.of(18, 0), LocalTime.of(19, 0));
		guardarActivo("Mañana temprano", manana, LocalTime.of(8, 0), LocalTime.of(9, 0));

		assertThat(catalogo()).containsExactly("Mañana temprano", "Mañana tarde", "Pasado mañana");
	}
}
