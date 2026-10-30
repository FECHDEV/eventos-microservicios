package com.sarao.eventos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import com.sarao.eventos.dto.PostergacionRequest;
import com.sarao.eventos.entity.EstadoEvento;
import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.exception.CambioDeEstadoNoPermitidoException;
import com.sarao.eventos.exception.CamposInvalidosException;
import com.sarao.eventos.exception.CamposInvalidosException.CampoInvalido;
import com.sarao.eventos.exception.EventoCanceladoException;
import com.sarao.eventos.exception.EventoNoEncontradoException;
import com.sarao.eventos.exception.EventoSinTiposDeEntradaException;
import com.sarao.eventos.exception.VentaYaCerradaException;
import com.sarao.eventos.service.EventoActivado;
import com.sarao.eventos.service.GestionEstadoEventosService;

@WebMvcTest(EstadoEventosController.class)
@ActiveProfiles("test")
@Import(SecurityConfig.class)
class EstadoEventosControllerTest {

	private static final String EVENTO = "/api/eventos/5";
	private static final LocalDateTime REGISTRO = LocalDateTime.of(2026, 9, 1, 8, 0);
	private static final LocalDate FECHA = LocalDate.of(2026, 10, 4);

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private GestionEstadoEventosService gestionEstadoEventosService;

	private final RequestPostProcessor admin = SesionDelGateway.administrador();
	private final RequestPostProcessor ana = SesionDelGateway.cliente();
	private Evento jazz;

	@BeforeEach
	void preparar() {
		jazz = Evento.registrar(1L, "Noche de jazz", null, FECHA, LocalTime.of(22, 0), LocalTime.of(3, 0),
				"Teatro Municipal", null, null);
		ReflectionTestUtils.setField(jazz, "idEvento", 5L);
	}

	private MvcTestResult operar(String operacion, RequestPostProcessor sesion) {
		return mvc.post().uri(EVENTO + "/" + operacion).with(sesion).exchange();
	}

	private MvcTestResult postergar(String cuerpo) {
		return mvc.post().uri(EVENTO + "/postergacion").with(admin)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
	}

	private static void tieneCodigo(MvcTestResult resultado, HttpStatus estado, String codigo) {
		assertThat(resultado).hasStatus(estado);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(codigo);
	}

	// --- Acceso (RF-1, RF-2) ---

	@Test
	void sinSesionDevuelve401() {
		tieneCodigo(mvc.post().uri(EVENTO + "/activacion").exchange(), HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO");
		then(gestionEstadoEventosService).shouldHaveNoInteractions();
	}

	@Test
	void unClienteRecibe403EnLasCuatroOperaciones() {
		tieneCodigo(operar("activacion", ana), HttpStatus.FORBIDDEN, "ACCESO_DENEGADO");
		tieneCodigo(operar("cancelacion", ana), HttpStatus.FORBIDDEN, "ACCESO_DENEGADO");
		tieneCodigo(operar("cierre-de-venta", ana), HttpStatus.FORBIDDEN, "ACCESO_DENEGADO");
		assertThat(mvc.post().uri(EVENTO + "/postergacion").with(ana)
				.contentType(MediaType.APPLICATION_JSON).content("{\"fecha\":\"2026-11-01\"}").exchange())
				.hasStatus(HttpStatus.FORBIDDEN);
		then(gestionEstadoEventosService).shouldHaveNoInteractions();
	}

	// --- Activar (RF-4 a RF-8) ---

	// FA-02 (RF-31): el mismo JSON del evento, sin anidar, más cuántas entradas se reactivaron
	@Test
	void activarInformaLasEntradasReactivadas() {
		jazz.activar();
		given(gestionEstadoEventosService.activar(5L)).willReturn(new EventoActivado(jazz, 2));

		MvcTestResult resultado = operar("activacion", admin);

		assertThat(resultado).hasStatusOk();
		assertThat(resultado).bodyJson().extractingPath("$.idEvento").isEqualTo(5);
		assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("ACTIVO");
		assertThat(resultado).bodyJson().extractingPath("$.entradasReactivadas").isEqualTo(2);
	}

	@Test
	void activarDevuelveElEventoActivoConLaVentaAbierta() {
		jazz.activar();
		given(gestionEstadoEventosService.activar(5L)).willReturn(new EventoActivado(jazz, 0));

		MvcTestResult resultado = operar("activacion", admin);

		assertThat(resultado).hasStatusOk();
		assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("ACTIVO");
		assertThat(resultado).bodyJson().extractingPath("$.ventaAbierta").isEqualTo(true);
	}

	@Test
	void losErroresDeActivarSalenConSuCodigo() {
		given(gestionEstadoEventosService.activar(5L)).willThrow(new EventoSinTiposDeEntradaException())
				.willThrow(new CambioDeEstadoNoPermitidoException())
				.willThrow(new EventoCanceladoException())
				.willThrow(new EventoNoEncontradoException());

		tieneCodigo(operar("activacion", admin), HttpStatus.CONFLICT, "EVENTO_SIN_TIPOS_DE_ENTRADA");
		tieneCodigo(operar("activacion", admin), HttpStatus.CONFLICT, "CAMBIO_DE_ESTADO_NO_PERMITIDO");
		tieneCodigo(operar("activacion", admin), HttpStatus.CONFLICT, "EVENTO_CANCELADO");
		tieneCodigo(operar("activacion", admin), HttpStatus.NOT_FOUND, "EVENTO_NO_ENCONTRADO");
	}

	// --- Postergar (RF-9 a RF-13) ---

	@Test
	void postergarMandaLaFechaNuevaYDevuelveLaOriginal() {
		jazz.activar();
		jazz.postergar(LocalDate.of(2026, 11, 1));
		given(gestionEstadoEventosService.postergar(eq(5L), any())).willReturn(jazz);

		MvcTestResult resultado = postergar("{\"fecha\":\"2026-11-01\"}");

		assertThat(resultado).hasStatusOk();
		assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("POSTERGADO");
		assertThat(resultado).bodyJson().extractingPath("$.fecha").isEqualTo("2026-11-01");
		assertThat(resultado).bodyJson().extractingPath("$.fechaOriginal").isEqualTo("2026-10-04");
		then(gestionEstadoEventosService).should().postergar(5L,
				new PostergacionRequest(LocalDate.of(2026, 11, 1)));
	}

	@Test
	void postergarSinFechaOConUnaFechaMalEscritaEsDatosInvalidos() {
		MvcTestResult sinFecha = postergar("{}");
		MvcTestResult malEscrita = postergar("{\"fecha\":\"2026-13-40\"}");

		assertThat(sinFecha).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(sinFecha).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("fecha");
		assertThat(malEscrita).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(malEscrita).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("fecha");
		then(gestionEstadoEventosService).shouldHaveNoInteractions();
	}

	// RF-11: la regla de la fecha sale del servicio con el formato de @Valid
	@Test
	void laReglaDeLaFechaDelServicioSaleComoCampo() {
		given(gestionEstadoEventosService.postergar(eq(5L), any())).willThrow(new CamposInvalidosException(
				List.of(new CampoInvalido("fecha", "debe ser distinta de la fecha actual del evento"))));

		MvcTestResult resultado = postergar("{\"fecha\":\"2026-10-04\"}");

		assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("DATOS_INVALIDOS");
		assertThat(resultado).bodyJson().extractingPath("$.errores[0].mensaje")
				.isEqualTo("debe ser distinta de la fecha actual del evento");
	}

	// --- Cancelar y cerrar la venta (RF-14 a RF-18) ---

	@Test
	void cancelarDevuelveElEventoCanceladoConLaVentaCerrada() {
		jazz.cancelar();
		given(gestionEstadoEventosService.cancelar(5L)).willReturn(jazz);

		MvcTestResult resultado = operar("cancelacion", admin);

		assertThat(resultado).hasStatusOk();
		assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("CANCELADO");
		assertThat(resultado).bodyJson().extractingPath("$.ventaAbierta").isEqualTo(false);
	}

	@Test
	void cerrarLaVentaDevuelveElEventoConSuEstado() {
		jazz.activar();
		jazz.cerrarVenta();
		given(gestionEstadoEventosService.cerrarVenta(5L)).willReturn(jazz);

		MvcTestResult resultado = operar("cierre-de-venta", admin);

		assertThat(resultado).hasStatusOk();
		assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("ACTIVO");
		assertThat(resultado).bodyJson().extractingPath("$.ventaAbierta").isEqualTo(false);
	}

	@Test
	void unaVentaYaCerradaYUnCanceladoSalenConSuCodigo() {
		given(gestionEstadoEventosService.cerrarVenta(5L)).willThrow(new VentaYaCerradaException());
		given(gestionEstadoEventosService.cancelar(5L)).willThrow(new EventoCanceladoException());

		tieneCodigo(operar("cierre-de-venta", admin), HttpStatus.CONFLICT, "VENTA_YA_CERRADA");
		tieneCodigo(operar("cancelacion", admin), HttpStatus.CONFLICT, "EVENTO_CANCELADO");
	}

	// Un id que no es número sale como dato inválido, no como error inesperado
	@Test
	void unIdQueNoEsNumeroEsDatosInvalidos() {
		MvcTestResult resultado = mvc.post().uri("/api/eventos/abc/activacion")
				.with(admin).exchange();

		assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("DATOS_INVALIDOS");
		then(gestionEstadoEventosService).shouldHaveNoInteractions();
	}
}
