package com.sarao.eventos;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

// Hace lo que el gateway: agrega la identidad de la sesión a la petición
public final class SesionDelGateway {

	private SesionDelGateway() {
	}

	public static RequestPostProcessor administrador() {
		return sesion(1L, "ADMINISTRADOR");
	}

	public static RequestPostProcessor cliente() {
		return sesion(7L, "CLIENTE");
	}

	private static RequestPostProcessor sesion(Long idUsuario, String rol) {
		return peticion -> {
			peticion.addHeader("X-Clave-Servicio", "clave-de-pruebas");
			peticion.addHeader("X-Usuario-Id", idUsuario.toString());
			peticion.addHeader("X-Usuario-Rol", rol);
			return peticion;
		};
	}
}
