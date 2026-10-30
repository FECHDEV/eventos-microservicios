package com.sarao.eventos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.math.BigDecimal;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.sarao.eventos.SesionDelGateway;
import com.sarao.eventos.config.SecurityConfig;
import com.sarao.eventos.dto.TipoEntradaRequest;
import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.entity.TipoEntrada;
import com.sarao.eventos.exception.CamposInvalidosException;
import com.sarao.eventos.exception.CamposInvalidosException.CampoInvalido;
import com.sarao.eventos.exception.EventoCanceladoException;
import com.sarao.eventos.exception.EventoNoEncontradoException;
import com.sarao.eventos.exception.TipoEntradaDuplicadoException;
import com.sarao.eventos.exception.TipoEntradaNoEncontradoException;
import com.sarao.eventos.service.GestionTiposEntradaService;

@WebMvcTest(TiposEntradaController.class)
@ActiveProfiles("test")
@Import(SecurityConfig.class)
class TiposEntradaControllerTest {

	private static final String TIPOS = "/api/eventos/5/tipos-entrada";
	private static final LocalDateTime REGISTRO = LocalDateTime.of(2026, 9, 1, 8, 0);
	private static final String GENERAL = """
			{"nombre":"General","precio":50,"cantidadTotal":100}
			""";

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private GestionTiposEntradaService gestionTiposEntradaService;

	private final RequestPostProcessor admin = SesionDelGateway.administrador();
	private final RequestPostProcessor ana = SesionDelGateway.cliente();
	private TipoEntrada general;

	@BeforeEach
	void preparar() {
		Evento jazz = Evento.registrar(1L, "Noche de jazz", null, LocalDate.of(2026, 10, 4), LocalTime.of(22, 0),
				LocalTime.of(3, 0), "Teatro Municipal", null, null);
		general = TipoEntrada.registrar(jazz, "General", new BigDecimal("50"), 100);
		ReflectionTestUtils.setField(general, "idTipoEntrada", 3L);
		ReflectionTestUtils.setField(general, "cantidadDisponible", 70);
	}

	private MvcTestResult registrar(String cuerpo, RequestPostProcessor sesion) {
		return mvc.post().uri(TIPOS).with(sesion)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
	}

	private MvcTestResult modificar(String cuerpo) {
		return mvc.put().uri(TIPOS + "/3").with(admin)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
	}

	private static void tieneCodigo(MvcTestResult resultado, HttpStatus estado, String codigo) {
		assertThat(resultado).hasStatus(estado);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(codigo);
	}

	// --- Acceso (RF-1, RF-2) ---

	@Test
	void sinSesionDevuelve401() {
		tieneCodigo(mvc.get().uri(TIPOS).exchange(), HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO");
		then(gestionTiposEntradaService).shouldHaveNoInteractions();
	}

	@Test
	void unClienteRecibe403() {
		tieneCodigo(mvc.get().uri(TIPOS).with(ana).exchange(),
				HttpStatus.FORBIDDEN, "ACCESO_DENEGADO");
		tieneCodigo(registrar(GENERAL, ana), HttpStatus.FORBIDDEN, "ACCESO_DENEGADO");
		then(gestionTiposEntradaService).shouldHaveNoInteractions();
	}

	// --- Consultar (RF-3, RF-4, RF-6) ---

	@Test
	void listaConPrecioYCantidades() throws Exception {
		given(gestionTiposEntradaService.listar(5L)).willReturn(List.of(general));

		MvcTestResult resultado = mvc.get().uri(TIPOS).with(admin).exchange();

		assertThat(resultado).hasStatusOk();
		assertThat(resultado).bodyJson().extractingPath("$[0].idTipoEntrada").isEqualTo(3);
		assertThat(resultado).bodyJson().extractingPath("$[0].nombre").isEqualTo("General");
		assertThat(resultado).bodyJson().extractingPath("$[0].cantidadTotal").isEqualTo(100);
		assertThat(resultado).bodyJson().extractingPath("$[0].cantidadDisponible").isEqualTo(70);
		// Con sus 2 decimales, como se guarda
		assertThat(resultado.getResponse().getContentAsString()).contains("\"precio\":50.00");
	}

	@Test
	void unEventoSinTiposDevuelveUnaListaVacia() {
		given(gestionTiposEntradaService.listar(5L)).willReturn(List.of());

		MvcTestResult resultado = mvc.get().uri(TIPOS).with(admin).exchange();

		assertThat(resultado).hasStatusOk();
		assertThat(resultado).bodyJson().isEqualTo("[]");
	}

	@Test
	void unEventoInexistenteEs404() {
		given(gestionTiposEntradaService.listar(5L)).willThrow(new EventoNoEncontradoException());

		tieneCodigo(mvc.get().uri(TIPOS).with(admin).exchange(),
				HttpStatus.NOT_FOUND, "EVENTO_NO_ENCONTRADO");
	}

	// --- Registrar y modificar (RF-7 a RF-20) ---

	@Test
	void registrarDevuelve201ConElTipo() {
		given(gestionTiposEntradaService.registrar(eq(5L), any())).willReturn(general);

		MvcTestResult resultado = registrar(GENERAL, admin);

		assertThat(resultado).hasStatus(HttpStatus.CREATED);
		assertThat(resultado).bodyJson().extractingPath("$.idTipoEntrada").isEqualTo(3);
		then(gestionTiposEntradaService).should().registrar(5L,
				new TipoEntradaRequest("General", new BigDecimal("50"), 100));
	}

	// RF-8, RF-10 y RF-11: todos los campos a corregir, con el formato de CU-01
	@Test
	void registrarConDatosInvalidosIndicaLosCampos() {
		MvcTestResult resultado = registrar("{\"nombre\":\" \",\"precio\":0,\"cantidadTotal\":0}", admin);

		assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("DATOS_INVALIDOS");
		assertThat(resultado).bodyJson().extractingPath("$.errores[*].campo").asArray()
				.containsExactlyInAnyOrder("nombre", "precio", "cantidadTotal");
		then(gestionTiposEntradaService).shouldHaveNoInteractions();
	}

	// Un precio que no es número y una cantidad que no entra en un entero se rechazan antes de validar
	@Test
	void unValorConFormatoInvalidoIndicaSuCampo() {
		MvcTestResult precio = registrar(GENERAL.replace("50", "\"cincuenta\""), admin);
		MvcTestResult cantidad = registrar(GENERAL.replace("100", "3000000000"), admin);

		assertThat(precio).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(precio).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("precio");
		assertThat(cantidad).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(cantidad).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("cantidadTotal");
		then(gestionTiposEntradaService).shouldHaveNoInteractions();
	}

	// Una cantidad con decimales no se trunca en silencio (spring.jackson.deserialization.accept-float-as-int)
	@Test
	void unaCantidadConDecimalesSeRechaza() {
		MvcTestResult cantidad = registrar(GENERAL.replace("100", "100.5"), admin);

		assertThat(cantidad).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(cantidad).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("cantidadTotal");
		then(gestionTiposEntradaService).shouldHaveNoInteractions();
	}

	// RF-18: la cantidad disponible que llegue en la petición se ignora
	@Test
	void modificarIgnoraLaCantidadDisponible() {
		given(gestionTiposEntradaService.modificar(eq(5L), eq(3L), any())).willReturn(general);

		MvcTestResult resultado = modificar(GENERAL.replace("}", ",\"cantidadDisponible\":999}"));

		assertThat(resultado).hasStatusOk();
		then(gestionTiposEntradaService).should().modificar(5L, 3L,
				new TipoEntradaRequest("General", new BigDecimal("50"), 100));
	}

	// Los errores del servicio salen con su código (RF-12, RF-15, RF-17, RF-20)
	@Test
	void losErroresDelServicioSalenConSuCodigo() {
		given(gestionTiposEntradaService.modificar(eq(5L), eq(3L), any()))
				.willThrow(new TipoEntradaNoEncontradoException())
				.willThrow(new TipoEntradaDuplicadoException())
				.willThrow(new EventoCanceladoException())
				.willThrow(new CamposInvalidosException(List.of(new CampoInvalido("cantidadTotal",
						"no puede ser menor que 30, lo ya vendido y reservado"))));

		tieneCodigo(modificar(GENERAL), HttpStatus.NOT_FOUND, "TIPO_ENTRADA_NO_ENCONTRADO");
		tieneCodigo(modificar(GENERAL), HttpStatus.CONFLICT, "TIPO_ENTRADA_DUPLICADO");
		tieneCodigo(modificar(GENERAL), HttpStatus.CONFLICT, "EVENTO_CANCELADO");
		MvcTestResult minimo = modificar(GENERAL);
		tieneCodigo(minimo, HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS");
		assertThat(minimo).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("cantidadTotal");
		assertThat(minimo).bodyJson().extractingPath("$.errores[0].mensaje")
				.isEqualTo("no puede ser menor que 30, lo ya vendido y reservado");
	}
}
