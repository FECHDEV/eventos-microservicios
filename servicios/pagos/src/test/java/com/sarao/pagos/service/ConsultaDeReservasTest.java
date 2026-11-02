package com.sarao.pagos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sarao.pagos.entity.EstadoReserva;
import com.sarao.pagos.exception.ReservaNoEncontradaException;
import com.sarao.pagos.exception.ServicioNoDisponibleException;
import com.sarao.pagos.service.ConsultaDeReservas.ResultadoConfirmacion;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

// Pagos frente a Eventos y Venta: respuestas buenas, rechazos, caídas y basura
class ConsultaDeReservasTest {

	private HttpServer venta;
	private ConsultaDeReservas consulta;
	private volatile int estado;
	private volatile String cuerpo;
	private volatile int demoraMs;
	private volatile String claveRecibida;
	private volatile String pedidoRecibido;

	@BeforeEach
	void levantar() throws IOException {
		venta = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		venta.createContext("/", this::responder);
		venta.start();
		consulta = new ConsultaDeReservas("http://localhost:" + venta.getAddress().getPort(), "clave-de-pruebas");
	}

	@AfterEach
	void apagar() {
		venta.stop(0);
	}

	private void responder(HttpExchange intercambio) throws IOException {
		claveRecibida = intercambio.getRequestHeaders().getFirst("X-Clave-Servicio");
		pedidoRecibido = intercambio.getRequestMethod() + " " + intercambio.getRequestURI() + " "
				+ new String(intercambio.getRequestBody().readAllBytes());
		try {
			Thread.sleep(demoraMs);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		byte[] bytes = cuerpo.getBytes();
		intercambio.getResponseHeaders().add("Content-Type", "application/json");
		intercambio.sendResponseHeaders(estado, bytes.length == 0 ? -1 : bytes.length);
		if (bytes.length > 0) {
			intercambio.getResponseBody().write(bytes);
		}
		intercambio.close();
	}

	@Test
	void pideLaReservaDelClienteConLaClave() {
		estado = 200;
		cuerpo = "{\"idReserva\":40,\"estado\":\"VIGENTE\",\"segundosRestantes\":540,\"total\":100.00,\"concepto\":\"2 × General\"}";

		var reserva = consulta.paraCobrar(40L, 7L);

		assertThat(reserva.estado()).isEqualTo(EstadoReserva.VIGENTE);
		assertThat(reserva.segundosRestantes()).isEqualTo(540);
		assertThat(reserva.total()).isEqualByComparingTo(new BigDecimal("100"));
		assertThat(claveRecibida).isEqualTo("clave-de-pruebas");
		assertThat(pedidoRecibido).startsWith("GET /interno/reservas/40?cliente=7");
	}

	// RF-2: Eventos y Venta responde 404 a una ajena
	@Test
	void unaReservaAjenaOInexistenteNoSeEncuentra() {
		estado = 404;
		cuerpo = "";

		assertThatThrownBy(() -> consulta.paraCobrar(40L, 7L)).isInstanceOf(ReservaNoEncontradaException.class);
	}

	@Test
	void unErrorUnaRespuestaInvalidaOUnaCaidaNoDejanCobrar() {
		estado = 500;
		cuerpo = "";
		assertThatThrownBy(() -> consulta.paraCobrar(40L, 7L)).isInstanceOf(ServicioNoDisponibleException.class);

		estado = 200;
		for (String basura : new String[] { "{}", "{\"estado\":\"VIGENTE\",\"total\":0,\"concepto\":\"x\"}",
				"{\"estado\":\"OTRO\"}", "no es json" }) {
			cuerpo = basura;
			assertThatThrownBy(() -> consulta.paraCobrar(40L, 7L)).isInstanceOf(ServicioNoDisponibleException.class);
		}

		venta.stop(0);
		assertThatThrownBy(() -> consulta.paraCobrar(40L, 7L)).isInstanceOf(ServicioNoDisponibleException.class);
	}

	@Test
	void confirmaConElPagoYLeeElResultado() {
		estado = 200;
		cuerpo = "{\"resultado\":\"SIN_CUPO\"}";

		assertThat(consulta.confirmar(40L, 41L)).isEqualTo(ResultadoConfirmacion.SIN_CUPO);
		assertThat(pedidoRecibido).isEqualTo("POST /interno/reservas/40/confirmacion {\"idPago\":41}");
	}

	// La respuesta tardía o rara deja la confirmación pendiente: se repite después
	@Test
	void unaConfirmacionSinRespuestaClaraFalla() {
		estado = 200;
		cuerpo = "{\"resultado\":\"CONFIRMADA\"}";
		demoraMs = 3500;
		assertThatThrownBy(() -> consulta.confirmar(40L, 41L)).isInstanceOf(ServicioNoDisponibleException.class);

		demoraMs = 0;
		cuerpo = "{}";
		assertThatThrownBy(() -> consulta.confirmar(40L, 41L)).isInstanceOf(ServicioNoDisponibleException.class);

		estado = 409;
		cuerpo = "{\"codigo\":\"OPERACION_SIMULTANEA\"}";
		assertThatThrownBy(() -> consulta.confirmar(40L, 41L)).isInstanceOf(ServicioNoDisponibleException.class);
	}

	// RF-9: si la liberación falla, la reserva se libera sola al vencer
	@Test
	void liberarNoPropagaLasFallas() {
		estado = 204;
		cuerpo = "";
		consulta.liberar(40L);
		assertThat(pedidoRecibido).startsWith("POST /interno/reservas/40/liberacion");

		venta.stop(0);
		assertThatCode(() -> consulta.liberar(40L)).doesNotThrowAnyException();
	}
}
