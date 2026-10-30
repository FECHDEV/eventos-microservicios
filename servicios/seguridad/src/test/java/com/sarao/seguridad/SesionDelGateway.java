package com.sarao.seguridad;

import java.io.UnsupportedEncodingException;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;

// Hace lo que el gateway: resuelve el token con /interno/sesiones y agrega la identidad a la petición
public final class SesionDelGateway {

	private static final String CLAVE = "clave-de-pruebas";

	private SesionDelGateway() {
	}

	public static RequestPostProcessor sesion(MockMvcTester mvc, String token) {
		MvcTestResult identidad = mvc.post().uri("/interno/sesiones").header("X-Clave-Servicio", CLAVE)
				.contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"%s\"}".formatted(token)).exchange();
		return peticion -> {
			if (identidad.getResponse().getStatus() == 200) {
				try {
					String cuerpo = identidad.getResponse().getContentAsString();
					peticion.addHeader("X-Clave-Servicio", CLAVE);
					peticion.addHeader("X-Usuario-Id", JsonPath.read(cuerpo, "$.idUsuario").toString());
					peticion.addHeader("X-Usuario-Rol", JsonPath.read(cuerpo, "$.rol"));
				} catch (UnsupportedEncodingException e) {
					throw new IllegalStateException(e);
				}
			}
			return peticion;
		};
	}
}
