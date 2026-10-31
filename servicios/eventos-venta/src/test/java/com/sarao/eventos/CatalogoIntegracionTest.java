package com.sarao.eventos;

import static com.sarao.eventos.SesionDelGateway.administrador;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogoIntegracionTest {

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13 };
	private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/La_Paz"));

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void limpiar() {
		jdbc.update("DELETE FROM tipo_entrada");
		jdbc.update("DELETE FROM evento");
	}

	// Toda la preparación es POST a la API de administración; sin cuerpo, las acciones de CU-12
	private MvcTestResult post(String uri, String cuerpo) {
		var peticion = mvc.post().uri(uri).with(administrador());
		return cuerpo == null ? peticion.exchange()
				: peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
	}

	private Integer registrar(String nombre, String lugar, LocalDate fecha) throws Exception {
		MvcTestResult alta = post("/api/eventos", """
				{"nombre":"%s","fecha":"%s","horaInicio":"20:00","horaFin":"23:00","lugar":"%s"}
				""".formatted(nombre, fecha, lugar));
		assertThat(alta).hasStatus(HttpStatus.CREATED);
		return JsonPath.read(alta.getResponse().getContentAsString(), "$.idEvento");
	}

	private Integer activo(String nombre, String lugar, LocalDate fecha) throws Exception {
		Integer id = registrar(nombre, lugar, fecha);
		assertThat(post("/api/eventos/" + id + "/tipos-entrada",
				"{\"nombre\":\"General\",\"precio\":50.00,\"cantidadTotal\":100}")).hasStatus(HttpStatus.CREATED);
		assertThat(post("/api/eventos/" + id + "/activacion", null)).hasStatus(HttpStatus.OK);
		return id;
	}

	private List<String> nombresDelCatalogo(String consulta, Object... valores) throws Exception {
		MvcTestResult lista = mvc.get().uri("/api/catalogo" + consulta, valores).exchange();
		assertThat(lista).hasStatus(HttpStatus.OK);
		return JsonPath.read(lista.getResponse().getContentAsString(), "$.contenido[*].nombre");
	}

	// RF-1, RF-3, RF-4, RF-16 y RF-17: sin sesión, solo los visibles; los demás no existen ni por su id
	@Test
	void sinSesionSoloSeVenLosActivosYPostergadosQueNoTerminaron() throws Exception {
		activo("Activo", "Teatro", HOY.plusDays(5));
		Integer postergado = activo("Postergado", "Teatro", HOY.plusDays(6));
		assertThat(post("/api/eventos/" + postergado + "/postergacion",
				"{\"fecha\":\"%s\"}".formatted(HOY.plusDays(7)))).hasStatus(HttpStatus.OK);
		Integer borrador = registrar("Borrador", "Teatro", HOY.plusDays(5));
		Integer cancelado = activo("Cancelado", "Teatro", HOY.plusDays(5));
		assertThat(post("/api/eventos/" + cancelado + "/cancelacion", null)).hasStatus(HttpStatus.OK);
		Integer terminado = activo("Terminado", "Teatro", HOY.plusDays(5));
		jdbc.update("UPDATE evento SET fecha = ? WHERE id_evento = ?", HOY.minusDays(2), terminado);

		assertThat(nombresDelCatalogo("")).containsExactly("Activo", "Postergado");
		for (Integer oculto : List.of(borrador, cancelado, terminado)) {
			MvcTestResult detalle = mvc.get().uri("/api/catalogo/" + oculto).exchange();
			assertThat(detalle).hasStatus(HttpStatus.NOT_FOUND);
			assertThat(detalle).bodyJson().extractingPath("$.codigo").isEqualTo("EVENTO_NO_ENCONTRADO");
		}
	}

	// RF-7 a RF-10, contra la base real: el '%' se busca como texto
	@Test
	void filtraPorTextoYFechasYRechazaUnRangoInvertido() throws Exception {
		activo("Noche de jazz", "Teatro Municipal", HOY.plusDays(3));
		activo("Feria", "Jazz Club", HOY.plusDays(10));
		activo("Oferta 100%", "Estadio", HOY.plusDays(4));
		activo("Rock", "Estadio", HOY.plusDays(20));

		assertThat(nombresDelCatalogo("?texto=JAZZ")).containsExactly("Noche de jazz", "Feria");
		// Como variable de la plantilla: la URL se codifica una sola vez y el backend recibe «100%»
		assertThat(nombresDelCatalogo("?texto={texto}", "100%")).containsExactly("Oferta 100%");
		assertThat(nombresDelCatalogo("?desde=%s&hasta=%s".formatted(HOY.plusDays(4), HOY.plusDays(10))))
				.containsExactly("Oferta 100%", "Feria");
		assertThat(nombresDelCatalogo("?texto=estadio&desde=%s".formatted(HOY.plusDays(5))))
				.containsExactly("Rock");

		MvcTestResult invertido = mvc.get()
				.uri("/api/catalogo?desde=%s&hasta=%s".formatted(HOY.plusDays(5), HOY.plusDays(4))).exchange();
		assertThat(invertido).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(invertido).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("hasta");
	}

	// RF-14 y RF-15
	@Test
	void elDetalleMuestraLosTiposDisponiblesYAgotados() throws Exception {
		Integer id = activo("Noche de jazz", "Teatro Municipal", HOY.plusDays(3));
		assertThat(post("/api/eventos/" + id + "/tipos-entrada",
				"{\"nombre\":\"VIP\",\"precio\":150.00,\"cantidadTotal\":10}")).hasStatus(HttpStatus.CREATED);
		jdbc.update("UPDATE tipo_entrada SET cantidad_disponible = 0 WHERE nombre = 'VIP'");

		MvcTestResult detalle = mvc.get().uri("/api/catalogo/" + id).exchange();

		assertThat(detalle).hasStatus(HttpStatus.OK);
		assertThat(detalle).bodyJson().extractingPath("$.nombre").isEqualTo("Noche de jazz");
		assertThat(detalle).bodyJson().extractingPath("$.tiposEntrada[0].nombre").isEqualTo("General");
		assertThat(detalle).bodyJson().extractingPath("$.tiposEntrada[0].disponible").isEqualTo(true);
		assertThat(detalle).bodyJson().extractingPath("$.tiposEntrada[1].nombre").isEqualTo("VIP");
		assertThat(detalle).bodyJson().extractingPath("$.tiposEntrada[1].disponible").isEqualTo(false);
	}

	// RF-17: la imagen de un visible se ve sin sesión; la de un borrador, no
	@Test
	void laImagenEsPublicaSoloSiElEventoSeMuestra() throws Exception {
		Integer visible = activo("Noche de jazz", "Teatro Municipal", HOY.plusDays(3));
		Integer borrador = registrar("Borrador", "Teatro", HOY.plusDays(3));
		for (Integer id : List.of(visible, borrador)) {
			assertThat(mvc.post().uri("/api/eventos/{id}/imagen", id)
					.with(administrador()).multipart()
					.file(new MockMultipartFile("imagen", "foto", "application/octet-stream", PNG)).exchange())
					.hasStatus(HttpStatus.OK);
		}

		MvcTestResult imagen = mvc.get().uri("/api/catalogo/" + visible + "/imagen").exchange();
		assertThat(imagen).hasStatus(HttpStatus.OK);
		assertThat(imagen).hasContentType(MediaType.IMAGE_PNG);
		assertThat(mvc.get().uri("/api/catalogo/" + borrador + "/imagen").exchange())
				.hasStatus(HttpStatus.NOT_FOUND);
	}
}
