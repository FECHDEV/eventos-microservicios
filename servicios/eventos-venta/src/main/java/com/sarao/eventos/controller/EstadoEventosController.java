package com.sarao.eventos.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sarao.eventos.dto.ActivacionResponse;
import com.sarao.eventos.dto.EventoResponse;
import com.sarao.eventos.dto.PostergacionRequest;
import com.sarao.eventos.service.GestionEstadoEventosService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// CU-12. Solo el Administrador llega acá: /api/eventos/** en SecurityConfig
@RestController
@RequestMapping("/api/eventos/{idEvento}")
@RequiredArgsConstructor
public class EstadoEventosController {

	private final GestionEstadoEventosService gestionEstadoEventosService;

	@PostMapping("/activacion")
	public ActivacionResponse activar(@PathVariable Long idEvento) {
		return ActivacionResponse.from(gestionEstadoEventosService.activar(idEvento));
	}

	@PostMapping("/postergacion")
	public EventoResponse postergar(@PathVariable Long idEvento,
			@Valid @RequestBody PostergacionRequest solicitud) {
		return EventoResponse.from(gestionEstadoEventosService.postergar(idEvento, solicitud));
	}

	@PostMapping("/cancelacion")
	public EventoResponse cancelar(@PathVariable Long idEvento) {
		return EventoResponse.from(gestionEstadoEventosService.cancelar(idEvento));
	}

	@PostMapping("/cierre-de-venta")
	public EventoResponse cerrarVenta(@PathVariable Long idEvento) {
		return EventoResponse.from(gestionEstadoEventosService.cerrarVenta(idEvento));
	}
}
