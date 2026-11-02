package com.sarao.pagos.service;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.sarao.pagos.entity.EstadoReserva;
import com.sarao.pagos.exception.ReservaNoEncontradaException;
import com.sarao.pagos.exception.ServicioNoDisponibleException;
import lombok.extern.slf4j.Slf4j;

// La reserva vive en Eventos y Venta, dueño del cupo (decisiones.md → Microservicios: confirmación en dos pasos)
@Slf4j
@Component
public class ConsultaDeReservas {

	private static final Duration PLAZO_CONEXION = Duration.ofSeconds(1);
	// La confirmación puede esperar un bloqueo del evento: si se pasa, se repite y no emite de más (es idempotente)
	private static final Duration PLAZO_RESPUESTA = Duration.ofSeconds(3);

	private final RestClient venta;
	private final String claveServicio;

	public ConsultaDeReservas(@Value("${eventos.venta-url}") String urlVenta,
			@Value("${eventos.clave-servicio}") String claveServicio) {
		var conexiones = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(PLAZO_CONEXION).build();
		var fabrica = new JdkClientHttpRequestFactory(conexiones);
		fabrica.setReadTimeout(PLAZO_RESPUESTA);
		this.venta = RestClient.builder().baseUrl(urlVenta).requestFactory(fabrica).build();
		this.claveServicio = claveServicio;
	}

	public record ReservaParaCobrar(EstadoReserva estado, long segundosRestantes, BigDecimal total, String concepto) {
	}

	public enum ResultadoConfirmacion {
		CONFIRMADA, SIN_CUPO
	}

	private record Confirmacion(ResultadoConfirmacion resultado) {
	}

	// La de otro Cliente responde igual que una inexistente (CU-07 RF-2)
	public ReservaParaCobrar paraCobrar(Long idReserva, Long idCliente) {
		try {
			return validar(venta.get().uri("/interno/reservas/{id}?cliente={cliente}", idReserva, idCliente)
					.header("X-Clave-Servicio", claveServicio).retrieve().body(ReservaParaCobrar.class));
		} catch (HttpClientErrorException e) {
			if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
				throw new ReservaNoEncontradaException();
			}
			throw noDisponible(e);
		} catch (RestClientException e) {
			throw noDisponible(e);
		}
	}

	// CU-07 RF-6 y RF-9. Si falla, no se reintenta: la reserva se libera sola al vencer
	public void liberar(Long idReserva) {
		try {
			venta.post().uri("/interno/reservas/{id}/liberacion", idReserva).header("X-Clave-Servicio", claveServicio)
					.retrieve().toBodilessEntity();
		} catch (RestClientException e) {
			log.warn("No se pudo liberar la reserva {}; se libera al vencer: {}", idReserva, e.getMessage());
		}
	}

	// CU-07 RF-11 y RF-15. Idempotente del otro lado: repetirla después de una falla no emite de más
	public ResultadoConfirmacion confirmar(Long idReserva, Long idPago) {
		try {
			Confirmacion confirmacion = venta.post().uri("/interno/reservas/{id}/confirmacion", idReserva)
					.header("X-Clave-Servicio", claveServicio).contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("idPago", idPago)).retrieve().body(Confirmacion.class);
			if (confirmacion == null || confirmacion.resultado() == null) {
				throw new ServicioNoDisponibleException();
			}
			return confirmacion.resultado();
		} catch (RestClientException e) {
			throw noDisponible(e);
		}
	}

	private static ReservaParaCobrar validar(ReservaParaCobrar reserva) {
		if (reserva == null || reserva.estado() == null || reserva.total() == null || reserva.concepto() == null
				|| reserva.total().signum() <= 0) {
			throw new ServicioNoDisponibleException();
		}
		return reserva;
	}

	private static ServicioNoDisponibleException noDisponible(RestClientException e) {
		log.warn("No se pudo hablar con Eventos y Venta: {}", e.getMessage());
		return new ServicioNoDisponibleException();
	}
}
