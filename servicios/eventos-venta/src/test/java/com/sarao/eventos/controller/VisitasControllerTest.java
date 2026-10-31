package com.sarao.eventos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.sarao.eventos.config.SecurityConfig;
import com.sarao.eventos.entity.Pagina;
import com.sarao.eventos.service.ContadorDeVisitas;

// decisiones.md → «Contador de visitas por página»: lo pide el pie de todas las pantallas, con o sin sesión
@WebMvcTest(VisitasController.class)
@ActiveProfiles("test")
@Import(SecurityConfig.class)
class VisitasControllerTest {

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private ContadorDeVisitas contador;

	@Test
	void sinSesionSumaUnaVisitaYDevuelveElTotal() {
		given(contador.registrarVisita(Pagina.DETALLE_DE_EVENTO)).willReturn(1234L);

		MvcTestResult resultado = mvc.post().uri("/api/visitas/DETALLE_DE_EVENTO").exchange();

		assertThat(resultado).hasStatus(HttpStatus.OK);
		assertThat(resultado).bodyJson().extractingPath("$.total").isEqualTo(1234);
	}

	// Solo las páginas del enum: una cualquiera no crea filas nuevas
	@Test
	void unaPaginaQueNoExisteSeRechaza() {
		MvcTestResult resultado = mvc.post().uri("/api/visitas/CUALQUIERA").exchange();

		assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("DATOS_INVALIDOS");
		then(contador).should(never()).registrarVisita(any());
	}

	// Lo público es solo sumar una visita: cualquier otro método o ruta sigue rechazado
	@Test
	void otroMetodoSinSesionNoEsPublico() {
		MvcTestResult resultado = mvc.get().uri("/api/visitas/PORTADA").exchange();

		assertThat(resultado).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post().uri("/api/visitas/PORTADA/otra").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}
}
