package com.sarao.seguridad.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.sarao.seguridad.dto.validacion.MaximoBytesUtf8;
import com.sarao.seguridad.dto.validacion.ReglasContrasena;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RestablecimientoContrasenaRequest(

		@NotBlank(message = "es obligatorio")
		@Size(max = 100, message = "admite hasta 100 caracteres")
		String token,

		@NotNull(message = "es obligatoria")
		@Pattern(regexp = ReglasContrasena.PATRON, flags = Pattern.Flag.DOTALL, message = ReglasContrasena.MENSAJE)
		@MaximoBytesUtf8(value = 72, message = "admite hasta 72 bytes")
		String contrasena) {

	@Override
	public String toString() {
		return "RestablecimientoContrasenaRequest[]";
	}
}
