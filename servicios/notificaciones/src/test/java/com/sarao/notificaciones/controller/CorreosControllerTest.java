package com.sarao.notificaciones.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.sarao.notificaciones.config.SecurityConfig;
import com.sarao.notificaciones.dto.CorreoDeCompra;
import com.sarao.notificaciones.service.NotificadorCorreo;

@WebMvcTest(CorreosController.class)
@ActiveProfiles("test")
@Import(SecurityConfig.class)
class CorreosControllerTest {

	private static final String RECUPERACION = """
			{"correo":"ana@mail.com","nombre":"Ana","token":"token-de-prueba"}
			""";
	private static final String COMPRA = """
			{"correo":"ana@mail.com","nombre":"Ana","evento":"Noche de jazz","fecha":"2026-10-04","horaInicio":"22:00",
			"lugar":"Teatro","tipo":"General","cantidad":2,"precioUnitario":50.00,"total":100.00}
			""";
	private static final CorreoDeCompra DE_ANA = new CorreoDeCompra("ana@mail.com", "Ana", "Noche de jazz",
			LocalDate.of(2026, 10, 4), LocalTime.of(22, 0), "Teatro", "General", 2, new BigDecimal("50.00"),
			new BigDecimal("100.00"));

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private NotificadorCorreo notificador;

	private MvcTestResult post(String ruta, String cuerpo) {
		return mvc.post().uri("/interno/correos/" + ruta).header("X-Clave-Servicio", "clave-de-pruebas")
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
	}

	@Test
	void sinLaClaveDeServicioNoEntra() {
		var sinClave = mvc.post().uri("/interno/correos/recuperacion").contentType(MediaType.APPLICATION_JSON)
				.content(RECUPERACION).exchange();
		var otraClave = mvc.post().uri("/interno/correos/recuperacion").header("X-Clave-Servicio", "adivinada")
				.contentType(MediaType.APPLICATION_JSON).content(RECUPERACION).exchange();

		assertThat(sinClave).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(otraClave).hasStatus(HttpStatus.UNAUTHORIZED);
		then(notificador).shouldHaveNoInteractions();
	}

	@Test
	void fueraDeInternoNoHayNada() {
		assertThat(mvc.get().uri("/api/correos").header("X-Clave-Servicio", "clave-de-pruebas").exchange())
				.hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void pideLaRecuperacion() {
		assertThat(post("recuperacion", RECUPERACION)).hasStatus(HttpStatus.ACCEPTED);

		then(notificador).should().enviarRecuperacion("ana@mail.com", "Ana", "token-de-prueba");
	}

	@Test
	void pideElDetalleDeCompra() {
		assertThat(post("detalle-de-compra", COMPRA)).hasStatus(HttpStatus.ACCEPTED);

		then(notificador).should().enviarDetalleDeCompra(DE_ANA);
	}

	@Test
	void pideLasEntradas() {
		assertThat(post("entradas", "{\"compra\":" + COMPRA + ",\"codigos\":[\"codigo-a\"]}"))
				.hasStatus(HttpStatus.ACCEPTED);

		then(notificador).should().enviarEntradas(DE_ANA, List.of("codigo-a"));
	}

	@Test
	void datosInvalidosSeRechazan() {
		for (String cuerpo : List.of("{}", "{\"correo\":\"no-es-correo\",\"nombre\":\"Ana\",\"token\":\"t\"}", "no json")) {
			assertThat(post("recuperacion", cuerpo)).hasStatus(HttpStatus.BAD_REQUEST);
		}
		assertThat(post("entradas", "{\"compra\":" + COMPRA + ",\"codigos\":[]}")).hasStatus(HttpStatus.BAD_REQUEST);
		then(notificador).shouldHaveNoInteractions();
	}
}
