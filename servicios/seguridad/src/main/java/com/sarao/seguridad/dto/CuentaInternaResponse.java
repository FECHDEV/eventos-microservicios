package com.sarao.seguridad.dto;

import java.time.LocalDateTime;

import com.sarao.seguridad.entity.EstadoCuenta;
import com.sarao.seguridad.entity.Usuario;

// CU-14: lo que los reportes de Eventos y Venta muestran de una cuenta. Sin la contraseña ni la fecha de nacimiento
public record CuentaInternaResponse(Long idUsuario, String nombre, String apellido, String correo, String telefono,
		LocalDateTime fechaRegistro, EstadoCuenta estado) {

	public static CuentaInternaResponse from(Usuario usuario) {
		return new CuentaInternaResponse(usuario.getIdUsuario(), usuario.getNombre(), usuario.getApellido(),
				usuario.getCorreo(), usuario.getTelefono(), usuario.getFechaRegistro(), usuario.getEstado());
	}
}
