package com.sarao.eventos;

import static com.sarao.eventos.SesionDelGateway.administrador;
import static com.sarao.eventos.SesionDelGateway.cliente;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import com.sarao.eventos.service.ConsultaDeClientes;

// shortcut: las entradas se insertan a mano porque todavía no hay pago que las emita; con HU-18, por la compra real
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EntradasIntegracionTest {

	private static final ZoneId LA_PAZ = ZoneId.of("America/La_Paz");
	private static final LocalDate HOY = LocalDate.now(LA_PAZ);

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private ConsultaDeClientes consultaDeClientes;

	private Integer idEvento;
	private Integer idTipo;
	private Integer idReserva;

	private MvcTestResult post(String uri, String cuerpo) {
		return mvc.post().uri(uri).with(administrador()).contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
	}

	private Integer idDe(MvcTestResult resultado, String campo) throws Exception {
		assertThat(resultado).hasStatus(HttpStatus.CREATED);
		return JsonPath.read(resultado.getResponse().getContentAsString(), "$." + campo);
	}

	private MvcTestResult reservar(int cantidad) {
		return mvc.post().uri("/api/reservas").with(cliente()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"idTipoEntrada\":%d,\"cantidad\":%d}".formatted(idTipo, cantidad)).exchange();
	}

	private void emitir(String codigo, String estado) {
		jdbc.update("""
				INSERT INTO entrada (id_tipo_entrada, id_reserva, id_usuario, id_pago, codigo_qr, estado, fecha_emision)
				VALUES (?, ?, 7, 30, ?, ?, ?)
				""", idTipo, idReserva, codigo, estado, LocalDateTime.now(LA_PAZ));
	}

	private Long idEntrada(String codigo) {
		return jdbc.queryForObject("SELECT id_entrada FROM entrada WHERE codigo_qr = ?", Long.class, codigo);
	}

	private String estado(String codigo) {
		return jdbc.queryForObject("SELECT estado FROM entrada WHERE codigo_qr = ?", String.class, codigo);
	}

	private MvcTestResult operar(String operacion, String cuerpo) {
		var peticion = mvc.post().uri("/api/eventos/{id}/{operacion}", idEvento, operacion).with(administrador());
		return cuerpo == null ? peticion.exchange()
				: peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
	}

	// Noche de jazz con límite de 3, y una reserva de Ana con una entrada activa y otra usada
	@BeforeEach
	void preparar() throws Exception {
		given(consultaDeClientes.fechaNacimiento(any())).willReturn(LocalDate.of(2000, 5, 20));
		idEvento = idDe(post("/api/eventos", """
				{"nombre":"Noche de jazz","fecha":"%s","horaInicio":"20:00","horaFin":"23:00","lugar":"Teatro","limiteCompra":3}
				""".formatted(HOY.plusDays(5))), "idEvento");
		idTipo = idDe(post("/api/eventos/" + idEvento + "/tipos-entrada",
				"{\"nombre\":\"General\",\"precio\":50.00,\"cantidadTotal\":10}"), "idTipoEntrada");
		assertThat(operar("activacion", null)).hasStatus(HttpStatus.OK);
		idReserva = idDe(reservar(2), "idReserva");
		jdbc.update("UPDATE reserva SET estado = 'CONFIRMADA' WHERE id_reserva = ?", idReserva);
		emitir("codigo-activa", "ACTIVA");
		emitir("codigo-usada", "UTILIZADA");
	}

	@AfterEach
	void limpiar() {
		jdbc.update("DELETE FROM entrada");
		jdbc.update("DELETE FROM reserva");
		jdbc.update("DELETE FROM tipo_entrada");
		jdbc.update("DELETE FROM evento");
	}

	// CU-08 RF-2, RF-3, RF-9 y RF-10, contra la base real
	@Test
	void elClienteVeSusComprasYElCodigoSoloEnElDetalle() throws Exception {
		MvcTestResult lista = mvc.get().uri("/api/entradas").with(cliente()).exchange();

		assertThat(lista).hasStatus(HttpStatus.OK);
		assertThat(lista).bodyJson().extractingPath("$.proximas[0].idReserva").isEqualTo(idReserva);
		assertThat(lista).bodyJson().extractingPath("$.proximas[0].cantidad").isEqualTo(2);
		assertThat(lista.getResponse().getContentAsString()).doesNotContain("codigo-activa");

		MvcTestResult activa = mvc.get().uri("/api/entradas/" + idEntrada("codigo-activa")).with(cliente()).exchange();
		assertThat(activa).bodyJson().extractingPath("$.codigoQr").isEqualTo("codigo-activa");
		assertThat(activa).bodyJson().extractingPath("$.numero").isEqualTo(1);
		MvcTestResult usada = mvc.get().uri("/api/entradas/" + idEntrada("codigo-usada")).with(cliente()).exchange();
		assertThat(usada).bodyJson().extractingPath("$.codigoQr").isNull();
		assertThat(usada).bodyJson().extractingPath("$.numero").isEqualTo(2);

		jdbc.update("UPDATE entrada SET id_usuario = 8");
		assertThat(mvc.get().uri("/api/entradas/" + idEntrada("codigo-activa")).with(cliente()).exchange())
				.hasStatus(HttpStatus.NOT_FOUND);
	}

	// CU-12 FA-02: activar un postergado reactiva la usada, que vuelve a mostrar su código (RF-26, RF-31 y RF-32)
	@Test
	void activarUnPostergadoReactivaLasEntradasUsadas() {
		assertThat(operar("postergacion", "{\"fecha\":\"%s\"}".formatted(HOY.plusDays(9)))).hasStatus(HttpStatus.OK);

		MvcTestResult activado = operar("activacion", null);

		assertThat(activado).bodyJson().extractingPath("$.entradasReactivadas").isEqualTo(1);
		assertThat(estado("codigo-usada")).isEqualTo("ACTIVA");
		assertThat(mvc.get().uri("/api/entradas/" + idEntrada("codigo-usada")).with(cliente()).exchange()).bodyJson()
				.extractingPath("$.codigoQr").isEqualTo("codigo-usada");
	}

	// CU-07 RF-21: las emitidas cuentan en el límite de 3
	@Test
	void lasEntradasEmitidasCuentanEnElLimite() {
		assertThat(reservar(2)).bodyJson().extractingPath("$.codigo").isEqualTo("LIMITE_COMPRA_EXCEDIDO");
		assertThat(reservar(1)).hasStatus(HttpStatus.CREATED);
	}
}
