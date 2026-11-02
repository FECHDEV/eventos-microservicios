package com.sarao.eventos.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.sarao.eventos.exception.ServicioNoDisponibleException;
import lombok.extern.slf4j.Slf4j;

// Los pagos viven en Pagos (decisiones.md → Microservicios: reportes): se piden por rango de fechas de pago
@Slf4j
@Component
public class ConsultaDePagos {

	private static final Duration PLAZO_RESPUESTA = Duration.ofSeconds(5);

	private final RestClient pagos;

	public ConsultaDePagos(@Value("${eventos.pagos-url}") String urlPagos,
			@Value("${eventos.clave-servicio}") String claveServicio) {
		this.pagos = ClienteInterno.crear(urlPagos, claveServicio, PLAZO_RESPUESTA);
	}

	// CU-14 RF-7 y RF-8: los pagos confirmados; con conDevolucion también los tardíos sin cupo. Un null no filtra y
	// hasta es exclusivo
	public List<PagoCobrado> cobrados(boolean conDevolucion, LocalDateTime desde, LocalDateTime hasta) {
		try {
			PagoCobrado[] cobrados = pagos.get().uri(uri -> uri.path("/interno/pagos/cobrados")
					.queryParam("conDevolucion", conDevolucion)
					.queryParamIfPresent("desde", Optional.ofNullable(desde))
					.queryParamIfPresent("hasta", Optional.ofNullable(hasta)).build()).retrieve().body(PagoCobrado[].class);
			return cobrados == null ? List.of() : List.of(cobrados);
		} catch (RestClientException e) {
			log.warn("No se pudieron consultar los pagos: {}", e.getMessage());
			throw new ServicioNoDisponibleException();
		}
	}
}
