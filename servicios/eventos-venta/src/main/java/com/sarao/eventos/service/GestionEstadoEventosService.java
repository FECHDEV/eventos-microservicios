package com.sarao.eventos.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sarao.eventos.dto.PostergacionRequest;
import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.exception.CambioDeEstadoNoPermitidoException;
import com.sarao.eventos.exception.CamposInvalidosException;
import com.sarao.eventos.exception.CamposInvalidosException.CampoInvalido;
import com.sarao.eventos.exception.EventoSinTiposDeEntradaException;
import com.sarao.eventos.exception.VentaYaCerradaException;
import com.sarao.eventos.repository.EventoRepository;
import com.sarao.eventos.repository.TipoEntradaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// CU-12: solo el Administrador llega acá (SecurityConfig). Reglas en decisiones.md → Gestión de Eventos
@Slf4j
@Service
@RequiredArgsConstructor
public class GestionEstadoEventosService {

	private final EventoRepository eventoRepository;
	private final TipoEntradaRepository tipoEntradaRepository;
	private final Clock clock;

	// shortcut: informa 0 entradas reactivadas, porque no hay entradas; FA-02 se agrega con HU-16 (RF-26 a RF-32)
	@Transactional
	public EventoActivado activar(Long idEvento) {
		Evento evento = GestionEventosService.buscarEditable(eventoRepository, idEvento);
		if (!evento.puedeActivarse()) {
			throw new CambioDeEstadoNoPermitidoException();
		}
		// Un postergado ya los tenía para llegar a ese estado (RF-7)
		if (evento.esBorrador() && !tipoEntradaRepository.existsByEvento_IdEvento(idEvento)) {
			throw new EventoSinTiposDeEntradaException();
		}
		evento.activar();
		return new EventoActivado(evento, 0);
	}

	@Transactional
	public Evento postergar(Long idEvento, PostergacionRequest solicitud) {
		Evento evento = GestionEventosService.buscarEditable(eventoRepository, idEvento);
		if (!evento.puedePostergarse()) {
			throw new CambioDeEstadoNoPermitidoException();
		}
		validarFechaNueva(evento, solicitud.fecha());
		evento.postergar(solicitud.fecha());
		return evento;
	}

	@Transactional
	public Evento cancelar(Long idEvento) {
		Evento evento = GestionEventosService.buscarEditable(eventoRepository, idEvento);
		evento.cancelar();
		return evento;
	}

	@Transactional
	public Evento cerrarVenta(Long idEvento) {
		Evento evento = GestionEventosService.buscarEditable(eventoRepository, idEvento);
		if (!evento.isVentaAbierta()) {
			throw new VentaYaCerradaException();
		}
		evento.cerrarVenta();
		return evento;
	}

	// FA-01: el actor Tiempo. Solo cierra la venta; el estado no cambia (RF-23)
	@Transactional
	public int cerrarVentasVencidas() {
		LocalDateTime ahora = LocalDateTime.now(clock);
		int cerrados = 0;
		// Cada candidato se relee con bloqueo (RF-24): entre la consulta y el cierre el administrador pudo postergarlo
		// o cambiarle la imagen, y sin el bloqueo este cierre reescribiría esas columnas con los valores viejos
		for (Long idEvento : eventoRepository.idsConLaVentaAbiertaHasta(ahora.toLocalDate())) {
			Evento evento = eventoRepository.findConBloqueoByIdEvento(idEvento).orElse(null);
			if (evento != null && evento.isVentaAbierta() && evento.termino(ahora)) {
				evento.cerrarVenta();
				cerrados++;
				log.info("Venta cerrada automáticamente para el evento {}", idEvento);
			}
		}
		return cerrados;
	}

	// Hoy se acepta; la fecha que el evento ya tiene, no: eso no es postergar
	private void validarFechaNueva(Evento evento, LocalDate nueva) {
		if (nueva.isBefore(LocalDate.now(clock))) {
			throw new CamposInvalidosException(List.of(new CampoInvalido("fecha", "no puede ser anterior a hoy")));
		}
		if (nueva.isAfter(Evento.FECHA_MAXIMA)) {
			throw new CamposInvalidosException(List.of(new CampoInvalido("fecha", "no es una fecha válida")));
		}
		if (nueva.equals(evento.getFecha())) {
			throw new CamposInvalidosException(
					List.of(new CampoInvalido("fecha", "debe ser distinta de la fecha actual del evento")));
		}
	}
}
