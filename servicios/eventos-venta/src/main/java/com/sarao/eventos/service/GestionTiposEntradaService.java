package com.sarao.eventos.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sarao.eventos.dto.TipoEntradaRequest;
import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.entity.TipoEntrada;
import com.sarao.eventos.exception.CamposInvalidosException;
import com.sarao.eventos.exception.CamposInvalidosException.CampoInvalido;
import com.sarao.eventos.exception.EventoNoEncontradoException;
import com.sarao.eventos.exception.TipoEntradaDuplicadoException;
import com.sarao.eventos.exception.TipoEntradaNoEncontradoException;
import com.sarao.eventos.repository.EventoRepository;
import com.sarao.eventos.repository.TipoEntradaRepository;
import lombok.RequiredArgsConstructor;

// CU-11: solo el Administrador llega acá (SecurityConfig). Reglas en decisiones.md → Gestión de Eventos
@Service
@RequiredArgsConstructor
public class GestionTiposEntradaService {

	private final EventoRepository eventoRepository;
	private final TipoEntradaRepository tipoEntradaRepository;

	// En cualquier estado del evento, también cancelado (RF-5)
	// shortcut: no libera las reservas vencidas, porque no hay reservas; las libera con HU-15 (CU-06 RF-17)
	@Transactional(readOnly = true)
	public List<TipoEntrada> listar(Long idEvento) {
		if (!eventoRepository.existsById(idEvento)) {
			throw new EventoNoEncontradoException();
		}
		return tipoEntradaRepository.findByEvento_IdEventoOrderByIdTipoEntrada(idEvento);
	}

	@Transactional
	public TipoEntrada registrar(Long idEvento, TipoEntradaRequest solicitud) {
		Evento evento = GestionEventosService.buscarEditable(eventoRepository, idEvento);
		String nombre = solicitud.nombre().strip();
		if (tipoEntradaRepository.existsByEvento_IdEventoAndNombreIgnoreCase(idEvento, nombre)) {
			throw new TipoEntradaDuplicadoException();
		}
		return tipoEntradaRepository.save(
				TipoEntrada.registrar(evento, nombre, solicitud.precio(), solicitud.cantidadTotal()));
	}

	// Primero el evento, después el tipo y al final los datos: un cancelado no llega a buscar el tipo
	@Transactional
	public TipoEntrada modificar(Long idEvento, Long idTipoEntrada, TipoEntradaRequest solicitud) {
		GestionEventosService.buscarEditable(eventoRepository, idEvento);
		// shortcut: el mínimo todavía incluye las reservas vencidas; las libera acá con HU-15 (CU-06 RF-17)
		TipoEntrada tipo = tipoEntradaRepository.findConBloqueoByIdTipoEntradaAndEvento_IdEvento(idTipoEntrada, idEvento)
				.orElseThrow(TipoEntradaNoEncontradoException::new);
		String nombre = solicitud.nombre().strip();
		if (tipoEntradaRepository.existsByEvento_IdEventoAndNombreIgnoreCaseAndIdTipoEntradaNot(idEvento, nombre,
				idTipoEntrada)) {
			throw new TipoEntradaDuplicadoException();
		}
		int minimo = tipo.cantidadComprometida();
		if (solicitud.cantidadTotal() < minimo) {
			throw new CamposInvalidosException(List.of(new CampoInvalido("cantidadTotal",
					"no puede ser menor que " + minimo + ", lo ya vendido y reservado")));
		}
		tipo.actualizar(nombre, solicitud.precio(), solicitud.cantidadTotal());
		return tipo;
	}
}
