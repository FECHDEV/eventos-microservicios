package com.sarao.pagos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// La cuenta de Libélula (decisiones.md → Adaptador de Libélula). La appkey llega solo del .env; sin ella, o sin el
// correo que Libélula exige en cada deuda, la aplicación no arranca con este proveedor. La URL del sitio arma el aviso
// de pago y la vuelta del cliente a su reserva
@Validated
@ConfigurationProperties("eventos.pago.libelula")
public record LibelulaProperties(@NotBlank String url, @NotBlank String appkey, @NotBlank @Email String email,
		@NotBlank String urlDelSitio) {

	public LibelulaProperties {
		// Sin la barra final, para que las rutas no queden con dos
		url = sinBarraFinal(url);
		urlDelSitio = sinBarraFinal(urlDelSitio);
	}

	private static String sinBarraFinal(String valor) {
		return valor == null ? null : valor.replaceAll("/+$", "");
	}

	// La appkey nunca sale en el registro ni en un mensaje de error
	@Override
	public String toString() {
		return "LibelulaProperties[url=" + url + ", email=" + email + ", urlDelSitio=" + urlDelSitio + "]";
	}
}
