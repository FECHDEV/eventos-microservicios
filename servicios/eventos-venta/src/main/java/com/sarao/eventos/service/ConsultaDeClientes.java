package com.sarao.eventos.service;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.sarao.eventos.exception.ServicioNoDisponibleException;

// Lo que Eventos y Venta necesita de una cuenta, que vive en Seguridad (decisiones.md → Fecha de nacimiento del Cliente)
@Component
public class ConsultaDeClientes {

	private static final Duration PLAZO_CONEXION = Duration.ofSeconds(1);
	private static final Duration PLAZO_RESPUESTA = Duration.ofSeconds(2);

	private final RestClient seguridad;
	private final String claveServicio;

	public ConsultaDeClientes(@Value("${eventos.seguridad-url}") String urlSeguridad,
			@Value("${eventos.clave-servicio}") String claveServicio) {
		var conexiones = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(PLAZO_CONEXION).build();
		var fabrica = new JdkClientHttpRequestFactory(conexiones);
		fabrica.setReadTimeout(PLAZO_RESPUESTA);
		this.seguridad = RestClient.builder().baseUrl(urlSeguridad).requestFactory(fabrica).build();
		this.claveServicio = claveServicio;
	}

	public LocalDate fechaNacimiento(Long idCliente) {
		try {
			return seguridad.get().uri("/interno/usuarios/{id}/fecha-nacimiento", idCliente)
					.header("X-Clave-Servicio", claveServicio).retrieve().body(FechaNacimiento.class).fechaNacimiento();
		} catch (RestClientException e) {
			throw new ServicioNoDisponibleException();
		}
	}

	private record FechaNacimiento(LocalDate fechaNacimiento) {
	}
}
