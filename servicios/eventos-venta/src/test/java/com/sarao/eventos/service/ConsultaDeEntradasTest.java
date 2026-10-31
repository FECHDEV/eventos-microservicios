package com.sarao.eventos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.sarao.eventos.entity.Entrada;
import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.entity.Reserva;
import com.sarao.eventos.entity.TipoEntrada;
import com.sarao.eventos.exception.EntradaNoEncontradaException;
import com.sarao.eventos.repository.EntradaRepository;

@ExtendWith(MockitoExtension.class)
class ConsultaDeEntradasTest {

	// 22/09/2026 a las 20:00 en La Paz
	private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-09-23T00:00:00Z"), ZoneId.of("America/La_Paz"));
	private static final LocalDateTime REGISTRO = LocalDateTime.of(2026, 9, 1, 8, 0);

	@Mock
	private EntradaRepository entradaRepository;

	private ConsultaDeEntradas consulta;
	private long ultimoId;

	@BeforeEach
	void preparar() {
		consulta = new ConsultaDeEntradas(entradaRepository, RELOJ);
	}

	private Evento evento(String nombre, LocalDate fecha, LocalTime inicio, LocalTime fin) {
		Evento evento = Evento.registrar(1L, nombre, null, fecha, inicio, fin, "Teatro Municipal", null, null);
		ReflectionTestUtils.setField(evento, "idEvento", ++ultimoId);
		return evento;
	}

	private Evento evento(String nombre, LocalDate fecha) {
		return evento(nombre, fecha, LocalTime.of(20, 0), LocalTime.of(23, 0));
	}

	// Una compra confirmada con ids, como la devuelve el repositorio
	private List<Entrada> compra(Evento evento, int cantidad) {
		TipoEntrada general = TipoEntrada.registrar(evento, "General", new BigDecimal("50"), 100);
		Reserva reserva = Reserva.crear(7L, general, cantidad, REGISTRO);
		ReflectionTestUtils.setField(reserva, "idReserva", ++ultimoId);
		List<Entrada> entradas = new ArrayList<>();
		for (int i = 0; i < cantidad; i++) {
			Entrada entrada = Entrada.emitir(reserva, 30L, "codigo-" + ultimoId, REGISTRO);
			ReflectionTestUtils.setField(entrada, "idEntrada", ++ultimoId);
			entradas.add(entrada);
		}
		return entradas;
	}

	@SafeVarargs
	private static List<Entrada> juntas(List<Entrada>... compras) {
		return Stream.of(compras).flatMap(List::stream).toList();
	}

	private static List<String> nombres(List<CompraDelCliente> compras) {
		return compras.stream().map(compra -> compra.evento().getNombre()).toList();
	}

	// RF-3: una compra por reserva, con sus entradas en el orden en que se emitieron
	@Test
	void agrupaLasEntradasPorCompra() {
		List<Entrada> jazz = compra(evento("Noche de jazz", LocalDate.of(2026, 10, 4)), 2);
		List<Entrada> feria = compra(evento("Feria del libro", LocalDate.of(2026, 10, 10)), 1);
		given(entradaRepository.delCliente(7L)).willReturn(juntas(jazz, feria));

		EntradasDelCliente resultado = consulta.delCliente(7L);

		assertThat(resultado.proximas()).hasSize(2);
		CompraDelCliente primera = resultado.proximas().get(0);
		assertThat(primera.entradas()).containsExactlyElementsOf(jazz);
		assertThat(primera.reserva()).isSameAs(jazz.get(0).getReserva());
		assertThat(primera.tipo()).isSameAs(jazz.get(0).getTipoEntrada());
		assertThat(primera.evento().getNombre()).isEqualTo("Noche de jazz");
		assertThat(resultado.pasadas()).isEmpty();
	}

	// RF-5: próximos del más cercano al más lejano; pasados del más reciente al más antiguo
	@Test
	void separaProximosDePasadosYLosOrdena() {
		List<Entrada> lejano = compra(evento("Lejano", LocalDate.of(2026, 12, 1)), 1);
		List<Entrada> viejo = compra(evento("Viejo", LocalDate.of(2026, 8, 1)), 1);
		List<Entrada> cercano = compra(evento("Cercano", LocalDate.of(2026, 10, 1)), 1);
		List<Entrada> reciente = compra(evento("Reciente", LocalDate.of(2026, 9, 1)), 1);
		given(entradaRepository.delCliente(7L)).willReturn(juntas(lejano, viejo, cercano, reciente));

		EntradasDelCliente resultado = consulta.delCliente(7L);

		assertThat(nombres(resultado.proximas())).containsExactly("Cercano", "Lejano");
		assertThat(nombres(resultado.pasadas())).containsExactly("Reciente", "Viejo");
	}

	// RF-5: a las 20:00, el de hoy de 20:00 a 23:00 todavía no terminó; el de anoche, que cruzó la medianoche hasta
	// las 3:00, sí
	@Test
	void unEventoEsProximoHastaQueTermina() {
		List<Entrada> hoy = compra(evento("Hoy", LocalDate.of(2026, 9, 22)), 1);
		List<Entrada> anoche = compra(evento("Anoche", LocalDate.of(2026, 9, 21), LocalTime.of(22, 0),
				LocalTime.of(3, 0)), 1);
		given(entradaRepository.delCliente(7L)).willReturn(juntas(hoy, anoche));

		EntradasDelCliente resultado = consulta.delCliente(7L);

		assertThat(nombres(resultado.proximas())).containsExactly("Hoy");
		assertThat(nombres(resultado.pasadas())).containsExactly("Anoche");
	}

	// RF-5: dos compras del mismo evento, por orden de compra
	@Test
	void dosComprasDelMismoEventoVanPorOrdenDeCompra() {
		Evento jazz = evento("Noche de jazz", LocalDate.of(2026, 10, 4));
		List<Entrada> primera = compra(jazz, 1);
		List<Entrada> segunda = compra(jazz, 2);
		given(entradaRepository.delCliente(7L)).willReturn(juntas(segunda, primera));

		EntradasDelCliente resultado = consulta.delCliente(7L);

		assertThat(resultado.proximas()).extracting(CompraDelCliente::entradas).containsExactly(primera, segunda);
	}

	// Pedido en la prueba manual: los cancelados van aparte, hayan terminado o no, del más reciente al más antiguo
	@Test
	void losCanceladosVanAparteHayanTerminadoONo() {
		Evento futuro = evento("Cancelado futuro", LocalDate.of(2026, 11, 1));
		futuro.cancelar();
		Evento viejo = evento("Cancelado viejo", LocalDate.of(2026, 8, 1));
		viejo.cancelar();
		List<Entrada> proximo = compra(evento("Próximo", LocalDate.of(2026, 10, 1)), 1);
		List<Entrada> pasado = compra(evento("Pasado", LocalDate.of(2026, 9, 1)), 1);
		given(entradaRepository.delCliente(7L))
				.willReturn(juntas(compra(viejo, 1), proximo, compra(futuro, 1), pasado));

		EntradasDelCliente resultado = consulta.delCliente(7L);

		assertThat(nombres(resultado.proximas())).containsExactly("Próximo");
		assertThat(nombres(resultado.pasadas())).containsExactly("Pasado");
		assertThat(nombres(resultado.canceladas())).containsExactly("Cancelado futuro", "Cancelado viejo");
	}

	// FA-01: sin compras, las listas vacías; no es un error
	@Test
	void sinEntradasDevuelveListasVacias() {
		given(entradaRepository.delCliente(7L)).willReturn(List.of());

		EntradasDelCliente resultado = consulta.delCliente(7L);

		assertThat(resultado.proximas()).isEmpty();
		assertThat(resultado.pasadas()).isEmpty();
		assertThat(resultado.canceladas()).isEmpty();
	}

	// RF-8: el número de la entrada dentro de su compra y la cantidad de la compra
	@Test
	void elDetalleTraeElNumeroYLaCantidadDeLaCompra() {
		List<Entrada> jazz = compra(evento("Noche de jazz", LocalDate.of(2026, 10, 4)), 3);
		Entrada segunda = jazz.get(1);
		given(entradaRepository.buscarDelCliente(segunda.getIdEntrada(), 7L)).willReturn(Optional.of(segunda));
		given(entradaRepository.idsDeLaCompra(segunda.getReserva().getIdReserva()))
				.willReturn(jazz.stream().map(Entrada::getIdEntrada).toList());

		DetalleEntrada detalle = consulta.detalle(7L, segunda.getIdEntrada());

		assertThat(detalle.entrada()).isSameAs(segunda);
		assertThat(detalle.numero()).isEqualTo(2);
		assertThat(detalle.cantidad()).isEqualTo(3);
	}

	// RF-2: la de otro Cliente, igual que una inexistente
	@Test
	void unaEntradaAjenaOInexistenteNoSeEncuentra() {
		given(entradaRepository.buscarDelCliente(99L, 7L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> consulta.detalle(7L, 99L)).isInstanceOf(EntradaNoEncontradaException.class)
				.extracting("codigo").isEqualTo("ENTRADA_NO_ENCONTRADA");
	}
}
