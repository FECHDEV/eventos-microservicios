package com.sarao.eventos.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sarao.eventos.repository.EventoRepository;
import com.sarao.eventos.repository.ReservaRepository;
import com.sarao.eventos.repository.ReservaVencida;
import com.sarao.eventos.repository.TipoEntradaRepository;
import lombok.RequiredArgsConstructor;

// CU-06 FA-04 con verificación perezosa (plan-implementacion.md, Paso 0, decisión 6): las reservas vencidas de un evento
// se liberan justo antes de leer o usar su disponibilidad, sin tarea programada. Son actualizaciones en bloque
// condicionadas: cada reserva pasa de VIGENTE a EXPIRADA una sola vez aunque dos barridos corran a la vez (RF-19), y la
// disponible se suma en la base, sin depender de lo que la transacción ya leyó. Esas actualizaciones vacían el contexto
// de persistencia: quien llama vuelve a leer después lo que necesite
@Component
@RequiredArgsConstructor
public class LiberacionDeReservas {

	private final EventoRepository eventoRepository;
	private final ReservaRepository reservaRepository;
	private final TipoEntradaRepository tipoEntradaRepository;

	// Para quien no tiene el evento bloqueado (el detalle del catálogo, la lista de tipos, la consulta de una reserva).
	// Bloquea el evento solo si hay algo que liberar: una consulta pública casi nunca toma el bloqueo
	@Transactional
	public void liberarVencidas(Long idEvento, LocalDateTime ahora) {
		if (!reservaRepository.hayVencidas(idEvento, ahora)) {
			return;
		}
		eventoRepository.findConBloqueoByIdEvento(idEvento);
		liberarVencidasConElEventoBloqueado(idEvento, ahora);
	}

	// Para quien ya bloqueó el evento (reservar, cambiar la cantidad total de un tipo). El orden de los bloqueos es
	// siempre evento, reserva y tipo
	@Transactional(propagation = Propagation.MANDATORY)
	public void liberarVencidasConElEventoBloqueado(Long idEvento, LocalDateTime ahora) {
		for (ReservaVencida vencida : reservaRepository.vencidasDelEvento(idEvento, ahora)) {
			if (reservaRepository.expirar(vencida.idReserva()) == 1) {
				tipoEntradaRepository.devolverDisponibilidad(vencida.idTipoEntrada(), vencida.cantidad());
			}
		}
	}
}
