package com.sarao.eventos.service;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

// Cómo Eventos y Venta llama a otro servicio: con la clave de servicio en cada pedido, por HTTP/1.1 y con plazos, así
// una caída del otro servicio no deja hilos esperando (decisiones.md → Microservicios: toda llamada interna lleva plazos)
final class ClienteInterno {

	private static final Duration PLAZO_CONEXION = Duration.ofSeconds(1);

	private ClienteInterno() {
	}

	static RestClient crear(String url, String claveServicio, Duration plazoRespuesta) {
		var conexiones = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(PLAZO_CONEXION)
				.build();
		var fabrica = new JdkClientHttpRequestFactory(conexiones);
		fabrica.setReadTimeout(plazoRespuesta);
		return RestClient.builder().baseUrl(url).requestFactory(fabrica).defaultHeader("X-Clave-Servicio", claveServicio)
				.build();
	}
}
