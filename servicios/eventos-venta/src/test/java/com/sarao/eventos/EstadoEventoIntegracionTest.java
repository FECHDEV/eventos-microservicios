package com.sarao.eventos;

import static com.sarao.eventos.SesionDelGateway.administrador;
import static com.sarao.eventos.SesionDelGateway.cliente;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import com.sarao.eventos.service.GestionEstadoEventosService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EstadoEventoIntegracionTest {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private GestionEstadoEventosService gestionEstadoEventosService;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void limpiar() {
		jdbc.update("DELETE FROM tipo_entrada");
		jdbc.update("DELETE FROM evento");
	}

	private static LocalDate hoy() {
		return LocalDate.now(ZoneId.of("America/La_Paz"));
	}

	private Integer registrarEvento(LocalDate fecha, String inicio, String fin) throws Exception {
		MvcTestResult alta = mvc.post().uri("/api/eventos").with(administrador())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nombre":"Noche de jazz","fecha":"%s","horaInicio":"%s","horaFin":"%s","lugar":"Teatro Municipal"}
						""".formatted(fecha, inicio, fin)).exchange();
		assertThat(alta).hasStatus(HttpStatus.CREATED);
		return JsonPath.read(alta.getResponse().getContentAsString(), "$.idEvento");
	}

	private void agregarTipo(Integer idEvento) {
		assertThat(mvc.post().uri("/api/eventos/{id}/tipos-entrada", idEvento)
				.with(administrador()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"General\",\"precio\":50,\"cantidadTotal\":100}").exchange())
				.hasStatus(HttpStatus.CREATED);
	}

	private MvcTestResult operar(Integer idEvento, String operacion) {
		return mvc.post().uri("/api/eventos/{id}/{operacion}", idEvento, operacion)
				.with(administrador()).exchange();
	}

	private MvcTestResult postergar(Integer idEvento, LocalDate fecha) {
		return mvc.post().uri("/api/eventos/{id}/postergacion", idEvento)
				.with(administrador()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"fecha\":\"%s\"}".formatted(fecha)).exchange();
	}

	private String enLaBase(Integer idEvento, String columna) {
		return jdbc.queryForObject("SELECT " + columna + " FROM evento WHERE id_evento = ?", String.class, idEvento);
	}

	@Test
	void unClienteNoCambiaElEstado() throws Exception {
		Integer id = registrarEvento(hoy().plusDays(10), "22:00", "03:00");
		MvcTestResult resultado = mvc.post().uri("/api/eventos/{id}/activacion", id)
				.with(cliente()).exchange();

		assertThat(resultado).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.post().uri("/api/eventos/{id}/activacion", id).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(enLaBase(id, "estado")).isEqualTo("BORRADOR");
	}

	// RF-5, RF-6 y RF-8: sin tipos no se activa; con un tipo sí, y dos veces no
	@Test
	void activaUnBorradorSoloConTiposYUnaSolaVez() throws Exception {
		Integer id = registrarEvento(hoy().plusDays(10), "22:00", "03:00");

		MvcTestResult sinTipos = operar(id, "activacion");
		agregarTipo(id);
		MvcTestResult conTipos = operar(id, "activacion");
		MvcTestResult deNuevo = operar(id, "activacion");

		assertThat(sinTipos).hasStatus(HttpStatus.CONFLICT);
		assertThat(sinTipos).bodyJson().extractingPath("$.codigo").isEqualTo("EVENTO_SIN_TIPOS_DE_ENTRADA");
		assertThat(conTipos).bodyJson().extractingPath("$.estado").isEqualTo("ACTIVO");
		assertThat(conTipos).bodyJson().extractingPath("$.ventaAbierta").isEqualTo(true);
		assertThat(deNuevo).hasStatus(HttpStatus.CONFLICT);
		assertThat(deNuevo).bodyJson().extractingPath("$.codigo").isEqualTo("CAMBIO_DE_ESTADO_NO_PERMITIDO");
	}

	// RF-9 a RF-13: postergar dos veces conserva la primera fecha original, y volver a activar
	@Test
	void postergaDosVecesYVuelveAActivar() throws Exception {
		LocalDate fecha = hoy().plusDays(10);
		Integer id = registrarEvento(fecha, "22:00", "03:00");
		agregarTipo(id);
		assertThat(operar(id, "activacion")).hasStatusOk();

		MvcTestResult primera = postergar(id, fecha.plusDays(5));
		MvcTestResult segunda = postergar(id, fecha.plusDays(20));
		MvcTestResult misma = postergar(id, fecha.plusDays(20));
		MvcTestResult ayer = postergar(id, hoy().minusDays(1));
		MvcTestResult reactivado = operar(id, "activacion");

		assertThat(primera).bodyJson().extractingPath("$.estado").isEqualTo("POSTERGADO");
		assertThat(primera).bodyJson().extractingPath("$.fechaOriginal").isEqualTo(fecha.toString());
		assertThat(segunda).bodyJson().extractingPath("$.fechaOriginal").isEqualTo(fecha.toString());
		assertThat(segunda).bodyJson().extractingPath("$.fecha").isEqualTo(fecha.plusDays(20).toString());
		assertThat(misma).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(misma).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("fecha");
		assertThat(ayer).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(reactivado).bodyJson().extractingPath("$.estado").isEqualTo("ACTIVO");
		assertThat(reactivado).bodyJson().extractingPath("$.ventaAbierta").isEqualTo(true);
	}

	// RF-17 y RF-18: cerrar la venta a mano, dos veces
	@Test
	void cierraLaVentaAManoSinCambiarElEstado() throws Exception {
		Integer id = registrarEvento(hoy().plusDays(10), "22:00", "03:00");
		agregarTipo(id);
		assertThat(operar(id, "activacion")).hasStatusOk();

		MvcTestResult cierre = operar(id, "cierre-de-venta");
		MvcTestResult otraVez = operar(id, "cierre-de-venta");

		assertThat(cierre).bodyJson().extractingPath("$.estado").isEqualTo("ACTIVO");
		assertThat(cierre).bodyJson().extractingPath("$.ventaAbierta").isEqualTo(false);
		assertThat(otraVez).hasStatus(HttpStatus.CONFLICT);
		assertThat(otraVez).bodyJson().extractingPath("$.codigo").isEqualTo("VENTA_YA_CERRADA");
	}

	// RF-14 a RF-16: cancelar cierra la venta, conserva los tipos y es definitivo
	@Test
	void cancelarEsDefinitivoYConservaLosTipos() throws Exception {
		Integer id = registrarEvento(hoy().plusDays(10), "22:00", "03:00");
		agregarTipo(id);
		assertThat(operar(id, "activacion")).hasStatusOk();

		MvcTestResult cancelacion = operar(id, "cancelacion");

		assertThat(cancelacion).bodyJson().extractingPath("$.estado").isEqualTo("CANCELADO");
		assertThat(cancelacion).bodyJson().extractingPath("$.ventaAbierta").isEqualTo(false);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tipo_entrada WHERE id_evento = ?", Integer.class, id))
				.isEqualTo(1);
		for (String operacion : new String[] { "activacion", "cancelacion", "cierre-de-venta" }) {
			MvcTestResult resultado = operar(id, operacion);
			assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
			assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("EVENTO_CANCELADO");
		}
		assertThat(postergar(id, hoy().plusDays(30))).hasStatus(HttpStatus.CONFLICT);
	}

	// RF-20 a RF-23: el cierre automático cierra la venta vencida y deja el estado
	@Test
	void elCierreAutomaticoCierraSoloLaVentaVencida() throws Exception {
		Integer vencido = registrarEvento(hoy(), "10:00", "18:00");
		agregarTipo(vencido);
		assertThat(operar(vencido, "activacion")).hasStatusOk();
		// Ayer a las 18:00 ya pasó a cualquier hora del día: así la prueba no depende del reloj
		jdbc.update("UPDATE evento SET fecha = ? WHERE id_evento = ?", hoy().minusDays(1), vencido);
		Integer futuro = registrarEvento(hoy().plusDays(10), "22:00", "03:00");
		agregarTipo(futuro);
		assertThat(operar(futuro, "activacion")).hasStatusOk();

		int cerrados = gestionEstadoEventosService.cerrarVentasVencidas();

		assertThat(cerrados).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT venta_abierta FROM evento WHERE id_evento = ?", Boolean.class,
				vencido)).isFalse();
		assertThat(enLaBase(vencido, "estado")).isEqualTo("ACTIVO");
		assertThat(jdbc.queryForObject("SELECT venta_abierta FROM evento WHERE id_evento = ?", Boolean.class,
				futuro)).isTrue();
	}
}
