package com.sarao.seguridad.dto;

import java.time.Instant;

import com.sarao.seguridad.service.SesionIniciada;

public record InicioSesionResponse(String token, String tipo, Instant expiracion, UsuarioResponse usuario) {

	public static InicioSesionResponse from(SesionIniciada sesion) {
		return new InicioSesionResponse(sesion.token().token(), "Bearer", sesion.token().expiracion(),
				UsuarioResponse.from(sesion.usuario()));
	}
}
