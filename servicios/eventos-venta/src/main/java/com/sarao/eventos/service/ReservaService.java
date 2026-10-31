package com.sarao.eventos.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.sarao.eventos.dto.ReservaRequest;
import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.entity.Reserva;
import com.sarao.eventos.entity.TipoEntrada;
import com.sarao.eventos.exception.EdadNoPermitidaException;
import com.sarao.eventos.exception.EntradasNoDisponiblesException;
import com.sarao.eventos.exception.LimiteCompraExcedidoException;
import com.sarao.eventos.exception.ReservaNoEncontradaException;
import com.sarao.eventos.exception.TipoEntradaNoEncontradoException;
import com.sarao.eventos.exception.VentaCerradaException;
import com.sarao.eventos.repository.EventoRepository;
import com.sarao.eventos.repository.ReservaRepository;
import com.sarao.eventos.repository.TipoEntradaRepository;
import lombok.RequiredArgsConstructor;

// CU-06 hasta la reserva vigente; el pago es CU-07 (decisiones.md → Adquirir entradas (CU-06)). Solo el Cliente llega
// acá (SecurityConfig)
@Service
@RequiredArgsConstructor
public class ReservaService {

	private final EventoRepository eventoRepository;
	private final TipoEntradaRepository tipoEntradaRepository;
	private final ReservaRepository reservaRepository;
	private final LiberacionDeReservas liberacionDeReservas;
	private final Clock clock;

	// Bloqueos en orden: evento, reserva (al liberar) y tipo. READ_COMMITTED: el límite se cuenta con el evento ya
	// bloqueado y tiene que ver la reserva que otra compra del mismo Cliente acaba de confirmar. Con REPEATABLE READ, el
	// nivel por defecto de MySQL, la foto sale de la primera lectura, previa al bloqueo, y dos compras simultáneas
	// podrían pasar juntas el límite. La fecha de nacimiento llega ya consultada (ConsultaDeClientes): una llamada a otro
	// servicio no puede esperar con el evento bloqueado
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public DetalleReserva reservar(Long idCliente, LocalDate fechaNacimiento, ReservaRequest solicitud) {
		LocalDateTime ahora = LocalDateTime.now(clock);
		Long idTipo = solicitud.idTipoEntrada();
		int cantidad = solicitud.cantidad();
		Long idEvento = tipoEntradaRepository.idEventoDe(idTipo).orElseThrow(TipoEntradaNoEncontradoException::new);
		// Uno que no se muestra en el catálogo (borrador, cancelado o terminado) responde como un tipo inexistente,
		// también durante el minuto en que el cierre automático todavía no le cerró la venta (RF-5)
		Evento evento = eventoRepository.findConBloqueoByIdEvento(idEvento)
				.filter(candidato -> candidato.seMuestraEnElCatalogo(ahora))
				.orElseThrow(TipoEntradaNoEncontradoException::new);
		if (!evento.isVentaAbierta()) {
			throw new VentaCerradaException();
		}
		liberacionDeReservas.liberarVencidasConElEventoBloqueado(idEvento, ahora);
		TipoEntrada tipo = tipoEntradaRepository.findConBloqueoByIdTipoEntradaAndEvento_IdEvento(idTipo, idEvento)
				.orElseThrow(TipoEntradaNoEncontradoException::new);
		if (!tipo.verificarDisponibilidad(cantidad)) {
			throw new EntradasNoDisponiblesException();
		}
		if (!evento.verificarRestriccionEdad(fechaNacimiento)) {
			throw new EdadNoPermitidaException(evento.getRestriccionEdad());
		}
		if (evento.getLimiteCompra() != null) {
			// shortcut: cuenta solo las reservas vigentes; las entradas emitidas se suman cuando existan (CU-07 RF-21)
			int yaComprometidas = (int) reservaRepository.cantidadVigenteDelCliente(idCliente, idEvento, ahora);
			if (!evento.verificarLimiteCompra(yaComprometidas, cantidad)) {
				throw new LimiteCompraExcedidoException(evento.getLimiteCompra(),
						evento.cuantasPuedeAgregar(yaComprometidas));
			}
		}
		tipo.reducirDisponibilidad(cantidad);
		Reserva reserva = reservaRepository.save(Reserva.crear(idCliente, tipo, cantidad, ahora));
		return new DetalleReserva(reserva, tipo, evento, ahora);
	}

	// Escribe: libera las vencidas del evento antes de leer la reserva, que así sale con su estado real (RF-20)
	@Transactional
	public DetalleReserva consultar(Long idCliente, Long idReserva) {
		LocalDateTime ahora = LocalDateTime.now(clock);
		Long idEvento = reservaRepository.idEventoDeLaReserva(idReserva, idCliente)
				.orElseThrow(ReservaNoEncontradaException::new);
		liberacionDeReservas.liberarVencidas(idEvento, ahora);
		Reserva reserva = reservaRepository.buscarDelCliente(idReserva, idCliente)
				.orElseThrow(ReservaNoEncontradaException::new);
		return new DetalleReserva(reserva, reserva.getTipoEntrada(), reserva.getTipoEntrada().getEvento(), ahora);
	}
}
