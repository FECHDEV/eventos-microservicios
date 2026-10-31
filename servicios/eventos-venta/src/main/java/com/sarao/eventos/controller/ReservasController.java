package com.sarao.eventos.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sarao.eventos.dto.ReservaRequest;
import com.sarao.eventos.dto.ReservaResponse;
import com.sarao.eventos.service.ConsultaDeClientes;
import com.sarao.eventos.service.ReservaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// CU-06. Solo el Cliente llega acá: /api/reservas/** en SecurityConfig. El Cliente sale de la sesión
// (IdentidadDelGatewayFiltro), nunca de la petición
@RestController
@RequestMapping("/api/reservas")
@RequiredArgsConstructor
public class ReservasController {

	private final ReservaService reservaService;
	private final ConsultaDeClientes consultaDeClientes;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ReservaResponse reservar(@AuthenticationPrincipal Long idUsuario,
			@Valid @RequestBody ReservaRequest solicitud) {
		// Antes de la transacción: la llamada a Seguridad no puede esperar con el evento bloqueado
		return ReservaResponse.from(
				reservaService.reservar(idUsuario, consultaDeClientes.fechaNacimiento(idUsuario), solicitud));
	}

	@GetMapping("/{idReserva}")
	public ReservaResponse consultar(@AuthenticationPrincipal Long idUsuario, @PathVariable Long idReserva) {
		return ReservaResponse.from(reservaService.consultar(idUsuario, idReserva));
	}
}
