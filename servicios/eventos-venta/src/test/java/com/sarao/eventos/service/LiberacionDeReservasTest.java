package com.sarao.eventos.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sarao.eventos.repository.EventoRepository;
import com.sarao.eventos.repository.ReservaRepository;
import com.sarao.eventos.repository.ReservaVencida;
import com.sarao.eventos.repository.TipoEntradaRepository;

// CU-06 RF-17 y RF-19
@ExtendWith(MockitoExtension.class)
class LiberacionDeReservasTest {

	private static final LocalDateTime AHORA = LocalDateTime.of(2026, 9, 21, 20, 0);

	@Mock
	private EventoRepository eventoRepository;

	@Mock
	private ReservaRepository reservaRepository;

	@Mock
	private TipoEntradaRepository tipoEntradaRepository;

	private LiberacionDeReservas liberacion;

	@BeforeEach
	void crear() {
		liberacion = new LiberacionDeReservas(eventoRepository, reservaRepository, tipoEntradaRepository);
	}

	// Una consulta pública casi nunca bloquea: sin vencidas no toca el evento
	@Test
	void sinVencidasNoBloqueaNiActualiza() {
		given(reservaRepository.hayVencidas(5L, AHORA)).willReturn(false);

		liberacion.liberarVencidas(5L, AHORA);

		then(eventoRepository).shouldHaveNoInteractions();
		then(reservaRepository).should(never()).vencidasDelEvento(any(), any());
		then(tipoEntradaRepository).shouldHaveNoInteractions();
	}

	// El orden de los bloqueos es evento, reserva y tipo
	@Test
	void conVencidasBloqueaElEventoYDevuelveCadaCantidad() {
		given(reservaRepository.hayVencidas(5L, AHORA)).willReturn(true);
		given(reservaRepository.vencidasDelEvento(5L, AHORA))
				.willReturn(List.of(new ReservaVencida(11L, 3L, 2), new ReservaVencida(12L, 4L, 1)));
		given(reservaRepository.expirar(any())).willReturn(1);

		liberacion.liberarVencidas(5L, AHORA);

		InOrder orden = inOrder(eventoRepository, reservaRepository, tipoEntradaRepository);
		orden.verify(eventoRepository).findConBloqueoByIdEvento(5L);
		orden.verify(reservaRepository).expirar(11L);
		orden.verify(tipoEntradaRepository).devolverDisponibilidad(3L, 2);
		orden.verify(reservaRepository).expirar(12L);
		orden.verify(tipoEntradaRepository).devolverDisponibilidad(4L, 1);
	}

	// RF-19: si otro barrido ya la expiró, no se devuelve dos veces
	@Test
	void unaReservaQueOtroYaExpiroNoDevuelveNada() {
		given(reservaRepository.vencidasDelEvento(5L, AHORA)).willReturn(List.of(new ReservaVencida(11L, 3L, 2)));
		given(reservaRepository.expirar(11L)).willReturn(0);

		liberacion.liberarVencidasConElEventoBloqueado(5L, AHORA);

		then(tipoEntradaRepository).should(never()).devolverDisponibilidad(any(), anyInt());
	}

	@Test
	void conElEventoYaBloqueadoNoLoVuelveABuscar() {
		given(reservaRepository.vencidasDelEvento(5L, AHORA)).willReturn(List.of());

		liberacion.liberarVencidasConElEventoBloqueado(5L, AHORA);

		then(eventoRepository).shouldHaveNoInteractions();
	}
}
