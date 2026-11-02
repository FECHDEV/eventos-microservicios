package com.sarao.eventos;

import static com.sarao.eventos.SesionDelGateway.administrador;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.sarao.eventos.service.ConfirmacionDeReservas;
import com.sarao.eventos.service.ConsultaDeClientes;
import com.sarao.eventos.service.CorreosDeCompra;
import com.sarao.eventos.service.ResultadoValidacion;
import com.sarao.eventos.service.ValidacionDeEntradas;

// CU-15 contra H2: elegir el evento, validar y registrar el acceso (RF-1 a RF-12)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ValidacionIntegracionTest {

	private static final Long ANA = 7L;
	private static final Long PEDRO = 20L;
	private static final ZoneId LA_PAZ = ZoneId.of("America/La_Paz");
	private static final LocalDate HOY = LocalDate.now(LA_PAZ);

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ConfirmacionDeReservas confirmacionDeReservas;

	@Autowired
	private ValidacionDeEntradas validacionDeEntradas;

	@MockitoBean
	private ConsultaDeClientes consultaDeClientes;

	// Los correos de la compra no son de este CU
	@MockitoBean
	private CorreosDeCompra correosDeCompra;

	private static RequestPostProcessor sesion(Long idUsuario, String rol) {
		return peticion -> {
			peticion.addHeader("X-Clave-Servicio", "clave-de-pruebas");
			peticion.addHeader("X-Usuario-Id", idUsuario.toString());
			peticion.addHeader("X-Usuario-Rol", rol);
			return peticion;
		};
	}

	private final RequestPostProcessor pedro = sesion(PEDRO, "PERSONAL");
	private final RequestPostProcessor ana = sesion(ANA, "CLIENTE");

	@BeforeEach
	void preparar() {
		given(consultaDeClientes.fechaNacimiento(any())).willReturn(LocalDate.of(2000, 5, 20));
	}

	@AfterEach
	void limpiar() {
		jdbc.update("DELETE FROM acceso");
		jdbc.update("DELETE FROM entrada");
		jdbc.update("DELETE FROM reserva");
		jdbc.update("DELETE FROM tipo_entrada");
		jdbc.update("DELETE FROM evento");
	}

	private MvcTestResult postComoAdmin(String uri, String cuerpo) {
		var peticion = mvc.post().uri(uri).with(administrador());
		return cuerpo == null ? peticion.exchange()
				: peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
	}

	// Un evento activo con un tipo «General»; devuelve [idEvento, idTipo]. Se crea a futuro y se mueve a hoy: de 23:59
	// a 23:58 cruza la medianoche, así admite validar durante todo el día de hoy, sea la hora que sea
	private int[] eventoDeHoy(String nombre) throws Exception {
		MvcTestResult alta = postComoAdmin("/api/eventos", """
				{"nombre":"%s","fecha":"%s","horaInicio":"20:00","horaFin":"23:00","lugar":"Teatro"}
				""".formatted(nombre, HOY.plusDays(5)));
		assertThat(alta).hasStatus(HttpStatus.CREATED);
		int idEvento = JsonPath.read(alta.getResponse().getContentAsString(), "$.idEvento");
		MvcTestResult tipo = postComoAdmin("/api/eventos/" + idEvento + "/tipos-entrada",
				"{\"nombre\":\"General\",\"precio\":50.00,\"cantidadTotal\":10}");
		int idTipo = JsonPath.read(tipo.getResponse().getContentAsString(), "$.idTipoEntrada");
		assertThat(postComoAdmin("/api/eventos/" + idEvento + "/activacion", null)).hasStatus(HttpStatus.OK);
		jdbc.update("UPDATE evento SET fecha = ?, hora_inicio = '23:59:00', hora_fin = '23:58:00' WHERE id_evento = ?",
				HOY, idEvento);
		return new int[] { idEvento, idTipo };
	}

	// Una entrada emitida a Ana en ese tipo; devuelve su código
	private String entrada(int idTipo) throws Exception {
		MvcTestResult reserva = mvc.post().uri("/api/reservas").with(ana).contentType(MediaType.APPLICATION_JSON)
				.content("{\"idTipoEntrada\":%d,\"cantidad\":1}".formatted(idTipo)).exchange();
		assertThat(reserva).hasStatus(HttpStatus.CREATED);
		Integer idReserva = JsonPath.read(reserva.getResponse().getContentAsString(), "$.idReserva");
		confirmacionDeReservas.confirmar(idReserva.longValue(), 41L);
		return jdbc.queryForObject("SELECT codigo_qr FROM entrada WHERE id_reserva = ?", String.class, idReserva);
	}

	private MvcTestResult validar(RequestPostProcessor sesion, int idEvento, String codigo) {
		return mvc.post().uri("/api/accesos").with(sesion).contentType(MediaType.APPLICATION_JSON)
				.content("{\"idEvento\":%d,\"codigo\":\"%s\"}".formatted(idEvento, codigo)).exchange();
	}

	private static void resulta(MvcTestResult respuesta, String resultado) {
		assertThat(respuesta).hasStatusOk();
		assertThat(respuesta).bodyJson().extractingPath("$.resultado").isEqualTo(resultado);
	}

	private int accesos() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM acceso", Integer.class);
	}

	private String estado(String codigo) {
		return jdbc.queryForObject("SELECT estado FROM entrada WHERE codigo_qr = ?", String.class, codigo);
	}

	// RF-1
	@Test
	void soloElPersonalValida() throws Exception {
		int[] jazz = eventoDeHoy("Noche de jazz");

		assertThat(mvc.get().uri("/api/accesos/eventos").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.get().uri("/api/accesos/eventos").with(ana).exchange()).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.get().uri("/api/accesos/eventos").with(administrador()).exchange())
				.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(validar(ana, jazz[0], "x")).hasStatus(HttpStatus.FORBIDDEN);
	}

	// RF-2: los activos de hoy, por hora de inicio; ni un borrador ni el de otro día
	@Test
	void listaLosEventosActivosDeHoy() throws Exception {
		int[] jazz = eventoDeHoy("Noche de jazz");
		int[] tango = eventoDeHoy("Tango");
		jdbc.update("UPDATE evento SET hora_inicio = '00:00:00', hora_fin = '23:58:00' WHERE id_evento = ?", tango[0]);
		postComoAdmin("/api/eventos", """
				{"nombre":"Borrador","fecha":"%s","horaInicio":"20:00","horaFin":"23:00","lugar":"Teatro"}
				""".formatted(HOY.plusDays(5)));
		int[] otroDia = eventoDeHoy("Mañana");
		jdbc.update("UPDATE evento SET fecha = ? WHERE id_evento = ?", HOY.plusDays(1), otroDia[0]);

		MvcTestResult lista = mvc.get().uri("/api/accesos/eventos").with(pedro).exchange();

		assertThat(lista).hasStatusOk();
		assertThat(lista).bodyJson().extractingPath("$[*].nombre").asArray().containsExactly("Tango", "Noche de jazz");
		assertThat(lista).bodyJson().extractingPath("$[0].idEvento").isEqualTo(tango[0]);
	}

	// RF-5, RF-6 y RF-10: válida una vez, con su acceso; la segunda, ya utilizada
	@Test
	void unaEntradaVigenteSeValidaUnaSolaVez() throws Exception {
		int[] jazz = eventoDeHoy("Noche de jazz");
		String codigo = entrada(jazz[1]);

		MvcTestResult valida = validar(pedro, jazz[0], "  " + codigo + " ");
		resulta(valida, "VALIDA");
		assertThat(valida).bodyJson().extractingPath("$.tipoEntrada").isEqualTo("General");
		assertThat(valida.getResponse().getContentAsString()).doesNotContain("ana").doesNotContain("idCliente");
		assertThat(estado(codigo)).isEqualTo("UTILIZADA");
		assertThat(jdbc.queryForObject("SELECT id_usuario FROM acceso", Long.class)).isEqualTo(PEDRO);

		resulta(validar(pedro, jazz[0], codigo), "YA_UTILIZADA");
		assertThat(accesos()).isEqualTo(1);
	}

	// RF-7: inexistente y de otro evento, sin distinguir
	@Test
	void unCodigoAjenoNoCorresponde() throws Exception {
		int[] jazz = eventoDeHoy("Noche de jazz");
		int[] tango = eventoDeHoy("Tango");
		String deTango = entrada(tango[1]);

		resulta(validar(pedro, jazz[0], deTango), "NO_CORRESPONDE");
		resulta(validar(pedro, jazz[0], "no-existe"), "NO_CORRESPONDE");
		assertThat(estado(deTango)).isEqualTo("ACTIVA");
		assertThat(accesos()).isZero();
	}

	// RF-9: una devuelta no es vigente
	@Test
	void unaDevueltaNoEsVigente() throws Exception {
		int[] jazz = eventoDeHoy("Noche de jazz");
		String codigo = entrada(jazz[1]);
		jdbc.update("UPDATE entrada SET estado = 'DEVUELTA'");

		resulta(validar(pedro, jazz[0], codigo), "NO_VIGENTE");
		assertThat(accesos()).isZero();
	}

	// RF-4: un evento que no es hoy, no activo o inexistente no admite validar
	@Test
	void unEventoQueNoEsDeHoyNoAdmiteValidar() throws Exception {
		int[] jazz = eventoDeHoy("Noche de jazz");
		String codigo = entrada(jazz[1]);

		jdbc.update("UPDATE evento SET fecha = ?", HOY.plusDays(1));
		assertThat(validar(pedro, jazz[0], codigo)).hasStatus(HttpStatus.CONFLICT).bodyJson()
				.extractingPath("$.codigo").isEqualTo("EVENTO_NO_DISPONIBLE");

		jdbc.update("UPDATE evento SET fecha = ?, estado = 'POSTERGADO'", HOY);
		assertThat(validar(pedro, jazz[0], codigo)).hasStatus(HttpStatus.CONFLICT);

		assertThat(validar(pedro, 999999, codigo)).hasStatus(HttpStatus.NOT_FOUND).bodyJson()
				.extractingPath("$.codigo").isEqualTo("EVENTO_NO_ENCONTRADO");
		assertThat(accesos()).isZero();
	}

	@Test
	void unCodigoVacioOMuyLargoEsDatoInvalido() throws Exception {
		int[] jazz = eventoDeHoy("Noche de jazz");

		assertThat(validar(pedro, jazz[0], "   ")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(validar(pedro, jazz[0], "x".repeat(256))).hasStatus(HttpStatus.BAD_REQUEST);
	}

	// RF-11: dos Personales, la misma entrada, a la vez: un solo acceso. En H2 el bloqueo compartido del evento es
	// exclusivo, así que esto no prueba el bloqueo de la entrada: eso se prueba contra MySQL (plan de HU-19)
	@Test
	void dosValidacionesALaVezRegistranUnSoloAcceso() throws Exception {
		int[] jazz = eventoDeHoy("Noche de jazz");
		String codigo = entrada(jazz[1]);
		CountDownLatch largada = new CountDownLatch(1);
		ExecutorService hilos = Executors.newFixedThreadPool(2);
		List<ResultadoValidacion.Resultado> resultados = new ArrayList<>();
		try {
			List<Future<ResultadoValidacion>> intentos = new ArrayList<>();
			for (long personal : new long[] { PEDRO, PEDRO + 1 }) {
				intentos.add(hilos.submit(() -> {
					largada.await();
					return validacionDeEntradas.validar(personal, (long) jazz[0], codigo);
				}));
			}
			largada.countDown();
			for (Future<ResultadoValidacion> intento : intentos) {
				resultados.add(intento.get(10, TimeUnit.SECONDS).resultado());
			}
		} finally {
			hilos.shutdownNow();
		}

		assertThat(resultados).containsExactlyInAnyOrder(ResultadoValidacion.Resultado.VALIDA,
				ResultadoValidacion.Resultado.YA_UTILIZADA);
		assertThat(accesos()).isEqualTo(1);
	}

	// RF-12: reactivada tras una postergación, se valida de nuevo y suma otro acceso
	@Test
	void unaReactivadaSumaOtroAcceso() throws Exception {
		int[] jazz = eventoDeHoy("Noche de jazz");
		String codigo = entrada(jazz[1]);
		resulta(validar(pedro, jazz[0], codigo), "VALIDA");
		jdbc.update("UPDATE entrada SET estado = 'ACTIVA'");

		resulta(validar(pedro, jazz[0], codigo), "VALIDA");
		assertThat(accesos()).isEqualTo(2);
	}
}
