package com.sarao.notificaciones.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

// Remitente de los correos y dirección pública del sitio, para armar los enlaces. Sin la URL, los enlaces quedarían sin
// sitio: la aplicación no arranca
@Validated
@ConfigurationProperties("eventos.correo")
public record CorreoProperties(@NotBlank String remitente, @NotBlank String urlDelSitio) {

	public CorreoProperties {
		// Sin la barra final, para que los enlaces no queden con dos
		urlDelSitio = urlDelSitio == null ? null : urlDelSitio.replaceAll("/+$", "");
	}
}
