package com.sarao.notificaciones.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

// Remitente de los correos, dirección pública del sitio para armar los enlaces y dominio propio, el único al que se
// mandan los reportes (CU-14). Sin ellos la aplicación no arranca
@Validated
@ConfigurationProperties("eventos.correo")
public record CorreoProperties(@NotBlank String remitente, @NotBlank String urlDelSitio, @NotBlank String dominio) {

	public CorreoProperties {
		// Sin la barra final, para que los enlaces no queden con dos
		urlDelSitio = urlDelSitio == null ? null : urlDelSitio.replaceAll("/+$", "");
	}
}
