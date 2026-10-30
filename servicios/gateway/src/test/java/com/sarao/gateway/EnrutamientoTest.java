package com.sarao.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.sun.net.httpserver.HttpServer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EnrutamientoTest {

	private static HttpServer seguridad;

	@Value("${local.server.port}")
	private int puerto;

	@DynamicPropertySource
	static void seguridadDePrueba(DynamicPropertyRegistry propiedades) throws IOException {
		seguridad = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		seguridad.createContext("/", intercambio -> {
			byte[] ruta = intercambio.getRequestURI().getPath().getBytes();
			intercambio.sendResponseHeaders(200, ruta.length);
			intercambio.getResponseBody().write(ruta);
			intercambio.close();
		});
		seguridad.start();
		propiedades.add("EVENTOS_SEGURIDAD_URL", () -> "http://localhost:" + seguridad.getAddress().getPort());
	}

	@AfterAll
	static void apagar() {
		seguridad.stop(0);
	}

	@Test
	void lasRutasPublicasLleganASeguridadConSuRutaOriginal() throws Exception {
		var respuesta = pedir("/api/auth/registro");

		assertThat(respuesta.statusCode()).isEqualTo(200);
		assertThat(respuesta.body()).isEqualTo("/api/auth/registro");
	}

	@Test
	void lasRutasInternasNoSePublican() throws Exception {
		assertThat(pedir("/interno/usuarios").statusCode()).isEqualTo(404);
		assertThat(pedir("/api/interno/usuarios").statusCode()).isEqualTo(404);
	}

	private HttpResponse<String> pedir(String ruta) throws Exception {
		try (var cliente = HttpClient.newHttpClient()) {
			var pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).build();
			return cliente.send(pedido, HttpResponse.BodyHandlers.ofString());
		}
	}

}
