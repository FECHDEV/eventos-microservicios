package com.sarao.eventos.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.entity.TipoEntrada;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TipoEntradaRepositoryTest {

	@Autowired
	private TestEntityManager em;

	@Autowired
	private TipoEntradaRepository tipoEntradaRepository;

	private Evento jazz;
	private Evento feria;

	@BeforeEach
	void crearEventos() {
		jazz = em.persist(evento("Noche de jazz"));
		feria = em.persist(evento("Feria del libro"));
	}

	private static Evento evento(String nombre) {
		return Evento.registrar(1L, nombre, null, LocalDate.of(2026, 10, 4), LocalTime.of(20, 0),
				LocalTime.of(23, 0), "Teatro Municipal", null, null);
	}

	private TipoEntrada guardado(Evento evento, String nombre) {
		return em.persist(TipoEntrada.registrar(evento, nombre, new BigDecimal("50"), 100));
	}

	// RF-7: la disponible nace igual a la total; el nombre sin espacios en los extremos y el precio con 2 decimales
	@Test
	void guardaYLeeTodasLasColumnas() {
		Long id = em.persistAndGetId(TipoEntrada.registrar(jazz, "  General ", new BigDecimal("10.5"), 100),
				Long.class);
		em.flush();
		em.clear();

		TipoEntrada leido = tipoEntradaRepository.findById(id).orElseThrow();

		assertThat(leido.getNombre()).isEqualTo("General");
		assertThat(leido.getPrecio()).isEqualTo(new BigDecimal("10.50"));
		assertThat(leido.getCantidadTotal()).isEqualTo(100);
		assertThat(leido.getCantidadDisponible()).isEqualTo(100);
		assertThat(leido.getEvento().getIdEvento()).isEqualTo(jazz.getIdEvento());
	}

	// RF-3: solo los del evento, en el orden en que se registraron
	@Test
	void listaLosDelEventoEnOrdenDeRegistro() {
		guardado(jazz, "VIP");
		guardado(feria, "Estudiante");
		guardado(jazz, "General");
		em.flush();
		em.clear();

		assertThat(tipoEntradaRepository.findByEvento_IdEventoOrderByIdTipoEntrada(jazz.getIdEvento()))
				.extracting(TipoEntrada::getNombre).containsExactly("VIP", "General");
	}

	// RF-12: el mismo nombre choca dentro del evento, sin distinguir mayúsculas; en otro evento, no
	@Test
	void detectaUnNombreRepetidoSinDistinguirMayusculas() {
		guardado(jazz, "VIP");
		em.flush();

		assertThat(tipoEntradaRepository.existsByEvento_IdEventoAndNombreIgnoreCase(jazz.getIdEvento(), "vip"))
				.isTrue();
		assertThat(tipoEntradaRepository.existsByEvento_IdEventoAndNombreIgnoreCase(feria.getIdEvento(), "vip"))
				.isFalse();
		assertThat(tipoEntradaRepository.existsByEvento_IdEventoAndNombreIgnoreCase(jazz.getIdEvento(), "General"))
				.isFalse();
	}

	// RF-13: un tipo no choca con su propio nombre, pero sí con el de otro del mismo evento
	@Test
	void unTipoNoChocaConSuPropioNombre() {
		TipoEntrada vip = guardado(jazz, "VIP");
		TipoEntrada general = guardado(jazz, "General");
		em.flush();

		assertThat(tipoEntradaRepository.existsByEvento_IdEventoAndNombreIgnoreCaseAndIdTipoEntradaNot(
				jazz.getIdEvento(), "vip", vip.getIdTipoEntrada())).isFalse();
		assertThat(tipoEntradaRepository.existsByEvento_IdEventoAndNombreIgnoreCaseAndIdTipoEntradaNot(
				jazz.getIdEvento(), "vip", general.getIdTipoEntrada())).isTrue();
	}

	// RF-17 y RF-21: la búsqueda para modificar bloquea la fila y no encuentra el tipo desde otro evento
	@Test
	void buscaConBloqueoSoloDentroDeSuEvento() {
		TipoEntrada vip = guardado(jazz, "VIP");
		em.flush();

		assertThat(tipoEntradaRepository.findConBloqueoByIdTipoEntradaAndEvento_IdEvento(vip.getIdTipoEntrada(),
				jazz.getIdEvento())).contains(vip);
		assertThat(tipoEntradaRepository.findConBloqueoByIdTipoEntradaAndEvento_IdEvento(vip.getIdTipoEntrada(),
				feria.getIdEvento())).isEmpty();
	}

	// RF-14 y RF-15: la disponible se mueve con la total y la total no baja de lo comprometido
	@Test
	void cambiarLaCantidadTotalMueveLaDisponible() {
		TipoEntrada tipo = TipoEntrada.registrar(jazz, "General", new BigDecimal("50"), 100);
		ReflectionTestUtils.setField(tipo, "cantidadDisponible", 70);
		assertThat(tipo.cantidadComprometida()).isEqualTo(30);

		tipo.actualizar("General", new BigDecimal("50"), 120);
		assertThat(tipo.getCantidadDisponible()).isEqualTo(90);

		tipo.actualizar("General", new BigDecimal("50"), 30);
		assertThat(tipo.getCantidadDisponible()).isZero();

		assertThatThrownBy(() -> tipo.actualizar("General", new BigDecimal("50"), 29))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(tipo.getCantidadTotal()).isEqualTo(30);
	}

	// RF-6: la activación necesita saber si el evento tiene algún tipo de entrada
	@Test
	void sabeSiUnEventoTieneAlgunTipo() {
		guardado(jazz, "VIP");
		em.flush();

		assertThat(tipoEntradaRepository.existsByEvento_IdEvento(jazz.getIdEvento())).isTrue();
		assertThat(tipoEntradaRepository.existsByEvento_IdEvento(feria.getIdEvento())).isFalse();
	}

	// CU-06 FA-04: la disponible se suma en la base, sobre el valor actual
	@Test
	void devolverDisponibilidadSumaALaDisponible() {
		TipoEntrada general = guardado(jazz, "General");
		ReflectionTestUtils.setField(general, "cantidadDisponible", 90);
		em.flush();

		assertThat(tipoEntradaRepository.devolverDisponibilidad(general.getIdTipoEntrada(), 4)).isEqualTo(1);

		assertThat(tipoEntradaRepository.findById(general.getIdTipoEntrada()).orElseThrow().getCantidadDisponible())
				.isEqualTo(94);
	}
}
