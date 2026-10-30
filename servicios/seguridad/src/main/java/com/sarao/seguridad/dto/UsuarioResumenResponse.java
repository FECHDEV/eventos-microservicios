package com.sarao.seguridad.dto;

import java.time.LocalDateTime;

import com.sarao.seguridad.entity.EstadoCuenta;
import com.sarao.seguridad.entity.Rol;
import com.sarao.seguridad.entity.Usuario;

// Lo necesario para identificar la cuenta y decidir, sin teléfono ni fecha de nacimiento (RNF-03)
public record UsuarioResumenResponse(Long idUsuario, String nombre, String apellido, String correo, Rol rol,
		EstadoCuenta estado, LocalDateTime fechaRegistro) {

	public static UsuarioResumenResponse from(Usuario usuario) {
		return new UsuarioResumenResponse(usuario.getIdUsuario(), usuario.getNombre(), usuario.getApellido(),
				usuario.getCorreo(), usuario.getRol(), usuario.getEstado(), usuario.getFechaRegistro());
	}
}
