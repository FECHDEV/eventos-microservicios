package com.sarao.eventos.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.sarao.eventos.entity.Acceso;
import com.sarao.eventos.entity.Entrada;
import com.sarao.eventos.entity.EstadoEntrada;
import com.sarao.eventos.entity.EstadoEvento;
import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.exception.EventoNoDisponibleException;
import com.sarao.eventos.exception.EventoNoEncontradoException;
import com.sarao.eventos.repository.AccesoRepository;
import com.sarao.eventos.repository.EntradaPorCodigo;
import com.sarao.eventos.repository.EntradaRepository;
import com.sarao.eventos.repository.EventoRepository;
import lombok.RequiredArgsConstructor;

// CU-15: solo el Personal autorizado llega acá (SecurityConfig), y el Personal sale de la sesión. Los rechazos de FA-01
// a FA-03 son resultados de la validación, no errores (decisiones.md → Validar entrada (CU-15))
@Service
@RequiredArgsConstructor
public class ValidacionDeEntradas {

	private final EventoRepository eventoRepository;
	private final EntradaRepository entradaRepository;
	private final AccesoRepository accesoRepository;
	private final Clock clock;

	// RF-2: un evento sigue en curso hasta su fin, que puede ser hoy aunque sea de ayer (cruce de medianoche)
	@Transactional(readOnly = true)
	public List<Evento> eventosParaValidar() {
		LocalDateTime ahora = LocalDateTime.now(clock);
		LocalDate hoy = ahora.toLocalDate();
		return eventoRepository
				.findByEstadoAndFechaBetweenOrderByFechaAscHoraInicioAsc(EstadoEvento.ACTIVO, hoy.minusDays(1), hoy)
				.stream().filter(evento -> evento.admiteValidacion(ahora)).toList();
	}

	// RF-5 a RF-11. La entrada se busca sin cargarla y se relee con bloqueo, en READ_COMMITTED: dos validaciones de la
	// misma entrada se serializan y la segunda la ve UTILIZADA. El evento se lee con bloqueo compartido, antes que la
	// entrada (el mismo orden que el resto del sistema): una cancelación o postergación en curso gana (RF-9)
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public ResultadoValidacion validar(Long idPersonal, Long idEvento, String codigo) {
		LocalDateTime ahora = LocalDateTime.now(clock);
		Evento evento = eventoRepository.findConBloqueoCompartidoByIdEvento(idEvento)
				.orElseThrow(EventoNoEncontradoException::new);
		if (!evento.admiteValidacion(ahora)) {
			throw new EventoNoDisponibleException();
		}
		Optional<EntradaPorCodigo> porCodigo = entradaRepository.buscarPorCodigo(codigo.strip());
		if (porCodigo.isEmpty() || !porCodigo.get().idEvento().equals(idEvento)) {
			return ResultadoValidacion.noCorresponde();
		}
		Entrada entrada = entradaRepository.findConBloqueoByIdEntrada(porCodigo.get().idEntrada()).orElseThrow();
		if (entrada.getEstado() == EstadoEntrada.UTILIZADA) {
			return ResultadoValidacion.yaUtilizada();
		}
		if (!entrada.verificarVigencia(ahora)) {
			return ResultadoValidacion.noVigente();
		}
		entrada.marcarUtilizada();
		accesoRepository.save(Acceso.registrar(entrada, idPersonal, ahora));
		return ResultadoValidacion.valida(entrada.getTipoEntrada().getNombre());
	}
}
