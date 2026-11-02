package com.sarao.eventos.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.sarao.eventos.entity.Entrada;
import com.sarao.eventos.entity.EstadoReserva;
import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.entity.Reserva;
import com.sarao.eventos.entity.TipoEntrada;
import com.sarao.eventos.exception.ReservaNoEncontradaException;
import com.sarao.eventos.repository.EntradaRepository;
import com.sarao.eventos.repository.EventoRepository;
import com.sarao.eventos.repository.ReservaRepository;
import com.sarao.eventos.repository.TipoEntradaRepository;
import lombok.RequiredArgsConstructor;

// Lo que Pagos pide sobre una reserva (decisiones.md → Microservicios: confirmación en dos pasos). Los bloqueos van en el
// orden de CU-06: evento, reserva y tipo (CU-07 RF-14). READ_COMMITTED, como reservar: lo que se lee después del
// bloqueo del evento incluye lo que otra transacción acaba de confirmar
@Service
@RequiredArgsConstructor
public class ConfirmacionDeReservas {

	private final EventoRepository eventoRepository;
	private final ReservaRepository reservaRepository;
	private final TipoEntradaRepository tipoEntradaRepository;
	private final EntradaRepository entradaRepository;
	private final LiberacionDeReservas liberacionDeReservas;
	private final GeneradorDeCodigos generadorDeCodigos;
	private final ApplicationEventPublisher eventos;
	private final Clock clock;

	// El pago ya está PAGADO en Pagos. Idempotente (RF-13): una reserva confirmada responde CONFIRMADA sin emitir otras
	// entradas, así Pagos puede repetir el pedido si se perdió la respuesta. SIN_CUPO no cambia nada
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public ResultadoConfirmacion confirmar(Long idReserva, Long idPago) {
		LocalDateTime ahora = LocalDateTime.now(clock);
		Long idEvento = reservaRepository.idEventoDe(idReserva).orElseThrow(ReservaNoEncontradaException::new);
		Evento evento = eventoRepository.findConBloqueoByIdEvento(idEvento).orElseThrow();
		// Puede vaciar el contexto: la reserva y el tipo se leen después, ya bloqueados
		liberacionDeReservas.liberarVencidasConElEventoBloqueado(idEvento, ahora);
		Reserva reserva = reservaRepository.findConBloqueoByIdReserva(idReserva).orElseThrow();
		if (reserva.getEstado() == EstadoReserva.CONFIRMADA) {
			return ResultadoConfirmacion.CONFIRMADA;
		}
		TipoEntrada tipo = tipoEntradaRepository
				.findConBloqueoByIdTipoEntradaAndEvento_IdEvento(reserva.getTipoEntrada().getIdTipoEntrada(), idEvento)
				.orElseThrow();
		if (!reserva.estaVigente(ahora)) {
			// FA-03: la reserva venció y su cupo volvió. Se confirma solo si el evento sigue vendiendo y hay cupo
			// (decisiones.md → Realizar pago: pago tardío solo si el evento sigue vendiendo)
			boolean sigueVendiendo = evento.seMuestraEnElCatalogo(ahora) && evento.isVentaAbierta();
			if (!sigueVendiendo || !tipo.verificarDisponibilidad(reserva.getCantidad())) {
				return ResultadoConfirmacion.SIN_CUPO;
			}
			tipo.reducirDisponibilidad(reserva.getCantidad());
		}
		reserva.confirmar();
		for (int i = 0; i < reserva.getCantidad(); i++) {
			entradaRepository.save(Entrada.emitir(reserva, idPago, generadorDeCodigos.nuevoCodigo(), ahora));
		}
		eventos.publishEvent(new CompraConfirmada(idReserva));
		return ResultadoConfirmacion.CONFIRMADA;
	}

	// FA-01 y CU-06 FA-05: un error del pago libera una reserva vigente. Una vencida ya devolvió su cupo y una
	// confirmada no se toca, así que repetir el pedido no cambia nada
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void liberar(Long idReserva) {
		LocalDateTime ahora = LocalDateTime.now(clock);
		Long idEvento = reservaRepository.idEventoDe(idReserva).orElseThrow(ReservaNoEncontradaException::new);
		eventoRepository.findConBloqueoByIdEvento(idEvento);
		liberacionDeReservas.liberarVencidasConElEventoBloqueado(idEvento, ahora);
		Reserva reserva = reservaRepository.findConBloqueoByIdReserva(idReserva).orElseThrow();
		if (!reserva.estaVigente(ahora)) {
			return;
		}
		TipoEntrada tipo = tipoEntradaRepository
				.findConBloqueoByIdTipoEntradaAndEvento_IdEvento(reserva.getTipoEntrada().getIdTipoEntrada(), idEvento)
				.orElseThrow();
		reserva.liberar();
		tipo.liberarDisponibilidad(reserva.getCantidad());
	}
}
