package com.sarao.eventos;

import static com.sarao.eventos.SesionDelGateway.administrador;
import static com.sarao.eventos.SesionDelGateway.cliente;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TiposEntradaIntegracionTest {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private Integer idEvento;

	@BeforeEach
	void prepararEvento() throws Exception {
		idEvento = registrarEvento("Noche de jazz");
	}

	@AfterEach
	void limpiar() {
		jdbc.update("DELETE FROM tipo_entrada");
		jdbc.update("DELETE FROM evento");
	}

	private Integer registrarEvento(String nombre) throws Exception {
		LocalDate fecha = LocalDate.now(ZoneId.of("America/La_Paz")).plusDays(10);
		MvcTestResult alta = mvc.post().uri("/api/eventos").with(administrador())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nombre":"%s","fecha":"%s","horaInicio":"22:00","horaFin":"03:00","lugar":"Teatro Municipal"}
						""".formatted(nombre, fecha)).exchange();
		assertThat(alta).hasStatus(HttpStatus.CREATED);
		return JsonPath.read(alta.getResponse().getContentAsString(), "$.idEvento");
	}

	private static String tipo(String nombre, String precio, int cantidadTotal) {
		return "{\"nombre\":\"%s\",\"precio\":%s,\"cantidadTotal\":%d}".formatted(nombre, precio, cantidadTotal);
	}

	private MvcTestResult listar(Integer evento) {
		return mvc.get().uri("/api/eventos/{id}/tipos-entrada", evento)
				.with(administrador()).exchange();
	}

	private MvcTestResult registrar(Integer evento, String cuerpo) {
		return mvc.post().uri("/api/eventos/{id}/tipos-entrada", evento)
				.with(administrador()).contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo).exchange();
	}

	private MvcTestResult modificar(Integer evento, Integer tipo, String cuerpo) {
		return mvc.put().uri("/api/eventos/{id}/tipos-entrada/{tipo}", evento, tipo)
				.with(administrador()).contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo).exchange();
	}

	private Integer registrarYObtenerId(Integer evento, String cuerpo) throws Exception {
		MvcTestResult alta = registrar(evento, cuerpo);
		assertThat(alta).hasStatus(HttpStatus.CREATED);
		return JsonPath.read(alta.getResponse().getContentAsString(), "$.idTipoEntrada");
	}

	@Test
	void unClienteNoGestionaTipos() throws Exception {
		MvcTestResult resultado = mvc.get().uri("/api/eventos/{id}/tipos-entrada", idEvento)
				.with(cliente()).exchange();

		assertThat(resultado).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.get().uri("/api/eventos/{id}/tipos-entrada", idEvento).exchange())
				.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// RF-4, RF-7, RF-12 y RF-6: sin tipos, alta, nombre repetido y evento inexistente
	@Test
	void registraListaYRechazaUnNombreRepetido() throws Exception {
		assertThat(listar(idEvento)).bodyJson().isEqualTo("[]");

		Integer id = registrarYObtenerId(idEvento, tipo("General", "10.5", 100));
		MvcTestResult repetido = registrar(idEvento, tipo(" general ", "80", 5));
		Integer otroEvento = registrarEvento("Feria del libro");

		assertThat(repetido).hasStatus(HttpStatus.CONFLICT);
		assertThat(repetido).bodyJson().extractingPath("$.codigo").isEqualTo("TIPO_ENTRADA_DUPLICADO");
		assertThat(registrar(otroEvento, tipo("General", "80", 5))).hasStatus(HttpStatus.CREATED);
		MvcTestResult lista = listar(idEvento);
		assertThat(lista).bodyJson().extractingPath("$.length()").isEqualTo(1);
		assertThat(lista).bodyJson().extractingPath("$[0].idTipoEntrada").isEqualTo(id);
		assertThat(lista).bodyJson().extractingPath("$[0].cantidadDisponible").isEqualTo(100);
		assertThat(jdbc.queryForObject("SELECT precio FROM tipo_entrada WHERE id_tipo_entrada = ?", BigDecimal.class,
				id)).isEqualTo(new BigDecimal("10.50"));
		assertThat(listar(999_999)).hasStatus(HttpStatus.NOT_FOUND);
	}

	// RF-14 y RF-15, con 30 vendidas o reservadas puestas en la base mientras no exista CU-06
	@Test
	void modificaPrecioYCantidadSinBajarDeLoVendido() throws Exception {
		Integer id = registrarYObtenerId(idEvento, tipo("General", "50", 100));
		jdbc.update("UPDATE tipo_entrada SET cantidad_disponible = 70 WHERE id_tipo_entrada = ?", id);

		MvcTestResult pocas = modificar(idEvento, id, tipo("General", "50", 29));
		MvcTestResult justo = modificar(idEvento, id, tipo("General", "50", 30));
		MvcTestResult mas = modificar(idEvento, id, tipo("General", "65", 130));

		assertThat(pocas).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(pocas).bodyJson().extractingPath("$.errores[0].mensaje")
				.isEqualTo("no puede ser menor que 30, lo ya vendido y reservado");
		assertThat(justo).bodyJson().extractingPath("$.cantidadDisponible").isEqualTo(0);
		assertThat(mas).bodyJson().extractingPath("$.cantidadTotal").isEqualTo(130);
		assertThat(mas).bodyJson().extractingPath("$.cantidadDisponible").isEqualTo(100);
		assertThat(jdbc.queryForObject("SELECT precio FROM tipo_entrada WHERE id_tipo_entrada = ?", BigDecimal.class,
				id)).isEqualTo(new BigDecimal("65.00"));
	}

	// RF-5 y RF-20, con el estado puesto en la base mientras no exista CU-12
	@Test
	void unCanceladoNoSeTocaPeroSeConsulta() throws Exception {
		Integer id = registrarYObtenerId(idEvento, tipo("General", "50", 100));
		jdbc.update("UPDATE evento SET estado = 'CANCELADO' WHERE id_evento = ?", idEvento);

		MvcTestResult alta = registrar(idEvento, tipo("VIP", "120", 40));
		MvcTestResult cambio = modificar(idEvento, id, tipo("General", "60", 100));

		assertThat(alta).hasStatus(HttpStatus.CONFLICT);
		assertThat(alta).bodyJson().extractingPath("$.codigo").isEqualTo("EVENTO_CANCELADO");
		assertThat(cambio).hasStatus(HttpStatus.CONFLICT);
		assertThat(listar(idEvento)).bodyJson().extractingPath("$.length()").isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT precio FROM tipo_entrada WHERE id_tipo_entrada = ?", BigDecimal.class,
				id)).isEqualTo(new BigDecimal("50.00"));
	}

	// RF-17: un tipo no se modifica desde otro evento
	@Test
	void unTipoDeOtroEventoNoSeEncuentra() throws Exception {
		Integer id = registrarYObtenerId(idEvento, tipo("General", "50", 100));
		Integer otroEvento = registrarEvento("Feria del libro");

		MvcTestResult resultado = modificar(otroEvento, id, tipo("General", "1", 1));

		assertThat(resultado).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("TIPO_ENTRADA_NO_ENCONTRADO");
		assertThat(jdbc.queryForObject("SELECT cantidad_total FROM tipo_entrada WHERE id_tipo_entrada = ?",
				Integer.class, id)).isEqualTo(100);
	}
}
