package com.sarao.eventos.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sarao.eventos.dto.EventoParaValidarResponse;
import com.sarao.eventos.dto.ResultadoValidacionResponse;
import com.sarao.eventos.dto.ValidacionRequest;
import com.sarao.eventos.service.ValidacionDeEntradas;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// CU-15. Solo el Personal autorizado llega acá: /api/accesos/** en SecurityConfig. El Personal sale de la sesión
// (IdentidadDelGatewayFiltro), nunca de la petición
@RestController
@RequestMapping("/api/accesos")
@RequiredArgsConstructor
public class AccesosController {

	private final ValidacionDeEntradas validacionDeEntradas;

	@GetMapping("/eventos")
	public List<EventoParaValidarResponse> eventosParaValidar() {
		return validacionDeEntradas.eventosParaValidar().stream().map(EventoParaValidarResponse::from).toList();
	}

	// Un rechazo (FA-01 a FA-03) es un resultado: responde 200 con él
	@PostMapping
	public ResultadoValidacionResponse validar(@AuthenticationPrincipal Long idUsuario,
			@Valid @RequestBody ValidacionRequest solicitud) {
		return ResultadoValidacionResponse
				.from(validacionDeEntradas.validar(idUsuario, solicitud.idEvento(), solicitud.codigo()));
	}
}
