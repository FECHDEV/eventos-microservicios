package com.sarao.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "eventos.clave-servicio=clave-de-pruebas")
class EnrutamientoTest {

	private static HttpServer seguridad;

	@Value("${local.server.port}")
	private int puerto;

	@DynamicPropertySource
	static void seguridadDePrueba(DynamicPropertyRegistry propiedades) throws IOException {
		seguridad = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		seguridad.createContext("/interno/sesiones", EnrutamientoTest::resolverToken);
		seguridad.createContext("/", EnrutamientoTest::devolverLoRecibido);
		seguridad.start();
		propiedades.add("EVENTOS_SEGURIDAD_URL", () -> "http://localhost:" + seguridad.getAddress().getPort());
	}

	@AfterAll
	static void apagar() {
		seguridad.stop(0);
	}

	private static void resolverToken(HttpExchange intercambio) throws IOException {
		String cuerpo = new String(intercambio.getRequestBody().readAllBytes());
		boolean claveCorrecta = "clave-de-pruebas".equals(intercambio.getRequestHeaders().getFirst("X-Clave-Servicio"));
		if (cuerpo.contains("falla")) {
			responder(intercambio, 500, "");
		} else if (claveCorrecta && cuerpo.contains("valido")) {
			intercambio.getResponseHeaders().add("Content-Type", "application/json");
			responder(intercambio, 200, "{\"idUsuario\":7,\"rol\":\"CLIENTE\"}");
		} else {
			responder(intercambio, 404, "");
		}
	}

	private static void devolverLoRecibido(HttpExchange intercambio) throws IOException {
		var encabezados = intercambio.getRequestHeaders();
		responder(intercambio, 200, String.join("|", intercambio.getRequestURI().getPath(),
				Objects.toString(encabezados.getFirst("X-Usuario-Id")), Objects.toString(encabezados.getFirst("X-Usuario-Rol")),
				Objects.toString(encabezados.getFirst("X-Clave-Servicio")), Objects.toString(encabezados.getFirst("X-Forwarded-For"))));
	}

	private static void responder(HttpExchange intercambio, int estado, String cuerpo) throws IOException {
		byte[] bytes = cuerpo.getBytes();
		intercambio.sendResponseHeaders(estado, bytes.length == 0 ? -1 : bytes.length);
		if (bytes.length > 0) {
			intercambio.getResponseBody().write(bytes);
		}
		intercambio.close();
	}

	@Test
	void lasRutasPublicasLleganASeguridadConSuRutaOriginalYSinIdentidad() throws Exception {
		var respuesta = pedir("/api/auth/registro");

		assertThat(respuesta.statusCode()).isEqualTo(200);
		assertThat(respuesta.body()).startsWith("/api/auth/registro|null|null|null|");
	}

	@Test
	void lasRutasInternasNoSePublican() throws Exception {
		assertThat(pedir("/interno/usuarios").statusCode()).isEqualTo(404);
		assertThat(pedir("/interno/sesiones").statusCode()).isEqualTo(404);
		assertThat(pedir("/api/interno/usuarios").statusCode()).isEqualTo(404);
	}

	@Test
	void tokenValidoPasaLaIdentidadConLaClaveDeServicio() throws Exception {
		assertThat(pedir("/api/cuenta", "Authorization", "Bearer valido").body())
				.startsWith("/api/cuenta|7|CLIENTE|clave-de-pruebas|");
	}

	@Test
	void tokenInvalidoOSinPrefijoSiguenSinIdentidad() throws Exception {
		assertThat(pedir("/api/cuenta", "Authorization", "Bearer otro").body()).startsWith("/api/cuenta|null|null|null|");
		assertThat(pedir("/api/cuenta", "Authorization", "valido").body()).startsWith("/api/cuenta|null|null|null|");
	}

	@Test
	void seguridadConErrorDevuelve503YNoCierraLaSesion() throws Exception {
		assertThat(pedir("/api/cuenta", "Authorization", "Bearer falla").statusCode()).isEqualTo(503);
	}

	@Test
	void losEncabezadosDeIdentidadDelClienteNoLleganALosServicios() throws Exception {
		var respuesta = pedir("/api/cuenta", "X-Usuario-Id", "1", "X-Usuario-Rol", "ADMINISTRADOR", "X-Clave-Servicio",
				"adivinada", "X-Forwarded-For", "1.2.3.4");

		assertThat(respuesta.body()).startsWith("/api/cuenta|null|null|null|");
		assertThat(respuesta.body()).doesNotContain("1.2.3.4");
		assertThat(respuesta.body()).endsWith("|127.0.0.1,127.0.0.1");
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
