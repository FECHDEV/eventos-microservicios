package com.sarao.eventos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.sarao.eventos.SesionDelGateway;
import com.sarao.eventos.config.SecurityConfig;
import com.sarao.eventos.dto.EventoRequest;
import com.sarao.eventos.entity.EstadoEvento;
import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.exception.CamposInvalidosException;
import com.sarao.eventos.exception.CamposInvalidosException.CampoInvalido;
import com.sarao.eventos.exception.EventoCanceladoException;
import com.sarao.eventos.exception.EventoNoEncontradoException;
import com.sarao.eventos.exception.FechaNoModificableException;
import com.sarao.eventos.exception.ImagenInvalidaException;
import com.sarao.eventos.exception.ImagenNoEncontradaException;
import com.sarao.eventos.service.FormatoImagen;
import com.sarao.eventos.service.GestionEventosService;
import com.sarao.eventos.service.ImagenEvento;

@WebMvcTest(EventosController.class)
@ActiveProfiles("test")
@Import(SecurityConfig.class)
class EventosControllerTest {

	private static final String EVENTOS = "/api/eventos";
	private static final LocalDateTime REGISTRO = LocalDateTime.of(2026, 9, 1, 8, 0);
	private static final String NOCHE_DE_JAZZ = """
			{"nombre":"Noche de jazz","descripcion":"Cuarteto","fecha":"2026-10-04","horaInicio":"22:00",
			"horaFin":"03:00","lugar":"Teatro Municipal","restriccionEdad":18,"limiteCompra":4}
			""";
	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13 };

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private GestionEventosService gestionEventosService;

	private final RequestPostProcessor admin = SesionDelGateway.administrador();
	private final RequestPostProcessor ana = SesionDelGateway.cliente();
	private Evento jazz;

	@BeforeEach
	void preparar() {
		jazz = Evento.registrar(1L, "Noche de jazz", "Cuarteto", LocalDate.of(2026, 10, 4), LocalTime.of(22, 0),
				LocalTime.of(3, 0), "Teatro Municipal", 18, 4);
		ReflectionTestUtils.setField(jazz, "idEvento", 5L);
	}

	private MvcTestResult registrar(String cuerpo, RequestPostProcessor sesion) {
		return mvc.post().uri(EVENTOS).with(sesion)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
	}

	private MvcTestResult modificar(String cuerpo) {
		return mvc.put().uri(EVENTOS + "/5").with(admin)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
	}

	private MvcTestResult subirImagen(MockMultipartFile archivo) {
		return mvc.post().uri(EVENTOS + "/5/imagen").with(admin).multipart()
				.file(archivo).exchange();
	}

	// --- Acceso (RF-1, RF-2) ---

	@Test
	void sinSesionDevuelve401() {
		MvcTestResult resultado = mvc.get().uri(EVENTOS).exchange();

		assertThat(resultado).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("NO_AUTENTICADO");
		then(gestionEventosService).shouldHaveNoInteractions();
	}

	@Test
	void unClienteRecibe403EnTodasLasOperaciones() {
		assertThat(mvc.get().uri(EVENTOS).with(ana).exchange())
				.hasStatus(HttpStatus.FORBIDDEN);
		MvcTestResult alta = registrar(NOCHE_DE_JAZZ, ana);
		assertThat(alta).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(alta).bodyJson().extractingPath("$.codigo").isEqualTo("ACCESO_DENEGADO");
		assertThat(mvc.get().uri(EVENTOS + "/5/imagen").with(ana).exchange())
				.hasStatus(HttpStatus.FORBIDDEN);
		then(gestionEventosService).shouldHaveNoInteractions();
	}

	// --- Consultar (RF-3 a RF-6) ---

	@Test
	void listaConLosDatosDeCadaFila() {
		given(gestionEventosService.listar(null, 0, 20))
				.willReturn(new PageImpl<>(List.of(jazz), PageRequest.of(0, 20), 21));

		MvcTestResult resultado = mvc.get().uri(EVENTOS).with(admin).exchange();

		assertThat(resultado).hasStatusOk();
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].idEvento").isEqualTo(5);
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].nombre").isEqualTo("Noche de jazz");
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].fecha").isEqualTo("2026-10-04");
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].horaInicio").isEqualTo("22:00:00");
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].horaFin").isEqualTo("03:00:00");
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].lugar").isEqualTo("Teatro Municipal");
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].estado").isEqualTo("BORRADOR");
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].ventaAbierta").isEqualTo(false);
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].tieneImagen").isEqualTo(false);
		assertThat(resultado).bodyJson().extractingPath("$.totalElementos").isEqualTo(21);
		assertThat(resultado).bodyJson().extractingPath("$.totalPaginas").isEqualTo(2);
	}

	@Test
	void filtraPorEstadoYPagina() {
		given(gestionEventosService.listar(EstadoEvento.ACTIVO, 1, 50)).willReturn(new PageImpl<>(List.of()));

		assertThat(mvc.get().uri(EVENTOS + "?estado=ACTIVO&pagina=1&tamano=50")
				.with(admin).exchange()).hasStatusOk();
		then(gestionEventosService).should().listar(EstadoEvento.ACTIVO, 1, 50);
	}

	@Test
	void unEstadoInexistenteEsDatosInvalidos() {
		MvcTestResult resultado = mvc.get().uri(EVENTOS + "?estado=TERMINADO")
				.with(admin).exchange();

		assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("DATOS_INVALIDOS");
		assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("estado");
	}

	@Test
	void muestraElDetalleCompleto() {
		given(gestionEventosService.obtener(5L)).willReturn(jazz);

		MvcTestResult resultado = mvc.get().uri(EVENTOS + "/5").with(admin)
				.exchange();

		assertThat(resultado).hasStatusOk();
		assertThat(resultado).bodyJson().extractingPath("$.descripcion").isEqualTo("Cuarteto");
		assertThat(resultado).bodyJson().extractingPath("$.restriccionEdad").isEqualTo(18);
		assertThat(resultado).bodyJson().extractingPath("$.limiteCompra").isEqualTo(4);
		assertThat(resultado).bodyJson().extractingPath("$.fechaOriginal").isNull();
	}

	@Test
	void unEventoInexistenteEs404() {
		given(gestionEventosService.obtener(9L)).willThrow(new EventoNoEncontradoException());

		MvcTestResult resultado = mvc.get().uri(EVENTOS + "/9").with(admin)
				.exchange();

		assertThat(resultado).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("EVENTO_NO_ENCONTRADO");
	}

	// --- Registrar y modificar (RF-7 a RF-21) ---

	@Test
	void registrarDevuelve201ConElEventoYElAdministradorDeLaSesion() {
		given(gestionEventosService.registrar(eq(1L), any())).willReturn(jazz);

		MvcTestResult resultado = registrar(NOCHE_DE_JAZZ, admin);

		assertThat(resultado).hasStatus(HttpStatus.CREATED);
		assertThat(resultado).bodyJson().extractingPath("$.idEvento").isEqualTo(5);
		then(gestionEventosService).should().registrar(1L, new EventoRequest("Noche de jazz", "Cuarteto",
				LocalDate.of(2026, 10, 4), LocalTime.of(22, 0), LocalTime.of(3, 0), "Teatro Municipal", 18, 4));
	}

	// RF-8: todos los campos a corregir, con el formato de CU-01
	@Test
	void registrarSinDatosIndicaLosCampos() {
		MvcTestResult resultado = registrar("{\"restriccionEdad\":0}", admin);

		assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("DATOS_INVALIDOS");
		assertThat(resultado).bodyJson().extractingPath("$.errores[*].campo").asArray().containsExactlyInAnyOrder(
				"nombre", "fecha", "horaInicio", "horaFin", "lugar", "restriccionEdad");
		then(gestionEventosService).shouldHaveNoInteractions();
	}

	@Test
	void unaHoraMalEscritaEsDatosInvalidos() {
		MvcTestResult resultado = registrar(NOCHE_DE_JAZZ.replace("\"22:00\"", "\"25:00\""), admin);

		assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("horaInicio");
	}

	// RF-10 y RF-11 salen del servicio con el mismo formato que @Valid
	@Test
	void lasReglasDeFechaYHoraDelServicioSalenComoCampos() {
		given(gestionEventosService.registrar(anyLong(), any())).willThrow(new CamposInvalidosException(
				List.of(new CampoInvalido("fecha", "no puede ser anterior a hoy"))));

		MvcTestResult resultado = registrar(NOCHE_DE_JAZZ, admin);

		assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("DATOS_INVALIDOS");
		assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("fecha");
		assertThat(resultado).bodyJson().extractingPath("$.errores[0].mensaje").isEqualTo("no puede ser anterior a hoy");
	}

	// RF-20: lo que no es del formulario se ignora
	@Test
	void modificarIgnoraEstadoYVenta() {
		given(gestionEventosService.modificar(eq(5L), any())).willReturn(jazz);

		assertThat(modificar(NOCHE_DE_JAZZ.replace("{", "{\"estado\":\"ACTIVO\",\"ventaAbierta\":true,"))).hasStatusOk();
		then(gestionEventosService).should().modificar(5L, new EventoRequest("Noche de jazz", "Cuarteto",
				LocalDate.of(2026, 10, 4), LocalTime.of(22, 0), LocalTime.of(3, 0), "Teatro Municipal", 18, 4));
	}

	// RF-17 y RF-18
	@Test
	void losRechazosPorEstadoSon409() {
		given(gestionEventosService.modificar(eq(5L), any())).willThrow(new FechaNoModificableException())
				.willThrow(new EventoCanceladoException());

		MvcTestResult fecha = modificar(NOCHE_DE_JAZZ);
		MvcTestResult cancelado = modificar(NOCHE_DE_JAZZ);

		assertThat(fecha).hasStatus(HttpStatus.CONFLICT);
		assertThat(fecha).bodyJson().extractingPath("$.codigo").isEqualTo("FECHA_NO_MODIFICABLE");
		assertThat(cancelado).hasStatus(HttpStatus.CONFLICT);
		assertThat(cancelado).bodyJson().extractingPath("$.codigo").isEqualTo("EVENTO_CANCELADO");
	}

	// --- Imagen (RF-22 a RF-29) ---

	@Test
	void subirLaImagenPasaSusBytesAlServicio() {
		jazz.cambiarImagen("5f2c1d3e-0000-4000-8000-000000000000.png");
		given(gestionEventosService.cambiarImagen(5L, PNG)).willReturn(jazz);

		MvcTestResult resultado = subirImagen(new MockMultipartFile("imagen", "foto.png", "image/png", PNG));

		assertThat(resultado).hasStatusOk();
		assertThat(resultado).bodyJson().extractingPath("$.tieneImagen").isEqualTo(true);
	}

	// RF-29: el nombre y el tipo que declara el archivo no importan
	@Test
	void elNombreYElTipoDeclaradoDelArchivoSeIgnoran() {
		given(gestionEventosService.cambiarImagen(5L, PNG)).willReturn(jazz);

		assertThat(subirImagen(new MockMultipartFile("imagen", "../../etc/passwd", "text/plain", PNG))).hasStatusOk();
		then(gestionEventosService).should().cambiarImagen(5L, PNG);
	}

	@Test
	void sinArchivoIndicaElCampo() {
		MvcTestResult resultado = mvc.post().uri(EVENTOS + "/5/imagen").with(admin)
				.multipart().file("otra", PNG).exchange();

		assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("DATOS_INVALIDOS");
		assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("imagen");
	}

	@Test
	void unArchivoQueNoEsImagenEsImagenInvalida() {
		given(gestionEventosService.cambiarImagen(eq(5L), any())).willThrow(new ImagenInvalidaException());

		MvcTestResult resultado = subirImagen(new MockMultipartFile("imagen", "foto.jpg", "image/jpeg", PNG));

		assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("IMAGEN_INVALIDA");
	}

	// RF-24: el límite de multipart corta antes de llegar al controlador
	@Test
	void unArchivoQueSuperaElLimiteDeMultipartEsDemasiadoGrande() {
		given(gestionEventosService.cambiarImagen(eq(5L), any()))
				.willThrow(new MaxUploadSizeExceededException(2 * 1024 * 1024));

		MvcTestResult resultado = subirImagen(new MockMultipartFile("imagen", "foto.png", "image/png", PNG));

		assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("IMAGEN_DEMASIADO_GRANDE");
	}

	@Test
	void quitarLaImagen() {
		given(gestionEventosService.quitarImagen(5L)).willReturn(jazz);

		assertThat(mvc.delete().uri(EVENTOS + "/5/imagen").with(admin)
				.exchange()).hasStatusOk();
		then(gestionEventosService).should().quitarImagen(5L);
	}

	// RF-28
	@Test
	void sirveLaImagenConSuTipo() {
		given(gestionEventosService.leerImagen(5L)).willReturn(new ImagenEvento(PNG, FormatoImagen.PNG));

		MvcTestResult resultado = mvc.get().uri(EVENTOS + "/5/imagen").with(admin)
				.exchange();

		assertThat(resultado).hasStatusOk();
		assertThat(resultado).headers().hasValue(HttpHeaders.CONTENT_TYPE, "image/png");
		assertThat(resultado).headers().hasValue("X-Content-Type-Options", "nosniff");
		assertThat(resultado.getResponse().getContentAsByteArray()).isEqualTo(PNG);
	}

	@Test
	void sinImagenEs404() {
		given(gestionEventosService.leerImagen(5L)).willThrow(new ImagenNoEncontradaException());

		MvcTestResult resultado = mvc.get().uri(EVENTOS + "/5/imagen").with(admin)
				.exchange();

		assertThat(resultado).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("IMAGEN_NO_ENCONTRADA");
	}
}
