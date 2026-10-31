package com.sarao.gateway;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.function.ServerRequest;

// Resuelve el token con Seguridad, la unica que tiene la clave JWT, y pasa la identidad a los servicios. Un token invalido
// no corta la peticion: va sin identidad y las rutas protegidas dan 401. Si Seguridad falla, 503: un 401 cerraria la
// sesion de un usuario con token vigente
@Component
public class IdentidadDeSesion {

	static final String CLAVE = "X-Clave-Servicio";
	static final String ID_USUARIO = "X-Usuario-Id";
	static final String ROL = "X-Usuario-Rol";

	private static final Logger log = LoggerFactory.getLogger(IdentidadDeSesion.class);
	private static final String PREFIJO = "Bearer ";
	private static final Duration PLAZO_CONEXION = Duration.ofSeconds(1);
	private static final Duration PLAZO_RESPUESTA = Duration.ofSeconds(2);

	private final RestClient seguridad;
	private final String claveServicio;

	public IdentidadDeSesion(@Value("${eventos.seguridad-url}") String urlSeguridad,
			@Value("${eventos.clave-servicio}") String claveServicio) {
		var conexiones = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(PLAZO_CONEXION).build();
		var fabrica = new JdkClientHttpRequestFactory(conexiones);
		fabrica.setReadTimeout(PLAZO_RESPUESTA);
		this.seguridad = RestClient.builder().baseUrl(urlSeguridad).requestFactory(fabrica).build();
		this.claveServicio = claveServicio;
	}

	ServerRequest aplicar(ServerRequest peticion) {
		Optional<Identidad> identidad = token(peticion).flatMap(this::consultar);
		ServerRequest anonima = sinIdentidad(peticion);
		return identidad.map(usuario -> ServerRequest.from(anonima).headers(encabezados -> {
			encabezados.set(CLAVE, claveServicio);
			encabezados.set(ID_USUARIO, usuario.idUsuario().toString());
			encabezados.set(ROL, usuario.rol());
		}).build()).orElse(anonima);
	}

	static ServerRequest sinIdentidad(ServerRequest peticion) {
		return ServerRequest.from(peticion).headers(encabezados -> {
			// Lo que mande el cliente nunca llega a los servicios
			encabezados.remove(CLAVE);
			encabezados.remove(ID_USUARIO);
			encabezados.remove(ROL);
			encabezados.set("X-Forwarded-For", peticion.servletRequest().getRemoteAddr());
		}).build();
	}

	private static Optional<String> token(ServerRequest peticion) {
		return peticion.headers().header(HttpHeaders.AUTHORIZATION).stream().findFirst()
				.filter(encabezado -> encabezado.startsWith(PREFIJO))
				.map(encabezado -> encabezado.substring(PREFIJO.length()));
	}

	private Optional<Identidad> consultar(String token) {
		try {
			return Optional.ofNullable(seguridad.post().uri("/interno/sesiones").header(CLAVE, claveServicio)
					.contentType(MediaType.APPLICATION_JSON).body(Map.of("token", token))
					.exchange((pedido, respuesta) -> {
						if (respuesta.getStatusCode().is5xxServerError()) {
							throw new RestClientException("Seguridad respondio " + respuesta.getStatusCode());
						}
						return respuesta.getStatusCode().is2xxSuccessful() ? respuesta.bodyTo(Identidad.class) : null;
					}));
		} catch (RestClientException e) {
			log.warn("No se pudo consultar la sesion en Seguridad: {}", e.getMessage());
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
		}
	}

	record Identidad(Long idUsuario, String rol) {
	}
}
