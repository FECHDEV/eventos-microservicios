package com.sarao.eventos;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

// Matriz de permisos de decisiones.md → Roles del personal (CU-09 RF-28): qué área de Eventos y Venta abre cada rol.
// Solo mira la autorización: una respuesta distinta de 401 y 403 es que el rol pasó
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PermisosPorRolIntegracionTest {

	private static final String EVENTOS = "/api/eventos";
	private static final String REPORTES = "/api/reportes/opciones";
	private static final String DEVOLUCIONES = "/api/devoluciones";
	private static final String VALIDAR = "/api/accesos/eventos";
	private static final String MIS_ENTRADAS = "/api/entradas";

	private static final Set<String> AREAS = Set.of(EVENTOS, REPORTES, DEVOLUCIONES, VALIDAR, MIS_ENTRADAS);

	@Autowired
	private MockMvcTester mvc;

	static Stream<Arguments> permisos() {
		return Stream.of(
				Arguments.of("ADMINISTRADOR", Set.of(EVENTOS, REPORTES, DEVOLUCIONES, VALIDAR)),
				Arguments.of("ORGANIZADOR", Set.of(EVENTOS)),
				Arguments.of("CONTADOR", Set.of(REPORTES)),
				Arguments.of("PERSONAL", Set.of(VALIDAR)),
				Arguments.of("CLIENTE", Set.of(MIS_ENTRADAS)));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("permisos")
	void cadaRolEntraSoloASusAreas(String rol, Set<String> permitidas) {
		for (String area : AREAS) {
			int estado = mvc.get().uri(area).with(SesionDelGateway.sesion(5L, rol)).exchange().getResponse().getStatus();
			if (permitidas.contains(area)) {
				assertThat(estado).as("%s en %s", rol, area).isNotIn(401, 403);
			} else {
				assertThat(estado).as("%s en %s", rol, area).isEqualTo(403);
			}
		}
	}
}
