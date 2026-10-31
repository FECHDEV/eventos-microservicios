package com.sarao.eventos.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import com.sarao.eventos.entity.Entrada;
import com.sarao.eventos.entity.EstadoEntrada;
import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.entity.Reserva;
import com.sarao.eventos.entity.TipoEntrada;

// CU-08: las entradas del Cliente y el detalle de una
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EntradaRepositoryTest {

	private static final LocalDateTime AHORA = LocalDateTime.of(2026, 9, 22, 20, 0);
	private static final Long ANA = 7L;
	private static final Long LUIS = 8L;

	@Autowired
	private TestEntityManager em;

	@Autowired
	private EntradaRepository entradaRepository;

	private TipoEntrada general;

	@BeforeEach
	void preparar() {
		Evento jazz = em.persist(Evento.registrar(1L, "Noche de jazz", null, LocalDate.of(2026, 10, 4),
				LocalTime.of(20, 0), LocalTime.of(23, 0), "Teatro Municipal", null, null));
		general = em.persist(TipoEntrada.registrar(jazz, "General", new BigDecimal("50"), 100));
	}

	private List<Entrada> compra(Long cliente, int cantidad) {
		return compra(cliente, general, cantidad);
	}

	// Una compra confirmada: la reserva y una entrada por unidad
	private List<Entrada> compra(Long cliente, TipoEntrada tipo, int cantidad) {
		Reserva reserva = em.persist(Reserva.crear(cliente, tipo, cantidad, AHORA));
		List<Entrada> entradas = new ArrayList<>();
		for (int i = 0; i < cantidad; i++) {
			entradas.add(em.persist(Entrada.emitir(reserva, 30L, "codigo-" + reserva.getIdReserva() + "-" + i, AHORA)));
		}
		return entradas;
	}

	private static List<Long> ids(List<Entrada> entradas) {
		return entradas.stream().map(Entrada::getIdEntrada).toList();
	}

	private List<EstadoEntrada> estados(List<Entrada> entradas) {
		return entradas.stream().map(entrada -> em.find(Entrada.class, entrada.getIdEntrada()).getEstado()).toList();
	}

	@Test
	void guardaYLeeTodasLasColumnas() {
		Entrada entrada = compra(ANA, 1).get(0);
		em.flush();
		em.clear();

		Entrada leida = entradaRepository.findById(entrada.getIdEntrada()).orElseThrow();

		assertThat(leida.getIdCliente()).isEqualTo(ANA);
		assertThat(leida.getIdPago()).isEqualTo(30L);
		assertThat(leida.getReserva().getIdReserva()).isEqualTo(entrada.getReserva().getIdReserva());
		assertThat(leida.getTipoEntrada().getIdTipoEntrada()).isEqualTo(general.getIdTipoEntrada());
		assertThat(leida.getCodigoQrEntrada()).isEqualTo(entrada.getCodigoQrEntrada());
		assertThat(leida.getEstado()).isEqualTo(EstadoEntrada.ACTIVA);
		assertThat(leida.getFechaEmision()).isEqualTo(AHORA);
	}

	// RF-2 y RF-3: solo las del Cliente, en el orden en que se emitieron, con todo lo que la respuesta muestra cargado
	// en la misma consulta (casos límite: sin una consulta por entrada ni por compra)
	@Test
	void listaLasDelClienteConTodoCargado() {
		List<Entrada> primera = compra(ANA, 2);
		compra(LUIS, 1);
		List<Entrada> segunda = compra(ANA, 1);
		em.flush();
		em.clear();

		List<Entrada> leidas = entradaRepository.delCliente(ANA);

		assertThat(ids(leidas)).containsExactly(primera.get(0).getIdEntrada(), primera.get(1).getIdEntrada(),
				segunda.get(0).getIdEntrada());
		Entrada una = leidas.get(0);
		assertThat(Hibernate.isInitialized(una.getReserva())).isTrue();
		assertThat(Hibernate.isInitialized(una.getTipoEntrada())).isTrue();
		assertThat(Hibernate.isInitialized(una.getTipoEntrada().getEvento())).isTrue();
	}

	// FA-01
	@Test
	void unClienteSinComprasNoTieneEntradas() {
		compra(LUIS, 1);
		em.flush();

		assertThat(entradaRepository.delCliente(ANA)).isEmpty();
	}

	// RF-4: una reserva sin entradas (pendiente, vencida o para devolución) no es una compra confirmada
	@Test
	void unaReservaSinEntradasNoAparece() {
		em.persist(Reserva.crear(ANA, general, 2, AHORA));
		em.flush();

		assertThat(entradaRepository.delCliente(ANA)).isEmpty();
	}

	// RF-2: la de otro Cliente no se encuentra, igual que una inexistente
	@Test
	void buscaUnaSoloEntreLasDelCliente() {
		Entrada deAna = compra(ANA, 1).get(0);
		Entrada deLuis = compra(LUIS, 1).get(0);
		em.flush();
		em.clear();

		Entrada leida = entradaRepository.buscarDelCliente(deAna.getIdEntrada(), ANA).orElseThrow();

		assertThat(Hibernate.isInitialized(leida.getReserva())).isTrue();
		assertThat(Hibernate.isInitialized(leida.getTipoEntrada().getEvento())).isTrue();
		assertThat(entradaRepository.buscarDelCliente(deLuis.getIdEntrada(), ANA)).isEmpty();
		assertThat(entradaRepository.buscarDelCliente(9_999L, ANA)).isEmpty();
	}

	// RF-8: el número de una entrada es su posición dentro de su compra
	@Test
	void listaLosIdsDeUnaCompraEnOrden() {
		List<Entrada> entradas = compra(ANA, 3);
		compra(ANA, 1);
		em.flush();

		assertThat(entradaRepository.idsDeLaCompra(entradas.get(0).getReserva().getIdReserva()))
				.containsExactlyElementsOf(ids(entradas));
	}

	// CU-07 RF-21: cuentan las emitidas del Cliente en el evento, salvo las devueltas
	@Test
	void cuentaLasEmitidasAlClienteEnElEventoSinLasDevueltas() {
		List<Entrada> deAna = compra(ANA, 3);
		ReflectionTestUtils.setField(deAna.get(0), "estado", EstadoEntrada.DEVUELTA);
		ReflectionTestUtils.setField(deAna.get(1), "estado", EstadoEntrada.UTILIZADA);
		compra(LUIS, 2);
		Evento feria = em.persist(Evento.registrar(1L, "Feria del libro", null, LocalDate.of(2026, 10, 10),
				LocalTime.of(10, 0), LocalTime.of(18, 0), "Campo ferial", null, null));
		compra(ANA, em.persist(TipoEntrada.registrar(feria, "General", new BigDecimal("20"), 100)), 1);
		em.flush();

		assertThat(entradaRepository.cantidadEmitidaAlCliente(ANA, general.getEvento().getIdEvento())).isEqualTo(2);
	}

	// CU-12 FA-02 (RF-26 y RF-28): solo las UTILIZADAS del evento pasan a ACTIVA; las activas, las devueltas y las de
	// otro evento no cambian
	@Test
	void reactivaSoloLasUtilizadasDelEvento() {
		List<Entrada> delEvento = compra(ANA, 3);
		ReflectionTestUtils.setField(delEvento.get(0), "estado", EstadoEntrada.UTILIZADA);
		ReflectionTestUtils.setField(delEvento.get(2), "estado", EstadoEntrada.DEVUELTA);
		Evento feria = em.persist(Evento.registrar(1L, "Feria del libro", null, LocalDate.of(2026, 10, 10),
				LocalTime.of(10, 0), LocalTime.of(18, 0), "Campo ferial", null, null));
		TipoEntrada deLaFeria = em.persist(TipoEntrada.registrar(feria, "General", new BigDecimal("20"), 100));
		List<Entrada> deOtroEvento = compra(LUIS, deLaFeria, 1);
		ReflectionTestUtils.setField(deOtroEvento.get(0), "estado", EstadoEntrada.UTILIZADA);
		em.flush();
		em.clear();

		int reactivadas = entradaRepository.reactivarUtilizadasDelEvento(general.getEvento().getIdEvento());

		assertThat(reactivadas).isEqualTo(1);
		em.clear();
		assertThat(estados(delEvento)).containsExactly(EstadoEntrada.ACTIVA, EstadoEntrada.ACTIVA,
				EstadoEntrada.DEVUELTA);
		assertThat(estados(deOtroEvento)).containsExactly(EstadoEntrada.UTILIZADA);
	}

	// RF-31: sin entradas usadas no cambia nada
	@Test
	void sinUtilizadasNoReactivaNinguna() {
		compra(ANA, 2);
		em.flush();

		assertThat(entradaRepository.reactivarUtilizadasDelEvento(general.getEvento().getIdEvento())).isZero();
	}
}
