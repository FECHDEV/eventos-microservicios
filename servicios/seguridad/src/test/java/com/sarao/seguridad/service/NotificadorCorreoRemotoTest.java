package com.sarao.seguridad.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

@ExtendWith(OutputCaptureExtension.class)
class NotificadorCorreoRemotoTest {

	private HttpServer notificaciones;
	private NotificadorCorreoRemoto notificador;
	private volatile int estado = 202;
	private volatile String cuerpo;
	private volatile String clave;
	private volatile String ruta;

	@BeforeEach
	void levantar() throws IOException {
		notificaciones = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		notificaciones.createContext("/", this::responder);
		notificaciones.start();
		notificador = new NotificadorCorreoRemoto("http://localhost:" + notificaciones.getAddress().getPort(),
				"clave-de-pruebas");
	}

	@AfterEach
	void apagar() {
		notificaciones.stop(0);
	}

	private void responder(HttpExchange intercambio) throws IOException {
		clave = intercambio.getRequestHeaders().getFirst("X-Clave-Servicio");
		ruta = intercambio.getRequestURI().getPath();
		cuerpo = new String(intercambio.getRequestBody().readAllBytes());
		intercambio.sendResponseHeaders(estado, -1);
		intercambio.close();
	}

	@Test
	void pideElCorreoConLaClaveDeServicio() {
		notificador.enviarRecuperacion("ana@mail.com", "Ana", "token-de-prueba");

		assertThat(ruta).isEqualTo("/interno/correos/recuperacion");
		assertThat(clave).isEqualTo("clave-de-pruebas");
		assertThat(cuerpo).contains("ana@mail.com").contains("token-de-prueba");
	}

	@Test
	void unErrorDeNotificacionesSeRegistraSinPropagarseNiMostrarElToken(CapturedOutput salida) {
		estado = 500;

		assertThatCode(() -> notificador.enviarRecuperacion("ana@mail.com", "Ana", "token-secreto"))
				.doesNotThrowAnyException();
		assertThat(salida.getOut()).contains("No se pudo pedir el correo de recuperación").doesNotContain("token-secreto");
	}

	@Test
	void conNotificacionesApagadoTampocoSePropaga(CapturedOutput salida) {
		notificaciones.stop(0);

		assertThatCode(() -> notificador.enviarRecuperacion("ana@mail.com", "Ana", "token-secreto"))
				.doesNotThrowAnyException();
		assertThat(salida.getOut()).doesNotContain("token-secreto");
	}
}
