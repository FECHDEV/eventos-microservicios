package com.sarao.eventos.repository;

import static org.assertj.core.api.Assertions.assertThat;

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

import com.sarao.eventos.entity.EstadoReserva;
import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.entity.Reserva;
import com.sarao.eventos.entity.TipoEntrada;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReservaRepositoryTest {

	private static final LocalDateTime AHORA = LocalDateTime.of(2026, 9, 21, 20, 0);
	private static final Long ANA = 7L;
	private static final Long LUIS = 8L;

	@Autowired
	private TestEntityManager em;

	@Autowired
	private ReservaRepository reservaRepository;

	private Evento jazz;
	private Evento feria;
	private TipoEntrada general;
	private TipoEntrada vip;
	private TipoEntrada feriaGeneral;

	@BeforeEach
	void preparar() {
		jazz = em.persist(evento("Noche de jazz"));
		feria = em.persist(evento("Feria del libro"));
		general = em.persist(TipoEntrada.registrar(jazz, "General", new BigDecimal("50"), 100));
		vip = em.persist(TipoEntrada.registrar(jazz, "VIP", new BigDecimal("150"), 10));
		feriaGeneral = em.persist(TipoEntrada.registrar(feria, "General", new BigDecimal("20"), 100));
	}

	private static Evento evento(String nombre) {
		return Evento.registrar(1L, nombre, null, LocalDate.of(2026, 10, 4), LocalTime.of(20, 0),
				LocalTime.of(23, 0), "Teatro Municipal", null, null);
	}

	// Creada en el instante indicado: vence 10 minutos después
	private Reserva reserva(Long cliente, TipoEntrada tipo, int cantidad, LocalDateTime creada) {
		return em.persist(Reserva.crear(cliente, tipo, cantidad, creada));
	}

	private static void confirmar(Reserva reserva) {
		ReflectionTestUtils.setField(reserva, "estado", EstadoReserva.CONFIRMADA);
	}

	@Test
	void guardaYLeeTodasLasColumnas() {
		Long id = em.persistAndGetId(Reserva.crear(ANA, general, 3, AHORA), Long.class);
		em.flush();
		em.clear();

		Reserva leida = reservaRepository.findById(id).orElseThrow();

		assertThat(leida.getIdCliente()).isEqualTo(ANA);
		assertThat(leida.getTipoEntrada().getIdTipoEntrada()).isEqualTo(general.getIdTipoEntrada());
		assertThat(leida.getCantidad()).isEqualTo(3);
		assertThat(leida.getPrecioUnitario()).isEqualTo(new BigDecimal("50.00"));
		assertThat(leida.getFechaCreacion()).isEqualTo(AHORA);
		assertThat(leida.getFechaExpiracion()).isEqualTo(AHORA.plusMinutes(10));
		assertThat(leida.getEstado()).isEqualTo(EstadoReserva.VIGENTE);
	}

	// Vencida = VIGENTE con la expiración en el pasado o justo ahora; solo las del evento, en orden de id
	@Test
	void listaLasVencidasDelEventoYNadaMas() {
		Reserva vencida = reserva(ANA, general, 2, AHORA.minusMinutes(15));
		Reserva justoAhora = reserva(LUIS, vip, 1, AHORA.minusMinutes(10));
		reserva(ANA, general, 1, AHORA.minusMinutes(5));
		confirmar(reserva(LUIS, general, 4, AHORA.minusMinutes(30)));
		reserva(ANA, feriaGeneral, 1, AHORA.minusMinutes(30));
		em.flush();
		em.clear();

		assertThat(reservaRepository.vencidasDelEvento(jazz.getIdEvento(), AHORA)).containsExactly(
				new ReservaVencida(vencida.getIdReserva(), general.getIdTipoEntrada(), 2),
				new ReservaVencida(justoAhora.getIdReserva(), vip.getIdTipoEntrada(), 1));
		assertThat(reservaRepository.hayVencidas(jazz.getIdEvento(), AHORA)).isTrue();
		assertThat(reservaRepository.hayVencidas(jazz.getIdEvento(), AHORA.minusMinutes(6))).isFalse();
	}

	// RF-19: una reserva pasa a EXPIRADA una sola vez; la segunda vez no cambia nada
	@Test
	void expirarSoloCambiaUnaVigente() {
		Reserva vencida = reserva(ANA, general, 2, AHORA.minusMinutes(15));
		Reserva confirmada = reserva(LUIS, general, 1, AHORA.minusMinutes(15));
		confirmar(confirmada);
		em.flush();

		assertThat(reservaRepository.expirar(vencida.getIdReserva())).isEqualTo(1);
		assertThat(reservaRepository.expirar(vencida.getIdReserva())).isZero();
		assertThat(reservaRepository.expirar(confirmada.getIdReserva())).isZero();

		assertThat(reservaRepository.findById(vencida.getIdReserva()).orElseThrow().getEstado())
				.isEqualTo(EstadoReserva.EXPIRADA);
		assertThat(reservaRepository.findById(confirmada.getIdReserva()).orElseThrow().getEstado())
				.isEqualTo(EstadoReserva.CONFIRMADA);
	}

	// RF-10 y RF-18: suma las vigentes del cliente en cualquier tipo del evento; una vencida no cuenta aunque
	// nadie la haya marcado
	@Test
	void cuentaLasVigentesDelClienteEnElEvento() {
		reserva(ANA, general, 2, AHORA.minusMinutes(5));
		reserva(ANA, vip, 1, AHORA.minusMinutes(1));
		reserva(ANA, general, 5, AHORA.minusMinutes(10));
		reserva(ANA, feriaGeneral, 3, AHORA.minusMinutes(1));
		reserva(LUIS, general, 4, AHORA.minusMinutes(1));
		em.flush();
		em.clear();

		assertThat(reservaRepository.cantidadVigenteDelCliente(ANA, jazz.getIdEvento(), AHORA))
				.isEqualTo(3);
		assertThat(reservaRepository.cantidadVigenteDelCliente(LUIS, feria.getIdEvento(), AHORA))
				.isZero();
	}

	// RF-20: solo el dueño la encuentra
	@Test
	void buscaLaReservaDelClienteConSuTipoYSuEvento() {
		Reserva deAna = reserva(ANA, general, 2, AHORA);
		em.flush();
		em.clear();

		assertThat(reservaRepository.idEventoDeLaReserva(deAna.getIdReserva(), ANA))
				.contains(jazz.getIdEvento());
		assertThat(reservaRepository.idEventoDeLaReserva(deAna.getIdReserva(), LUIS)).isEmpty();

		Reserva leida = reservaRepository.buscarDelCliente(deAna.getIdReserva(), ANA).orElseThrow();
		em.clear();
		// Con el contexto vacío, el tipo y el evento ya tienen que estar cargados
		assertThat(leida.getTipoEntrada().getNombre()).isEqualTo("General");
		assertThat(leida.getTipoEntrada().getEvento().getNombre()).isEqualTo("Noche de jazz");
		assertThat(reservaRepository.buscarDelCliente(deAna.getIdReserva(), LUIS)).isEmpty();
	}
}
