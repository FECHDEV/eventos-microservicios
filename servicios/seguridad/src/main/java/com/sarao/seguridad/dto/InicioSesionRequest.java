package com.sarao.seguridad.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.sarao.seguridad.entity.Usuario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InicioSesionRequest(

		@NotBlank(message = "es obligatorio")
		@Size(max = 120, message = "admite hasta 120 caracteres")
		String correo,

		@NotBlank(message = "es obligatoria")
		// Más de 72 bytes da credenciales incorrectas (CU-02 RF-13); esto solo acota lo que llega a BCrypt
		@Size(max = 1000, message = "admite hasta 1000 caracteres")
		String contrasena) {

	public InicioSesionRequest {
		correo = correo == null ? null : Usuario.normalizarCorreo(correo);
	}

	@Override
	public String toString() {
		return "InicioSesionRequest[correo=" + correo + "]";
	}
}
