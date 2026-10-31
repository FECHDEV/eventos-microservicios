package com.sarao.eventos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sarao.eventos.exception.ServicioNoDisponibleException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class ConsultaDeClientesTest {

	private HttpServer seguridad;
	private ConsultaDeClientes consulta;
	private volatile int estado;
	private volatile String cuerpo;
	private volatile String claveRecibida;
	private volatile String rutaRecibida;

	@BeforeEach
	void levantar() throws IOException {
		seguridad = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		seguridad.createContext("/", this::responder);
		seguridad.start();
		consulta = new ConsultaDeClientes("http://localhost:" + seguridad.getAddress().getPort(), "clave-de-pruebas");
	}

	@AfterEach
	void apagar() {
		seguridad.stop(0);
	}

	private void responder(HttpExchange intercambio) throws IOException {
		claveRecibida = intercambio.getRequestHeaders().getFirst("X-Clave-Servicio");
		rutaRecibida = intercambio.getRequestURI().getPath();
		byte[] bytes = cuerpo.getBytes();
		intercambio.getResponseHeaders().add("Content-Type", "application/json");
		intercambio.sendResponseHeaders(estado, bytes.length == 0 ? -1 : bytes.length);
		if (bytes.length > 0) {
			intercambio.getResponseBody().write(bytes);
		}
		intercambio.close();
	}

	@Test
	void preguntaConLaClaveDeServicio() {
		estado = 200;
		cuerpo = "{\"fechaNacimiento\":\"2000-05-20\"}";

		assertThat(consulta.fechaNacimiento(7L)).isEqualTo(LocalDate.of(2000, 5, 20));
		assertThat(claveRecibida).isEqualTo("clave-de-pruebas");
		assertThat(rutaRecibida).isEqualTo("/interno/usuarios/7/fecha-nacimiento");
	}

	@Test
	void unaCuentaSinFechaOUnErrorNoDejanReservar() {
		cuerpo = "";
		for (int respuesta : new int[] { 404, 500, 401 }) {
			estado = respuesta;
			assertThatThrownBy(() -> consulta.fechaNacimiento(7L)).isInstanceOf(ServicioNoDisponibleException.class);
		}
	}

	@Test
	void conSeguridadApagadaNoDejaReservar() {
		seguridad.stop(0);

		assertThatThrownBy(() -> consulta.fechaNacimiento(7L)).isInstanceOf(ServicioNoDisponibleException.class);
	}
}
