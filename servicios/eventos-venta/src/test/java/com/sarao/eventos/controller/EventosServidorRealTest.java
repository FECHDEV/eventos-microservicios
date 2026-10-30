package com.sarao.eventos.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

// Con Tomcat real: MockMvc no aplica los límites de multipart ni corta la conexión como Tomcat
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EventosServidorRealTest {

	private static final String LIMITE = "limite-de-prueba";

	@LocalServerPort
	private int puerto;

	// Una foto de celular de 10 MB: Tomcat la corta en el límite de multipart y, por defecto, solo descarta 2 MB del
	// resto antes de cerrar la conexión. El administrador tiene que recibir el aviso, no un error de red
	@Test
	void unaFotoMuyGrandeRecibeElAvisoYNoUnCorteDeConexion() throws Exception {
		HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/eventos/1/imagen"))
				.header("X-Clave-Servicio", "clave-de-pruebas")
				.header("X-Usuario-Id", "1")
				.header("X-Usuario-Rol", "ADMINISTRADOR")
				.header("Content-Type", "multipart/form-data; boundary=" + LIMITE)
				.POST(HttpRequest.BodyPublishers.ofByteArray(cuerpoConArchivoDe(10 * 1024 * 1024))).build();

		HttpResponse<String> respuesta = HttpClient.newHttpClient().send(peticion, HttpResponse.BodyHandlers.ofString());

		assertThat(respuesta.statusCode()).isEqualTo(400);
		assertThat(respuesta.body()).contains("IMAGEN_DEMASIADO_GRANDE");
	}

	@Test
	void unaSubidaMalArmadaEsDatosInvalidos() throws Exception {
		HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/eventos/1/imagen"))
				.header("X-Clave-Servicio", "clave-de-pruebas")
				.header("X-Usuario-Id", "1")
				.header("X-Usuario-Rol", "ADMINISTRADOR")
				.header("Content-Type", "multipart/form-data")
				.POST(HttpRequest.BodyPublishers.ofString("sin limite")).build();

		HttpResponse<String> respuesta = HttpClient.newHttpClient().send(peticion, HttpResponse.BodyHandlers.ofString());

		assertThat(respuesta.statusCode()).isEqualTo(400);
		assertThat(respuesta.body()).contains("DATOS_INVALIDOS");
	}

	private static byte[] cuerpoConArchivoDe(int bytes) {
		ByteArrayOutputStream cuerpo = new ByteArrayOutputStream();
		cuerpo.writeBytes(("--" + LIMITE + "\r\n"
				+ "Content-Disposition: form-data; name=\"imagen\"; filename=\"foto.jpg\"\r\n"
				+ "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
		cuerpo.writeBytes(new byte[bytes]);
		cuerpo.writeBytes(("\r\n--" + LIMITE + "--\r\n").getBytes(StandardCharsets.US_ASCII));
		return cuerpo.toByteArray();
	}
}
