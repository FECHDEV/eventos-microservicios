package com.sarao.eventos.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sarao.eventos.dto.EntradaResponse;
import com.sarao.eventos.dto.EntradasDelClienteResponse;
import com.sarao.eventos.service.ConsultaDeEntradas;
import lombok.RequiredArgsConstructor;

// CU-08. Solo el Cliente llega acá: /api/entradas/** en SecurityConfig. El Cliente sale de la sesión
// (IdentidadDelGatewayFiltro), nunca de la petición
@RestController
@RequestMapping("/api/entradas")
@RequiredArgsConstructor
public class EntradasController {

	private final ConsultaDeEntradas consultaDeEntradas;

	@GetMapping
	public EntradasDelClienteResponse listar(@AuthenticationPrincipal Long idUsuario) {
		return EntradasDelClienteResponse.from(consultaDeEntradas.delCliente(idUsuario));
	}

	@GetMapping("/{idEntrada}")
	public EntradaResponse consultar(@AuthenticationPrincipal Long idUsuario, @PathVariable Long idEntrada) {
		return EntradaResponse.from(consultaDeEntradas.detalle(idUsuario, idEntrada));
	}
}
