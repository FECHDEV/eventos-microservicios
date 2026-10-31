package com.sarao.eventos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.sarao.eventos.config.SecurityConfig;
import com.sarao.eventos.entity.EstadoEvento;
import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.entity.TipoEntrada;
import com.sarao.eventos.exception.CamposInvalidosException;
import com.sarao.eventos.exception.CamposInvalidosException.CampoInvalido;
import com.sarao.eventos.exception.EventoNoEncontradoException;
import com.sarao.eventos.service.CatalogoService;
import com.sarao.eventos.service.EventoDelCatalogo;
import com.sarao.eventos.service.FormatoImagen;
import com.sarao.eventos.service.ImagenEvento;

// Sin sesión: es la parte pública de la API (CU-05 RF-1)
@WebMvcTest(CatalogoController.class)
@ActiveProfiles("test")
@Import(SecurityConfig.class)
class CatalogoControllerTest {

	private static final String CATALOGO = "/api/catalogo";

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private CatalogoService catalogoService;

	private Evento jazz;

	@BeforeEach
	void preparar() {
		jazz = Evento.registrar(1L, "Noche de jazz", "Cuarteto", LocalDate.of(2026, 10, 4), LocalTime.of(22, 0),
				LocalTime.of(3, 0), "Teatro Municipal", 18, 4);
		ReflectionTestUtils.setField(jazz, "idEvento", 5L);
		jazz.activar();
	}

	private TipoEntrada tipo(long id, String nombre, String precio, int total, int disponible) {
		TipoEntrada tipo = TipoEntrada.registrar(jazz, nombre, new BigDecimal(precio), total);
		ReflectionTestUtils.setField(tipo, "idTipoEntrada", id);
		ReflectionTestUtils.setField(tipo, "cantidadDisponible", disponible);
		return tipo;
	}

	// --- Lista (RF-1, RF-5 a RF-9) ---

	@Test
	void listaSinSesionConLosDatosDeCadaTarjeta() {
		given(catalogoService.buscar(null, null, null, 0, 12))
				.willReturn(new PageImpl<>(List.of(jazz), PageRequest.of(0, 12), 13));

		MvcTestResult resultado = mvc.get().uri(CATALOGO).exchange();

		assertThat(resultado).hasStatus(HttpStatus.OK);
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].idEvento").isEqualTo(5);
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].nombre").isEqualTo("Noche de jazz");
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].fecha").isEqualTo("2026-10-04");
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].horaInicio").isEqualTo("22:00:00");
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].horaFin").isEqualTo("03:00:00");
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].lugar").isEqualTo("Teatro Municipal");
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].ventaAbierta").isEqualTo(true);
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].tieneImagen").isEqualTo(false);
		assertThat(resultado).bodyJson().extractingPath("$.totalElementos").isEqualTo(13);
		assertThat(resultado).bodyJson().extractingPath("$.totalPaginas").isEqualTo(2);
		// Lo que es del Administrador no sale en la parte pública
		assertThat(resultado).bodyJson().doesNotHavePath("$.contenido[0].estado");
	}

	@Test
	void unPostergadoInformaSuFechaOriginal() {
		jazz.postergar(LocalDate.of(2026, 11, 1));
		given(catalogoService.buscar(null, null, null, 0, 12)).willReturn(new PageImpl<>(List.of(jazz)));

		MvcTestResult resultado = mvc.get().uri(CATALOGO).exchange();

		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].fecha").isEqualTo("2026-11-01");
		assertThat(resultado).bodyJson().extractingPath("$.contenido[0].fechaOriginal").isEqualTo("2026-10-04");
	}

	@Test
	void pasaLosFiltrosAlServicio() {
		given(catalogoService.buscar(any(), any(), any(), anyInt(), anyInt())).willReturn(new PageImpl<>(List.of()));

		assertThat(mvc.get().uri(CATALOGO + "?texto=jazz&desde=2026-10-01&hasta=2026-10-31&pagina=1&tamano=6")
				.exchange()).hasStatus(HttpStatus.OK);

		then(catalogoService).should().buscar("jazz", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 1, 6);
	}

	// RF-10: una fecha que no es ISO no llega al servicio
	@Test
	void unaFechaConFormatoInvalidoSeRechaza() {
		MvcTestResult resultado = mvc.get().uri(CATALOGO + "?desde=04/10/2026").exchange();

		assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("DATOS_INVALIDOS");
		assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("desde");
		then(catalogoService).shouldHaveNoInteractions();
	}

	@Test
	void unRangoInvertidoSaleConElCampo() {
		given(catalogoService.buscar(null, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 4), 0, 12))
				.willThrow(new CamposInvalidosException(
						List.of(new CampoInvalido("hasta", "no puede ser anterior a la fecha desde"))));

		MvcTestResult resultado = mvc.get().uri(CATALOGO + "?desde=2026-10-05&hasta=2026-10-04").exchange();

		assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("hasta");
	}

	// Solo se abre la lectura
	@Test
	void escribirEnElCatalogoExigeSesion() {
		assertThat(mvc.post().uri(CATALOGO).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// --- Detalle (RF-14 a RF-16) ---

	@Test
	void elDetalleTraeLosTiposConSuDisponibilidadSinCantidades() {
		given(catalogoService.obtener(5L)).willReturn(new EventoDelCatalogo(jazz,
				List.of(tipo(1, "General", "50.00", 100, 40), tipo(2, "VIP", "150.00", 20, 0))));

		MvcTestResult resultado = mvc.get().uri(CATALOGO + "/5").exchange();

		assertThat(resultado).hasStatus(HttpStatus.OK);
		assertThat(resultado).bodyJson().extractingPath("$.descripcion").isEqualTo("Cuarteto");
		assertThat(resultado).bodyJson().extractingPath("$.restriccionEdad").isEqualTo(18);
		assertThat(resultado).bodyJson().extractingPath("$.limiteCompra").isEqualTo(4);
		assertThat(resultado).bodyJson().extractingPath("$.fechaOriginal").isNull();
		assertThat(resultado).bodyJson().extractingPath("$.tiposEntrada[0].nombre").isEqualTo("General");
		assertThat(resultado).bodyJson().extractingPath("$.tiposEntrada[0].precio").isEqualTo(50.00);
		assertThat(resultado).bodyJson().extractingPath("$.tiposEntrada[0].disponible").isEqualTo(true);
		assertThat(resultado).bodyJson().extractingPath("$.tiposEntrada[1].disponible").isEqualTo(false);
		assertThat(resultado).bodyJson().doesNotHavePath("$.tiposEntrada[0].cantidadDisponible");
		assertThat(resultado).bodyJson().doesNotHavePath("$.tiposEntrada[0].cantidadTotal");
		assertThat(resultado).bodyJson().doesNotHavePath("$.estado");
	}

	@Test
	void unEventoQueNoSeMuestraResponde404() {
		given(catalogoService.obtener(9L)).willThrow(new EventoNoEncontradoException());

		MvcTestResult resultado = mvc.get().uri(CATALOGO + "/9").exchange();

		assertThat(resultado).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("EVENTO_NO_ENCONTRADO");
	}

	// --- Imagen (RF-17) ---

	@Test
	void sirveLaImagenConSuTipo() throws Exception {
		byte[] png = { (byte) 0x89, 'P', 'N', 'G' };
		given(catalogoService.leerImagen(5L)).willReturn(new ImagenEvento(png, FormatoImagen.PNG));

		MvcTestResult resultado = mvc.get().uri(CATALOGO + "/5/imagen").exchange();

		assertThat(resultado).hasStatus(HttpStatus.OK);
		assertThat(resultado).hasContentType(MediaType.IMAGE_PNG);
		assertThat(resultado.getResponse().getContentAsByteArray()).isEqualTo(png);
		assertThat(resultado.getResponse().getHeader("Cache-Control")).isEqualTo("max-age=300, public");
	}

	// RF-1: HEAD también es lectura
	@Test
	void headSeAtiendeSinSesion() {
		given(catalogoService.buscar(null, null, null, 0, 12)).willReturn(new PageImpl<>(List.of()));

		assertThat(mvc.head().uri(CATALOGO).exchange()).hasStatus(HttpStatus.OK);
	}
}
