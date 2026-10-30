package com.sarao.eventos.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.sarao.eventos.dto.EventoRequest;
import com.sarao.eventos.entity.EstadoEvento;
import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.exception.CamposInvalidosException;
import com.sarao.eventos.exception.CamposInvalidosException.CampoInvalido;
import com.sarao.eventos.exception.EventoCanceladoException;
import com.sarao.eventos.exception.EventoNoEncontradoException;
import com.sarao.eventos.exception.FechaNoModificableException;
import com.sarao.eventos.exception.ImagenDemasiadoGrandeException;
import com.sarao.eventos.exception.ImagenInvalidaException;
import com.sarao.eventos.exception.ImagenNoEncontradaException;
import com.sarao.eventos.repository.EventoRepository;
import lombok.RequiredArgsConstructor;

// CU-10: solo el Administrador llega acá (SecurityConfig). Reglas en decisiones.md → Gestión de Eventos
@Service
@RequiredArgsConstructor
public class GestionEventosService {

	private static final int TAMANO_POR_DEFECTO = 20;
	private static final int TAMANO_MAXIMO = 100;
	private static final Sort ORDEN = Sort.by(Sort.Direction.ASC, "fecha", "horaInicio", "idEvento");
	// decisiones.md → Imagen del evento. El límite de multipart (application.properties) es el mismo: esta comprobación
	// cubre al servicio aunque alguien cambie esa propiedad
	static final int BYTES_MAXIMOS_IMAGEN = 2 * 1024 * 1024;

	private final EventoRepository eventoRepository;
	private final AlmacenImagenes almacenImagenes;
	private final Clock clock;

	@Transactional(readOnly = true)
	public Page<Evento> listar(EstadoEvento estado, int pagina, int tamano) {
		// Igual que CU-09: una página negativa es la primera y un tamaño inválido, el por defecto
		int tamanoValido = tamano < 1 ? TAMANO_POR_DEFECTO : Math.min(tamano, TAMANO_MAXIMO);
		// El offset tiene que entrar en un int: una página más alta también queda vacía, con los totales correctos
		int paginaValida = Math.min(Math.max(pagina, 0), Integer.MAX_VALUE / tamanoValido);
		return eventoRepository.buscar(estado, PageRequest.of(paginaValida, tamanoValido, ORDEN));
	}

	@Transactional(readOnly = true)
	public Evento obtener(Long idEvento) {
		return buscar(idEvento);
	}

	@Transactional
	public Evento registrar(Long idAdministrador, EventoRequest solicitud) {
		validarHorario(solicitud, true);
		Evento evento = Evento.registrar(idAdministrador, solicitud.nombre(),
				solicitud.descripcion(), solicitud.fecha(), solicitud.horaInicio(), solicitud.horaFin(),
				solicitud.lugar(), solicitud.restriccionEdad(), solicitud.limiteCompra());
		return eventoRepository.save(evento);
	}

	// Primero el estado, después los datos: un cancelado o un cambio de fecha no permitido no llega a validarse
	@Transactional
	public Evento modificar(Long idEvento, EventoRequest solicitud) {
		Evento evento = buscarEditable(eventoRepository, idEvento);
		if (evento.cambiaElHorario(solicitud.fecha(), solicitud.horaInicio(), solicitud.horaFin())
				&& !evento.permiteCambiarElHorario()) {
			throw new FechaNoModificableException();
		}
		validarHorario(solicitud, !solicitud.fecha().equals(evento.getFecha()));
		evento.actualizar(solicitud.nombre(), solicitud.descripcion(), solicitud.fecha(), solicitud.horaInicio(),
				solicitud.horaFin(), solicitud.lugar(), solicitud.restriccionEdad(), solicitud.limiteCompra());
		return evento;
	}

	@Transactional
	public Evento cambiarImagen(Long idEvento, byte[] contenido) {
		Evento evento = buscarEditable(eventoRepository, idEvento);
		if (contenido.length > BYTES_MAXIMOS_IMAGEN) {
			throw new ImagenDemasiadoGrandeException();
		}
		FormatoImagen formato = FormatoImagen.detectar(contenido).orElseThrow(ImagenInvalidaException::new);
		String nueva = almacenImagenes.guardar(contenido, formato);
		String anterior = evento.cambiarImagen(nueva);
		borrarAlTerminar(anterior, nueva);
		return evento;
	}

	@Transactional
	public Evento quitarImagen(Long idEvento) {
		Evento evento = buscarEditable(eventoRepository, idEvento);
		String anterior = evento.quitarImagen();
		borrarAlTerminar(anterior, null);
		return evento;
	}

	@Transactional(readOnly = true)
	public ImagenEvento leerImagen(Long idEvento) {
		return leerImagenDe(almacenImagenes, buscar(idEvento));
	}

	// La imagen de un evento ya encontrado
	static ImagenEvento leerImagenDe(AlmacenImagenes almacenImagenes, Evento evento) {
		String nombre = evento.getImagen();
		if (nombre == null) {
			throw new ImagenNoEncontradaException();
		}
		byte[] contenido = almacenImagenes.leer(nombre).orElseThrow(ImagenNoEncontradaException::new);
		return new ImagenEvento(contenido, FormatoImagen.deArchivo(nombre));
	}

	// Para escribir un evento (CU-10, CU-11 y CU-12): toma la fila con bloqueo, así dos cambios del mismo evento se
	// esperan en vez de pisarse, y rechaza el cancelado, que ya no se toca. Quien además bloquee un tipo de entrada lo
	// hace después de esto: el orden es evento y después tipo
	static Evento buscarEditable(EventoRepository eventoRepository, Long idEvento) {
		Evento evento = eventoRepository.findConBloqueoByIdEvento(idEvento)
				.orElseThrow(EventoNoEncontradoException::new);
		if (evento.estaCancelado()) {
			throw new EventoCanceladoException();
		}
		return evento;
	}

	// El archivo sobrante depende de cómo termina la transacción: si se confirma, sobra el que el evento dejó de usar;
	// si se revierte, el evento sigue con el anterior y sobra el recién guardado. Borrar antes de saberlo, o con un final
	// desconocido, podría dejar al evento apuntando a un archivo que ya no existe
	private void borrarAlTerminar(String siSeConfirma, String siSeRevierte) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int estado) {
				String sobrante = switch (estado) {
					case STATUS_COMMITTED -> siSeConfirma;
					case STATUS_ROLLED_BACK -> siSeRevierte;
					default -> null;
				};
				if (sobrante != null) {
					almacenImagenes.borrar(sobrante);
				}
			}
		});
	}

	private Evento buscar(Long idEvento) {
		return eventoRepository.findById(idEvento).orElseThrow(EventoNoEncontradoException::new);
	}

	// La fecha contra hoy solo cuando se elige una fecha (RF-10, RF-21); las horas siempre (RF-11). Se juntan los dos
	// errores para indicar todos los campos a corregir de una vez (FA-01)
	private void validarHorario(EventoRequest solicitud, boolean validarFecha) {
		List<CampoInvalido> errores = new ArrayList<>();
		if (validarFecha && solicitud.fecha().isBefore(LocalDate.now(clock))) {
			errores.add(new CampoInvalido("fecha", "no puede ser anterior a hoy"));
		}
		if (validarFecha && solicitud.fecha().isAfter(Evento.FECHA_MAXIMA)) {
			errores.add(new CampoInvalido("fecha", "no es una fecha válida"));
		}
		if (solicitud.horaFin().equals(solicitud.horaInicio())) {
			errores.add(new CampoInvalido("horaFin", "debe ser distinta de la hora de inicio"));
		}
		if (!errores.isEmpty()) {
			throw new CamposInvalidosException(errores);
		}
	}
}
