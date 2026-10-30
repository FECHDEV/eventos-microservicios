package com.sarao.eventos.controller;

import java.io.IOException;
import java.net.URI;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sarao.eventos.dto.EventoRequest;
import com.sarao.eventos.dto.EventoResponse;
import com.sarao.eventos.dto.EventoResumenResponse;
import com.sarao.eventos.dto.PaginaResponse;
import com.sarao.eventos.entity.EstadoEvento;
import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.service.GestionEventosService;
import com.sarao.eventos.service.ImagenEvento;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// CU-10. Solo el Administrador llega acá (SecurityConfig); quien registra sale de la sesión
@RestController
@RequestMapping("/api/eventos")
@RequiredArgsConstructor
public class EventosController {

	private final GestionEventosService gestionEventosService;

	@GetMapping
	public PaginaResponse<EventoResumenResponse> listar(@RequestParam(required = false) EstadoEvento estado,
			@RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamano) {
		return PaginaResponse.from(gestionEventosService.listar(estado, pagina, tamano), EventoResumenResponse::from);
	}

	@GetMapping("/{idEvento}")
	public EventoResponse obtener(@PathVariable Long idEvento) {
		return EventoResponse.from(gestionEventosService.obtener(idEvento));
	}

	@PostMapping
	public ResponseEntity<EventoResponse> registrar(@AuthenticationPrincipal Long idAdministrador,
			@Valid @RequestBody EventoRequest solicitud) {
		Evento evento = gestionEventosService.registrar(idAdministrador, solicitud);
		return ResponseEntity.created(URI.create("/api/eventos/" + evento.getIdEvento()))
				.body(EventoResponse.from(evento));
	}

	@PutMapping("/{idEvento}")
	public EventoResponse modificar(@PathVariable Long idEvento, @Valid @RequestBody EventoRequest solicitud) {
		return EventoResponse.from(gestionEventosService.modificar(idEvento, solicitud));
	}

	// El nombre y el tipo que declara el archivo se descartan: el formato sale de su contenido (RF-23, RF-29)
	@PostMapping(path = "/{idEvento}/imagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public EventoResponse cambiarImagen(@PathVariable Long idEvento, @RequestPart("imagen") MultipartFile imagen)
			throws IOException {
		return EventoResponse.from(gestionEventosService.cambiarImagen(idEvento, imagen.getBytes()));
	}

	@DeleteMapping("/{idEvento}/imagen")
	public EventoResponse quitarImagen(@PathVariable Long idEvento) {
		return EventoResponse.from(gestionEventosService.quitarImagen(idEvento));
	}

	@GetMapping("/{idEvento}/imagen")
	public ResponseEntity<byte[]> imagen(@PathVariable Long idEvento) {
		ImagenEvento imagen = gestionEventosService.leerImagen(idEvento);
		return ResponseEntity.ok().contentType(MediaType.parseMediaType(imagen.formato().tipoDeContenido()))
				.body(imagen.contenido());
	}
}
