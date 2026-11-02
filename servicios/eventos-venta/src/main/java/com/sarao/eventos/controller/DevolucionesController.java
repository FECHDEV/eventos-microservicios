package com.sarao.eventos.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sarao.eventos.dto.DevolucionesDelEventoResponse;
import com.sarao.eventos.dto.EventoCanceladoResponse;
import com.sarao.eventos.dto.RegistroDevolucionResponse;
import com.sarao.eventos.service.GestionDevolucionesService;
import lombok.RequiredArgsConstructor;

// CU-13. Solo el Administrador llega acá: /api/devoluciones/** en SecurityConfig
@RestController
@RequestMapping("/api/devoluciones")
@RequiredArgsConstructor
public class DevolucionesController {

	private final GestionDevolucionesService gestionDevolucionesService;

	@GetMapping
	public List<EventoCanceladoResponse> eventosCancelados() {
		return gestionDevolucionesService.eventosCancelados().stream().map(EventoCanceladoResponse::from).toList();
	}

	@GetMapping("/{idEvento}")
	public DevolucionesDelEventoResponse delEvento(@PathVariable Long idEvento) {
		return DevolucionesDelEventoResponse.from(gestionDevolucionesService.delEvento(idEvento));
	}

	@PostMapping("/compras/{idReserva}")
	public RegistroDevolucionResponse registrarCompra(@PathVariable Long idReserva) {
		return new RegistroDevolucionResponse(gestionDevolucionesService.registrarCompra(idReserva));
	}

	@PostMapping("/entradas/{idEntrada}")
	public RegistroDevolucionResponse registrarEntrada(@PathVariable Long idEntrada) {
		return new RegistroDevolucionResponse(gestionDevolucionesService.registrarEntrada(idEntrada));
	}
}
