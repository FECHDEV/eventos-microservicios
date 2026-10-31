package com.sarao.seguridad.service;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import lombok.extern.slf4j.Slf4j;

// Pide el correo a Notificaciones. En segundo plano y sin propagar los fallos: si la solicitud de recuperación tardara o
// fallara solo cuando la cuenta existe, revelaría qué correos están registrados
@Slf4j
@Service
public class NotificadorCorreoRemoto implements NotificadorCorreo {

	private static final Duration PLAZO_CONEXION = Duration.ofSeconds(1);
	private static final Duration PLAZO_RESPUESTA = Duration.ofSeconds(2);

	private final RestClient notificaciones;
	private final String claveServicio;

	public NotificadorCorreoRemoto(@Value("${eventos.notificaciones-url}") String urlNotificaciones,
			@Value("${eventos.clave-servicio}") String claveServicio) {
		var conexiones = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(PLAZO_CONEXION).build();
		var fabrica = new JdkClientHttpRequestFactory(conexiones);
		fabrica.setReadTimeout(PLAZO_RESPUESTA);
		this.notificaciones = RestClient.builder().baseUrl(urlNotificaciones).requestFactory(fabrica).build();
		this.claveServicio = claveServicio;
	}

	@Async
	@Override
	public void enviarRecuperacion(String correo, String nombre, String token) {
		try {
			notificaciones.post().uri("/interno/correos/recuperacion").header("X-Clave-Servicio", claveServicio)
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("correo", correo, "nombre", nombre, "token", token)).retrieve().toBodilessEntity();
		} catch (RestClientException e) {
			// Sin el cuerpo ni la causa completa: la petición lleva el token
			log.error("No se pudo pedir el correo de recuperación para {}: {}", correo, e.getClass().getSimpleName());
		}
	}
}
