package com.sarao.eventos.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.sarao.eventos.exception.ServicioNoDisponibleException;
import lombok.extern.slf4j.Slf4j;

// Lo que Eventos y Venta necesita de una cuenta, que vive en Seguridad (decisiones.md → Fecha de nacimiento del Cliente)
@Slf4j
@Component
public class ConsultaDeClientes {

	// La compra espera poco; los reportes traen listas y pueden tardar más sin frenar a nadie
	private static final Duration PLAZO_RESPUESTA = Duration.ofSeconds(2);
	private static final Duration PLAZO_DE_LISTAS = Duration.ofSeconds(5);
	// Lo que admite /interno/usuarios/lote por pedido
	private static final int TANDA = 1000;

	private final RestClient seguridad;
	private final RestClient seguridadParaListas;

	public ConsultaDeClientes(@Value("${eventos.seguridad-url}") String urlSeguridad,
			@Value("${eventos.clave-servicio}") String claveServicio) {
		this.seguridad = ClienteInterno.crear(urlSeguridad, claveServicio, PLAZO_RESPUESTA);
		this.seguridadParaListas = ClienteInterno.crear(urlSeguridad, claveServicio, PLAZO_DE_LISTAS);
	}

	public LocalDate fechaNacimiento(Long idCliente) {
		try {
			return seguridad.get().uri("/interno/usuarios/{id}/fecha-nacimiento", idCliente).retrieve().body(FechaNacimiento.class).fechaNacimiento();
		} catch (RestClientException e) {
			log.warn("No se pudo consultar la cuenta en Seguridad: {}", e.getMessage());
			throw new ServicioNoDisponibleException();
		}
	}

	// CU-07 RF-17: a quién mandar los correos de la compra
	public Contacto contacto(Long idCliente) {
		try {
			return seguridad.get().uri("/interno/usuarios/{id}/contacto", idCliente).retrieve().body(Contacto.class);
		} catch (RestClientException e) {
			log.warn("No se pudo consultar el contacto en Seguridad: {}", e.getMessage());
			throw new ServicioNoDisponibleException();
		}
	}

	public record Contacto(String correo, String nombre) {
	}

	// CU-14: las cuentas de unos ids, por tandas de mil. Una que no existe no viene
	public Map<Long, Cuenta> cuentas(Collection<Long> ids) {
		List<Long> distintos = List.copyOf(Set.copyOf(ids));
		Map<Long, Cuenta> todas = new HashMap<>();
		try {
			for (int desde = 0; desde < distintos.size(); desde += TANDA) {
				List<Long> tanda = distintos.subList(desde, Math.min(desde + TANDA, distintos.size()));
				Cuenta[] cuentas = seguridadParaListas.post().uri("/interno/usuarios/lote").contentType(MediaType.APPLICATION_JSON)
						.body(Map.of("ids", tanda)).retrieve().body(Cuenta[].class);
				if (cuentas != null) {
					Arrays.stream(cuentas).forEach(cuenta -> todas.put(cuenta.idUsuario(), cuenta));
				}
			}
			return todas;
		} catch (RestClientException e) {
			log.warn("No se pudieron consultar las cuentas en Seguridad: {}", e.getMessage());
			throw new ServicioNoDisponibleException();
		}
	}

	// CU-14 RF-10: los Clientes registrados en el rango; un null no filtra y hasta es exclusivo
	public List<Cuenta> clientes(LocalDateTime desde, LocalDateTime hasta) {
		try {
			Cuenta[] cuentas = seguridadParaListas.get().uri(uri -> uri.path("/interno/usuarios/clientes")
					.queryParamIfPresent("desde", Optional.ofNullable(desde))
					.queryParamIfPresent("hasta", Optional.ofNullable(hasta)).build()).retrieve().body(Cuenta[].class);
			return cuentas == null ? List.of() : List.of(cuentas);
		} catch (RestClientException e) {
			log.warn("No se pudieron consultar los clientes en Seguridad: {}", e.getMessage());
			throw new ServicioNoDisponibleException();
		}
	}

	private record FechaNacimiento(LocalDate fechaNacimiento) {
	}
}
