package com.sarao.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
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

// Un servicio apagado no es un error del gateway: 503 con el formato y el código de los demás servicios,
// para que el frontend lo traduzca, en vez de un 500 con la traza en el registro
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "eventos.clave-servicio=clave-de-pruebas")
class ServicioCaidoTest {

	private static HttpServer seguridad;

	@Value("${local.server.port}")
	private int puerto;

	@DynamicPropertySource
	static void serviciosDePrueba(DynamicPropertyRegistry propiedades) throws IOException {
		seguridad = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		seguridad.createContext("/interno/sesiones", intercambio -> {
			byte[] cuerpo = "{\"idUsuario\":7,\"rol\":\"CLIENTE\"}".getBytes();
			intercambio.getResponseHeaders().add("Content-Type", "application/json");
			intercambio.sendResponseHeaders(200, cuerpo.length);
			intercambio.getResponseBody().write(cuerpo);
			intercambio.close();
		});
		seguridad.start();
		String apagado = "http://localhost:" + puertoLibre();
		propiedades.add("EVENTOS_SEGURIDAD_URL", () -> "http://localhost:" + seguridad.getAddress().getPort());
		propiedades.add("EVENTOS_VENTA_URL", () -> apagado);
		propiedades.add("EVENTOS_PAGOS_URL", () -> apagado);
	}

	// Un puerto donde nadie escucha: el servicio está apagado
	private static int puertoLibre() throws IOException {
		try (var socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}

	@AfterAll
	static void apagar() {
		seguridad.stop(0);
	}

	@Test
	void conPagosApagadoElPagoDevuelve503ConSuCodigo() throws Exception {
		var respuesta = pedir("/api/reservas/40/pago", "Authorization", "Bearer valido");

		assertThat(respuesta.statusCode()).isEqualTo(503);
		assertThat(respuesta.headers().firstValue("Content-Type")).hasValueSatisfying(
				tipo -> assertThat(tipo).startsWith("application/problem+json"));
		assertThat(respuesta.body()).contains("\"codigo\":\"SERVICIO_NO_DISPONIBLE\"").contains("\"status\":503");
	}

	@Test
	void conEventosYVentaApagadoElCatalogoPublicoDevuelve503() throws Exception {
		var respuesta = pedir("/api/catalogo");

		assertThat(respuesta.statusCode()).isEqualTo(503);
		assertThat(respuesta.body()).contains("\"codigo\":\"SERVICIO_NO_DISPONIBLE\"");
	}

	private HttpResponse<String> pedir(String ruta, String... encabezados) throws Exception {
		try (var cliente = HttpClient.newHttpClient()) {
			var pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta));
			for (int i = 0; i < encabezados.length; i += 2) {
				pedido.setHeader(encabezados[i], encabezados[i + 1]);
			}
			return cliente.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
		}
	}
}
