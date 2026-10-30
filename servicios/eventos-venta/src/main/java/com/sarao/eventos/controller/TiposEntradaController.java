package com.sarao.eventos.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sarao.eventos.dto.TipoEntradaRequest;
import com.sarao.eventos.dto.TipoEntradaResponse;
import com.sarao.eventos.service.GestionTiposEntradaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// CU-11. Solo el Administrador llega acá: /api/eventos/** en SecurityConfig
@RestController
@RequestMapping("/api/eventos/{idEvento}/tipos-entrada")
@RequiredArgsConstructor
public class TiposEntradaController {

	private final GestionTiposEntradaService gestionTiposEntradaService;

	@GetMapping
	public List<TipoEntradaResponse> listar(@PathVariable Long idEvento) {
		return gestionTiposEntradaService.listar(idEvento).stream().map(TipoEntradaResponse::from).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TipoEntradaResponse registrar(@PathVariable Long idEvento, @Valid @RequestBody TipoEntradaRequest solicitud) {
		return TipoEntradaResponse.from(gestionTiposEntradaService.registrar(idEvento, solicitud));
	}

	@PutMapping("/{idTipoEntrada}")
	public TipoEntradaResponse modificar(@PathVariable Long idEvento, @PathVariable Long idTipoEntrada,
			@Valid @RequestBody TipoEntradaRequest solicitud) {
		return TipoEntradaResponse.from(gestionTiposEntradaService.modificar(idEvento, idTipoEntrada, solicitud));
	}
}
