package com.sarao.gateway;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.function.ServerRequest;

// Resuelve el token con Seguridad, la unica que tiene la clave JWT, y pasa la identidad a los servicios. Un token invalido
// o Seguridad caida no cortan la peticion: va sin identidad, las rutas publicas la atienden y las protegidas dan 401
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
		return ServerRequest.from(peticion).headers(encabezados -> {
			// Lo que mande el cliente nunca llega a los servicios
			encabezados.remove(CLAVE);
			encabezados.remove(ID_USUARIO);
			encabezados.remove(ROL);
			encabezados.set("X-Forwarded-For", peticion.servletRequest().getRemoteAddr());
			identidad.ifPresent(usuario -> {
				encabezados.set(CLAVE, claveServicio);
				encabezados.set(ID_USUARIO, usuario.idUsuario().toString());
				encabezados.set(ROL, usuario.rol());
			});
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
					.exchange((pedido, respuesta) -> respuesta.getStatusCode().is2xxSuccessful()
							? respuesta.bodyTo(Identidad.class)
							: null));
		} catch (RestClientException e) {
			log.warn("No se pudo consultar la sesion en Seguridad: {}", e.getMessage());
			return Optional.empty();
		}
	}

	record Identidad(Long idUsuario, String rol) {
	}
}
