package com.sarao.eventos.service;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.sarao.eventos.exception.ServicioNoDisponibleException;
import lombok.extern.slf4j.Slf4j;

// CU-14 RF-23: el correo del reporte lo manda Notificaciones, que responde 202 sin esperar al servidor de correo. Si no
// lo acepta, el Administrador lo sabe (503) en vez de ver un «enviado» que no sale (plan de HU-20 → Decisiones)
@Slf4j
@Component
public class PedidoDeCorreoDeReporte {

	private static final Duration PLAZO_CONEXION = Duration.ofSeconds(1);
	private static final Duration PLAZO_RESPUESTA = Duration.ofSeconds(5);

	private final RestClient notificaciones;
	private final String claveServicio;

	public PedidoDeCorreoDeReporte(@Value("${eventos.notificaciones-url}") String urlNotificaciones,
			@Value("${eventos.clave-servicio}") String claveServicio) {
		var conexiones = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(PLAZO_CONEXION).build();
		var fabrica = new JdkClientHttpRequestFactory(conexiones);
		fabrica.setReadTimeout(PLAZO_RESPUESTA);
		this.notificaciones = RestClient.builder().baseUrl(urlNotificaciones).requestFactory(fabrica).build();
		this.claveServicio = claveServicio;
	}

	public void pedir(Object correo) {
		try {
			notificaciones.post().uri("/interno/correos/reporte").header("X-Clave-Servicio", claveServicio)
					.contentType(MediaType.APPLICATION_JSON).body(correo).retrieve().toBodilessEntity();
		} catch (RestClientException e) {
			// Sin el contenido: lleva los datos del reporte
			log.error("No se pudo pedir el correo del reporte: {}", e.getClass().getSimpleName());
			throw new ServicioNoDisponibleException();
		}
	}
}
