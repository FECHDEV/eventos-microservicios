package com.sarao.eventos.service;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
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

	private static final Duration PLAZO_RESPUESTA = Duration.ofSeconds(5);

	private final RestClient notificaciones;

	public PedidoDeCorreoDeReporte(@Value("${eventos.notificaciones-url}") String urlNotificaciones,
			@Value("${eventos.clave-servicio}") String claveServicio) {
		this.notificaciones = ClienteInterno.crear(urlNotificaciones, claveServicio, PLAZO_RESPUESTA);
	}

	public void pedir(Object correo) {
		try {
			notificaciones.post().uri("/interno/correos/reporte")
					.contentType(MediaType.APPLICATION_JSON).body(correo).retrieve().toBodilessEntity();
		} catch (RestClientException e) {
			// Sin el contenido: lleva los datos del reporte
			log.error("No se pudo pedir el correo del reporte: {}", e.getClass().getSimpleName());
			throw new ServicioNoDisponibleException();
		}
	}
}
