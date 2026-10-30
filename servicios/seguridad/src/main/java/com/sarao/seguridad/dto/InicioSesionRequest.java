package com.sarao.seguridad.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.sarao.seguridad.entity.Usuario;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InicioSesionRequest(

		@NotBlank(message = "es obligatorio")
		String correo,

		@NotBlank(message = "es obligatoria")
		String contrasena) {

	public InicioSesionRequest {
		correo = correo == null ? null : Usuario.normalizarCorreo(correo);
	}
}
