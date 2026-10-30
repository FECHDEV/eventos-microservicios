package com.sarao.seguridad.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.sarao.seguridad.entity.Usuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SolicitudRecuperacionRequest(

		@NotBlank(message = "es obligatorio")
		@Email(message = "no tiene formato de correo")
		@Size(max = 120, message = "admite hasta 120 caracteres")
		String correo) {

	public SolicitudRecuperacionRequest {
		correo = correo == null ? null : Usuario.normalizarCorreo(correo);
	}
}
