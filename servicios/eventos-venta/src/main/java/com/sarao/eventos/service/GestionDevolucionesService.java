package com.sarao.eventos.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.sarao.eventos.entity.Entrada;
import com.sarao.eventos.entity.EstadoEntrada;
import com.sarao.eventos.entity.EstadoEvento;
import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.exception.CompraNoEncontradaException;
import com.sarao.eventos.exception.EntradaNoDevolvibleException;
import com.sarao.eventos.exception.EntradaNoEncontradaException;
import com.sarao.eventos.exception.EntradaYaDevueltaException;
import com.sarao.eventos.exception.EventoNoCanceladoException;
import com.sarao.eventos.exception.EventoNoEncontradoException;
import com.sarao.eventos.repository.EntradaRepository;
import com.sarao.eventos.repository.EventoRepository;
import com.sarao.eventos.repository.PendientesDelEvento;
import lombok.RequiredArgsConstructor;

// CU-13: solo el Administrador llega acá (SecurityConfig). El registro es el estado DEVUELTA; la devolución económica
// ocurre fuera del sistema y el pago no cambia (decisiones.md → Gestionar devoluciones (CU-13)). Vive acá y no en Pagos:
// lo que cambia es la entrada (decisiones.md → Microservicios: corte en servicios)
@Service
@RequiredArgsConstructor
public class GestionDevolucionesService {

	private final EventoRepository eventoRepository;
	private final EntradaRepository entradaRepository;
	private final ConsultaDeClientes consultaDeClientes;

	@Transactional(readOnly = true)
	public List<EventoCancelado> eventosCancelados() {
		Map<Long, Long> pendientes = entradaRepository.pendientesDeLosCancelados().stream()
				.collect(Collectors.toMap(PendientesDelEvento::idEvento, PendientesDelEvento::pendientes));
		return eventoRepository.findByEstadoOrderByFechaDescHoraInicioDesc(EstadoEvento.CANCELADO).stream()
				.map(evento -> new EventoCancelado(evento,
						pendientes.getOrDefault(evento.getIdEvento(), 0L).intValue()))
				.toList();
	}

	// Una compra con entradas activas y devueltas aparece en las dos listas; las usadas no aparecen (RF-6). Sin
	// transacción: los datos de los clientes se piden a Seguridad sin retener una conexión de la base
	public DevolucionesDelEvento delEvento(Long idEvento) {
		Evento evento = eventoRepository.findById(idEvento).orElseThrow(EventoNoEncontradoException::new);
		if (!evento.estaCancelado()) {
			throw new EventoNoCanceladoException();
		}
		Map<Long, List<Entrada>> porCompra = new LinkedHashMap<>();
		for (Entrada entrada : entradaRepository.delEventoConSuCompra(idEvento)) {
			porCompra.computeIfAbsent(entrada.getReserva().getIdReserva(), id -> new ArrayList<>()).add(entrada);
		}
		Map<Long, Cuenta> clientes = consultaDeClientes.cuentas(porCompra.values().stream()
				.map(deLaCompra -> deLaCompra.get(0).getIdCliente()).collect(Collectors.toSet()));
		List<CompraParaDevolucion> pendientes = new ArrayList<>();
		List<CompraParaDevolucion> devueltas = new ArrayList<>();
		for (List<Entrada> deLaCompra : porCompra.values()) {
			Cuenta cliente = clientes.get(deLaCompra.get(0).getIdCliente());
			CompraParaDevolucion.de(deLaCompra, cliente, EstadoEntrada.ACTIVA).ifPresent(pendientes::add);
			CompraParaDevolucion.de(deLaCompra, cliente, EstadoEntrada.DEVUELTA).ifPresent(devueltas::add);
		}
		return new DevolucionesDelEvento(evento, pendientes, devueltas);
	}

	// RF-9. Con el evento bloqueado y en READ_COMMITTED: dos registros de la misma compra se serializan y el segundo
	// ve lo que confirmó el primero (RF-13)
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public int registrarCompra(Long idReserva) {
		Long idEvento = entradaRepository.idEventoDeLaCompra(idReserva)
				.orElseThrow(CompraNoEncontradaException::new);
		bloquearCancelado(idEvento);
		int devueltas = entradaRepository.devolverLasActivasDeLaCompra(idReserva);
		if (devueltas == 0) {
			// Sin activas: FA-02 si ya se devolvió; si solo tiene usadas, no hay nada que devolver (RF-12)
			throw entradaRepository.hayDevueltasEnLaCompra(idReserva) ? new EntradaYaDevueltaException()
					: new EntradaNoDevolvibleException();
		}
		return devueltas;
	}

	// RF-10, con las mismas reglas. Devuelve cuántas registró: 1
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public int registrarEntrada(Long idEntrada) {
		Long idEvento = entradaRepository.idEventoDeLaEntrada(idEntrada)
				.orElseThrow(EntradaNoEncontradaException::new);
		bloquearCancelado(idEvento);
		EstadoEntrada estado = entradaRepository.estadoDe(idEntrada).orElseThrow(EntradaNoEncontradaException::new);
		if (estado == EstadoEntrada.UTILIZADA) {
			throw new EntradaNoDevolvibleException();
		}
		int devueltas = estado == EstadoEntrada.DEVUELTA ? 0 : entradaRepository.devolver(idEntrada);
		if (devueltas == 0) {
			throw new EntradaYaDevueltaException();
		}
		return devueltas;
	}

	private void bloquearCancelado(Long idEvento) {
		Evento evento = eventoRepository.findConBloqueoByIdEvento(idEvento)
				.orElseThrow(EventoNoEncontradoException::new);
		if (!evento.estaCancelado()) {
			throw new EventoNoCanceladoException();
		}
	}
}
