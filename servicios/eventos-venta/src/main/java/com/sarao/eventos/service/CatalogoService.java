package com.sarao.eventos.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.exception.CamposInvalidosException;
import com.sarao.eventos.exception.CamposInvalidosException.CampoInvalido;
import com.sarao.eventos.exception.EventoNoEncontradoException;
import com.sarao.eventos.repository.EventoRepository;
import com.sarao.eventos.repository.TipoEntradaRepository;
import lombok.RequiredArgsConstructor;

// CU-05: el catálogo público, solo de lectura. Qué se muestra: decisiones.md → Catálogo de eventos (CU-05)
@Service
@RequiredArgsConstructor
public class CatalogoService {

	private static final int TAMANO_POR_DEFECTO = 12;
	private static final int TAMANO_MAXIMO = 48;
	private static final Sort ORDEN = Sort.by(Sort.Direction.ASC, "fecha", "horaInicio", "idEvento");
	// El lugar admite hasta 200 caracteres (EventoRequest): un texto más largo no puede coincidir con nada
	static final int LARGO_MAXIMO_TEXTO = 200;
	private static final LocalDate FECHA_MINIMA = LocalDate.of(1000, 1, 1);

	private final EventoRepository eventoRepository;
	private final TipoEntradaRepository tipoEntradaRepository;
	private final AlmacenImagenes almacenImagenes;
	private final Clock clock;

	@Transactional(readOnly = true)
	public Page<Evento> buscar(String texto, LocalDate desde, LocalDate hasta, int pagina, int tamano) {
		String limpio = texto == null ? "" : texto.strip();
		validar(limpio, desde, hasta);
		LocalDateTime ahora = LocalDateTime.now(clock);
		int tamanoValido = tamano < 1 ? TAMANO_POR_DEFECTO : Math.min(tamano, TAMANO_MAXIMO);
		// El offset tiene que entrar en un int: una página más alta también queda vacía, con los totales correctos
		PageRequest paginacion = PageRequest.of(Math.min(Math.max(pagina, 0), Integer.MAX_VALUE / tamanoValido),
				tamanoValido, ORDEN);
		return eventoRepository.buscarEnElCatalogo(Evento.ESTADOS_EN_CATALOGO, ahora.toLocalDate(), ahora.toLocalDate().minusDays(1),
				ahora.toLocalTime(), limpio.isEmpty() ? null : patron(limpio), desde, hasta, paginacion);
	}

	// shortcut: lee la disponible tal cual; con HU-15 libera antes las reservas vencidas (CU-06 RF-17 y RF-21)
	@Transactional(readOnly = true)
	public EventoDelCatalogo obtener(Long idEvento) {
		Evento evento = buscarVisible(idEvento);
		return new EventoDelCatalogo(evento, tipoEntradaRepository.findByEvento_IdEventoOrderByIdTipoEntrada(idEvento));
	}

	// Sin transacción: la conexión no queda tomada mientras se lee el archivo
	public ImagenEvento leerImagen(Long idEvento) {
		return GestionEventosService.leerImagenDe(almacenImagenes, buscarVisible(idEvento));
	}

	// Un evento que no se muestra responde igual que uno que no existe: el catálogo no revela borradores ni
	// cancelados (CU-05 RF-16)
	private Evento buscarVisible(Long idEvento) {
		return eventoRepository.findById(idEvento)
				.filter(evento -> evento.seMuestraEnElCatalogo(LocalDateTime.now(clock)))
				.orElseThrow(EventoNoEncontradoException::new);
	}

	private static void validar(String texto, LocalDate desde, LocalDate hasta) {
		List<CampoInvalido> errores = new ArrayList<>();
		if (texto.length() > LARGO_MAXIMO_TEXTO) {
			errores.add(new CampoInvalido("texto", "admite hasta 200 caracteres"));
		}
		// Una fecha que MySQL no guarda haría fallar la consulta
		if (fueraDeRango(desde)) {
			errores.add(new CampoInvalido("desde", "no es una fecha válida"));
		}
		if (fueraDeRango(hasta)) {
			errores.add(new CampoInvalido("hasta", "no es una fecha válida"));
		}
		if (desde != null && hasta != null && hasta.isBefore(desde)) {
			errores.add(new CampoInvalido("hasta", "no puede ser anterior a la fecha desde"));
		}
		if (!errores.isEmpty()) {
			throw new CamposInvalidosException(errores);
		}
	}

	private static boolean fueraDeRango(LocalDate fecha) {
		return fecha != null && (fecha.isBefore(FECHA_MINIMA) || fecha.isAfter(Evento.FECHA_MAXIMA));
	}

	// «contiene», sin distinguir mayúsculas. '%' y '_' del texto se buscan tal cual: se escapan con '!', el carácter
	// de escape de la consulta, que también se escapa a sí mismo
	private static String patron(String texto) {
		String escapado = texto.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
		return "%" + escapado + "%";
	}
}
