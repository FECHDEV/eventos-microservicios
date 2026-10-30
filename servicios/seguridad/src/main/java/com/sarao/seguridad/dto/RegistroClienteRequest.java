package com.sarao.seguridad.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.sarao.seguridad.dto.validacion.MaximoBytesUtf8;
import com.sarao.seguridad.dto.validacion.ReglasContrasena;
import com.sarao.seguridad.entity.Usuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RegistroClienteRequest(

		@NotBlank(message = "es obligatorio")
		@Size(max = 80, message = "admite hasta 80 caracteres")
		String nombre,

		@NotBlank(message = "es obligatorio")
		@Size(max = 80, message = "admite hasta 80 caracteres")
		String apellido,

		@NotBlank(message = "es obligatorio")
		@Email(message = "no tiene formato de correo")
		@Size(max = 120, message = "admite hasta 120 caracteres")
		String correo,

		@NotNull(message = "es obligatoria")
		@Pattern(regexp = ReglasContrasena.PATRON, flags = Pattern.Flag.DOTALL, message = ReglasContrasena.MENSAJE)
		@MaximoBytesUtf8(value = 72, message = "admite hasta 72 bytes")
		String contrasena,

		@Size(max = 20, message = "admite hasta 20 caracteres")
		String telefono,

		@NotNull(message = "es obligatoria")
		@PastOrPresent(message = "no puede ser una fecha futura")
		LocalDate fechaNacimiento) {

	public RegistroClienteRequest {
		correo = correo == null ? null : Usuario.normalizarCorreo(correo);
		telefono = telefono == null || telefono.isBlank() ? null : telefono;
	}

	// La contraseña nunca llega a un registro
	@Override
	public String toString() {
		return "RegistroClienteRequest[correo=" + correo + "]";
	}
}
