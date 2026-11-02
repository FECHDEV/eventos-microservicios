package com.sarao.seguridad.dto;

import com.sarao.seguridad.entity.Usuario;

public record ContactoResponse(String correo, String nombre) {

	public static ContactoResponse from(Usuario usuario) {
		return new ContactoResponse(usuario.getCorreo(), usuario.getNombre());
	}
}
