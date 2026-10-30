package com.sarao.eventos;

import static com.sarao.eventos.SesionDelGateway.administrador;
import static com.sarao.eventos.SesionDelGateway.cliente;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
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
class GestionEventosIntegracionTest {

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13 };
	private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F' };

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Value("${eventos.imagenes.directorio}")
	private Path carpetaImagenes;

	@AfterEach
	void limpiar() {
		jdbc.update("DELETE FROM tipo_entrada");
		jdbc.update("DELETE FROM evento");
	}

	private static String evento(String nombre, LocalDate fecha, String inicio, String fin) {
		return """
				{"nombre":"%s","fecha":"%s","horaInicio":"%s","horaFin":"%s","lugar":"Teatro Municipal"}
				""".formatted(nombre, fecha, inicio, fin);
	}

	private MvcTestResult registrar(String cuerpo) {
		return mvc.post().uri("/api/eventos").with(administrador())
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
	}

	private Integer registrarYObtenerId(String cuerpo) throws Exception {
		MvcTestResult alta = registrar(cuerpo);
		assertThat(alta).hasStatus(HttpStatus.CREATED);
		return JsonPath.read(alta.getResponse().getContentAsString(), "$.idEvento");
	}

	private MvcTestResult subir(Integer id, byte[] contenido) {
		return mvc.post().uri("/api/eventos/{id}/imagen", id).with(administrador())
				.multipart().file(new MockMultipartFile("imagen", "foto", "application/octet-stream", contenido))
				.exchange();
	}

	private String imagenEnLaBase(Integer id) {
		return jdbc.queryForObject("SELECT imagen FROM evento WHERE id_evento = ?", String.class, id);
	}

	private static LocalDate hoy() {
		return LocalDate.now(ZoneId.of("America/La_Paz"));
	}

	@Test
	void unClienteNoGestionaEventos() throws Exception {
		MvcTestResult resultado = mvc.get().uri("/api/eventos")
				.with(cliente()).exchange();

		assertThat(resultado).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.get().uri("/api/eventos").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// RF-7, RF-11 y RF-3: se registra en borrador, cruzando la medianoche, y aparece en la lista
	@Test
	void registraUnBorradorQueCruzaLaMedianocheYLoLista() throws Exception {
		Integer id = registrarYObtenerId(evento("Noche de jazz", hoy().plusDays(10), "22:00", "03:00"));

		MvcTestResult lista = mvc.get().uri("/api/eventos?estado=BORRADOR")
				.with(administrador()).exchange();

		assertThat(lista).bodyJson().extractingPath("$.contenido[0].idEvento").isEqualTo(id);
		assertThat(lista).bodyJson().extractingPath("$.contenido[0].horaFin").isEqualTo("03:00:00");
		assertThat(jdbc.queryForObject("SELECT estado FROM evento WHERE id_evento = ?", String.class, id))
				.isEqualTo("BORRADOR");
		assertThat(jdbc.queryForObject("SELECT venta_abierta FROM evento WHERE id_evento = ?", Boolean.class, id))
				.isFalse();
		assertThat(jdbc.queryForObject("SELECT id_usuario FROM evento WHERE id_evento = ?", Long.class, id)).isEqualTo(1L);
	}

	// FA-01: fecha pasada y horas iguales en una sola respuesta
	@Test
	void rechazaFechaPasadaYHorasIguales() {
		MvcTestResult resultado = registrar(evento("Feria", hoy().minusDays(1), "10:00", "10:00"));

		assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(resultado).bodyJson().extractingPath("$.errores[*].campo").asArray()
				.containsExactly("fecha", "horaFin");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento", Integer.class)).isZero();
	}

	// RF-16 y RF-17: un activo cambia su descripción pero no su fecha
	@Test
	void unActivoCambiaSusDatosPeroNoSuFecha() throws Exception {
		LocalDate fecha = hoy().plusDays(10);
		Integer id = registrarYObtenerId(evento("Noche de jazz", fecha, "22:00", "03:00"));
		jdbc.update("UPDATE evento SET estado = 'ACTIVO' WHERE id_evento = ?", id);

		MvcTestResult descripcion = mvc.put().uri("/api/eventos/{id}", id)
				.with(administrador()).contentType(MediaType.APPLICATION_JSON)
				.content(evento("Noche de jazz", fecha, "22:00", "03:00").replace("}", ",\"descripcion\":\"Nueva\"}"))
				.exchange();
		MvcTestResult otraFecha = mvc.put().uri("/api/eventos/{id}", id)
				.with(administrador()).contentType(MediaType.APPLICATION_JSON)
				.content(evento("Noche de jazz", fecha.plusDays(1), "22:00", "03:00")).exchange();

		assertThat(descripcion).hasStatusOk();
		assertThat(descripcion).bodyJson().extractingPath("$.descripcion").isEqualTo("Nueva");
		assertThat(otraFecha).hasStatus(HttpStatus.CONFLICT);
		assertThat(otraFecha).bodyJson().extractingPath("$.codigo").isEqualTo("FECHA_NO_MODIFICABLE");
		assertThat(jdbc.queryForObject("SELECT fecha FROM evento WHERE id_evento = ?", LocalDate.class, id))
				.isEqualTo(fecha);
	}

	// RF-22, RF-25, RF-26 y RF-28: subir, reemplazar (el archivo anterior desaparece), servir y quitar
	@Test
	void subeReemplazaSirveYQuitaLaImagen() throws Exception {
		Integer id = registrarYObtenerId(evento("Feria", hoy().plusDays(5), "10:00", "12:00"));

		assertThat(subir(id, PNG)).hasStatusOk();
		String primera = imagenEnLaBase(id);
		assertThat(carpetaImagenes.resolve(primera)).exists();

		assertThat(subir(id, JPEG)).hasStatusOk();
		String segunda = imagenEnLaBase(id);
		assertThat(segunda).endsWith(".jpg");
		assertThat(carpetaImagenes.resolve(primera)).doesNotExist();

		MvcTestResult servida = mvc.get().uri("/api/eventos/{id}/imagen", id)
				.with(administrador()).exchange();
		assertThat(servida).headers().hasValue(HttpHeaders.CONTENT_TYPE, "image/jpeg");
		assertThat(servida.getResponse().getContentAsByteArray()).isEqualTo(JPEG);

		assertThat(mvc.delete().uri("/api/eventos/{id}/imagen", id)
				.with(administrador()).exchange()).hasStatusOk();
		assertThat(imagenEnLaBase(id)).isNull();
		assertThat(carpetaImagenes.resolve(segunda)).doesNotExist();
		assertThat(mvc.get().uri("/api/eventos/{id}/imagen", id)
				.with(administrador()).exchange()).hasStatus(HttpStatus.NOT_FOUND);
	}

	// RF-23: un archivo que dice ser .jpg pero es otra cosa no se guarda
	@Test
	void unaImagenFalsaNoSeGuarda() throws Exception {
		Integer id = registrarYObtenerId(evento("Feria", hoy().plusDays(5), "10:00", "12:00"));
		long antes = contarArchivos();

		MvcTestResult resultado = subir(id, "<svg onload=alert(1)/>".getBytes());

		assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("IMAGEN_INVALIDA");
		assertThat(imagenEnLaBase(id)).isNull();
		assertThat(contarArchivos()).isEqualTo(antes);
	}

	private long contarArchivos() throws IOException {
		try (var archivos = Files.list(carpetaImagenes)) {
			return archivos.count();
		}
	}
}
