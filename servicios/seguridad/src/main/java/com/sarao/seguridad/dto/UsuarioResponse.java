package com.sarao.seguridad.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.sarao.seguridad.entity.EstadoCuenta;
import com.sarao.seguridad.entity.Rol;
import com.sarao.seguridad.entity.Usuario;

public record UsuarioResponse(Long idUsuario, String nombre, String apellido, String correo, String telefono,
		LocalDate fechaNacimiento, Rol rol, EstadoCuenta estado, LocalDateTime fechaRegistro) {

	public static UsuarioResponse from(Usuario usuario) {
		return new UsuarioResponse(usuario.getIdUsuario(), usuario.getNombre(), usuario.getApellido(),
				usuario.getCorreo(), usuario.getTelefono(), usuario.getFechaNacimiento(), usuario.getRol(),
				usuario.getEstado(), usuario.getFechaRegistro());
	}
}
