package com.sarao.seguridad.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.sarao.seguridad.dto.validacion.MaximoBytesUtf8;
import com.sarao.seguridad.dto.validacion.ReglasContrasena;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CambioContrasenaRequest(

		@NotBlank(message = "es obligatoria")
		@Size(max = 1000, message = "admite hasta 1000 caracteres")
		String contrasenaActual,

		@NotNull(message = "es obligatoria")
		@Pattern(regexp = ReglasContrasena.PATRON, flags = Pattern.Flag.DOTALL, message = ReglasContrasena.MENSAJE)
		@MaximoBytesUtf8(value = 72, message = "admite hasta 72 bytes")
		String contrasenaNueva) {

	@Override
	public String toString() {
		return "CambioContrasenaRequest[]";
	}
}
