package com.sarao.eventos.service;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.sarao.eventos.exception.ServicioNoDisponibleException;
import lombok.extern.slf4j.Slf4j;

// Lo que Eventos y Venta necesita de una cuenta, que vive en Seguridad (decisiones.md → Fecha de nacimiento del Cliente)
@Slf4j
@Component
public class ConsultaDeClientes {

	private static final Duration PLAZO_CONEXION = Duration.ofSeconds(1);
	private static final Duration PLAZO_RESPUESTA = Duration.ofSeconds(5);

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
			log.warn("No se pudo consultar la cuenta en Seguridad: {}", e.getMessage());
			throw new ServicioNoDisponibleException();
		}
	}

	// CU-07 RF-17: a quién mandar los correos de la compra
	public Contacto contacto(Long idCliente) {
		try {
			return seguridad.get().uri("/interno/usuarios/{id}/contacto", idCliente)
					.header("X-Clave-Servicio", claveServicio).retrieve().body(Contacto.class);
		} catch (RestClientException e) {
			log.warn("No se pudo consultar el contacto en Seguridad: {}", e.getMessage());
			throw new ServicioNoDisponibleException();
		}
	}

	public record Contacto(String correo, String nombre) {
	}

	// CU-14: las cuentas de unos ids, en una sola llamada. Una que no existe no viene
	public Map<Long, Cuenta> cuentas(Collection<Long> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		try {
			Cuenta[] cuentas = seguridad.post().uri("/interno/usuarios/lote").header("X-Clave-Servicio", claveServicio)
					.contentType(MediaType.APPLICATION_JSON).body(Map.of("ids", Set.copyOf(ids))).retrieve()
					.body(Cuenta[].class);
			return cuentas == null ? Map.of()
					: Arrays.stream(cuentas).collect(Collectors.toMap(Cuenta::idUsuario, Function.identity()));
		} catch (RestClientException e) {
			log.warn("No se pudieron consultar las cuentas en Seguridad: {}", e.getMessage());
			throw new ServicioNoDisponibleException();
		}
	}

	// CU-14 RF-10: los Clientes registrados en el rango; un null no filtra y hasta es exclusivo
	public List<Cuenta> clientes(LocalDateTime desde, LocalDateTime hasta) {
		try {
			Cuenta[] cuentas = seguridad.get().uri(uri -> uri.path("/interno/usuarios/clientes")
					.queryParamIfPresent("desde", Optional.ofNullable(desde))
					.queryParamIfPresent("hasta", Optional.ofNullable(hasta)).build())
					.header("X-Clave-Servicio", claveServicio).retrieve().body(Cuenta[].class);
			return cuentas == null ? List.of() : List.of(cuentas);
		} catch (RestClientException e) {
			log.warn("No se pudieron consultar los clientes en Seguridad: {}", e.getMessage());
			throw new ServicioNoDisponibleException();
		}
	}

	private record FechaNacimiento(LocalDate fechaNacimiento) {
	}
}
