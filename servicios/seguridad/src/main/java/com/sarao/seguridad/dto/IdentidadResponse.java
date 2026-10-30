package com.sarao.seguridad.dto;

import com.sarao.seguridad.entity.Rol;
import com.sarao.seguridad.entity.Usuario;

public record IdentidadResponse(Long idUsuario, Rol rol) {

	public static IdentidadResponse from(Usuario usuario) {
		return new IdentidadResponse(usuario.getIdUsuario(), usuario.getRol());
	}
}
