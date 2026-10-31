package com.sarao.eventos.controller;

import java.time.Duration;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sarao.eventos.dto.EventoCatalogoResponse;
import com.sarao.eventos.dto.EventoCatalogoResumenResponse;
import com.sarao.eventos.dto.PaginaResponse;
import com.sarao.eventos.service.CatalogoService;
import com.sarao.eventos.service.ImagenEvento;
import lombok.RequiredArgsConstructor;

// CU-05. Público y solo de lectura (SecurityConfig): no hace falta sesión para consultar eventos
@RestController
@RequestMapping("/api/catalogo")
@RequiredArgsConstructor
public class CatalogoController {

	private static final Duration CACHE_DE_IMAGEN = Duration.ofMinutes(5);

	private final CatalogoService catalogoService;

	@GetMapping
	public PaginaResponse<EventoCatalogoResumenResponse> buscar(@RequestParam(required = false) String texto,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
			@RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "12") int tamano) {
		return PaginaResponse.from(catalogoService.buscar(texto, desde, hasta, pagina, tamano),
				EventoCatalogoResumenResponse::from);
	}

	@GetMapping("/{idEvento}")
	public EventoCatalogoResponse obtener(@PathVariable Long idEvento) {
		return EventoCatalogoResponse.from(catalogoService.obtener(idEvento));
	}

	@GetMapping("/{idEvento}/imagen")
	public ResponseEntity<byte[]> imagen(@PathVariable Long idEvento) {
		ImagenEvento imagen = catalogoService.leerImagen(idEvento);
		// Corto: un evento que deja de mostrarse tiene que dar 404 pronto (CU-05 RF-17)
		return ResponseEntity.ok().cacheControl(CacheControl.maxAge(CACHE_DE_IMAGEN).cachePublic())
				.contentType(MediaType.parseMediaType(imagen.formato().tipoDeContenido())).body(imagen.contenido());
	}
}
