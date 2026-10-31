package com.sarao.eventos.dto;

import java.util.List;

import com.sarao.eventos.service.EntradasDelCliente;

// CU-08 RF-5: «Próximos», «Pasados» y «Cancelados», ya ordenados
public record EntradasDelClienteResponse(List<CompraDelClienteResponse> proximas,
		List<CompraDelClienteResponse> pasadas, List<CompraDelClienteResponse> canceladas) {

	public static EntradasDelClienteResponse from(EntradasDelCliente entradas) {
		return new EntradasDelClienteResponse(
				entradas.proximas().stream().map(CompraDelClienteResponse::from).toList(),
				entradas.pasadas().stream().map(CompraDelClienteResponse::from).toList(),
				entradas.canceladas().stream().map(CompraDelClienteResponse::from).toList());
	}
}
