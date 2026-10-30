package com.sarao.eventos.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

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
}
