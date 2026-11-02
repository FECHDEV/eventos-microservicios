package com.sarao.eventos;

import static com.sarao.eventos.SesionDelGateway.administrador;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

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
import com.sarao.eventos.exception.ErrorNegocioException;
import com.sarao.eventos.service.ConfirmacionDeReservas;
import com.sarao.eventos.service.ConsultaDeClientes;
import com.sarao.eventos.service.CorreosDeCompra;
import com.sarao.eventos.service.Cuenta;
import com.sarao.eventos.service.GestionDevolucionesService;

// CU-13 contra H2: los eventos cancelados, sus compras pendientes y el registro de la devolución (RF-1 a RF-13)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DevolucionesIntegracionTest {

	private static final Long ANA = 7L;
	private static final Long LUIS = 8L;
	private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/La_Paz"));
	private static final Map<Long, Cuenta> CUENTAS = List.of(
			new Cuenta(ANA, "Ana", "Rojas", "ana@mail.com", "70000000", HOY.atStartOfDay(), "ACTIVA"),
			new Cuenta(LUIS, "Luis", "Paz", "luis@mail.com", null, HOY.atStartOfDay(), "ACTIVA"))
			.stream().collect(Collectors.toMap(Cuenta::idUsuario, Function.identity()));

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ConfirmacionDeReservas confirmacionDeReservas;

	@Autowired
	private GestionDevolucionesService gestionDevoluciones;

	@MockitoBean
	private ConsultaDeClientes consultaDeClientes;

	@MockitoBean
	private CorreosDeCompra correosDeCompra;

	private final RequestPostProcessor admin = administrador();
	private int idEvento;
	private Integer compraDeAna;
	private Integer compraDeLuis;

	private static RequestPostProcessor cliente(Long id) {
		return peticion -> {
			peticion.addHeader("X-Clave-Servicio", "clave-de-pruebas");
			peticion.addHeader("X-Usuario-Id", id.toString());
			peticion.addHeader("X-Usuario-Rol", "CLIENTE");
			return peticion;
		};
	}

	@SuppressWarnings("unchecked")
	@BeforeEach
	void preparar() throws Exception {
		given(consultaDeClientes.fechaNacimiento(any())).willReturn(LocalDate.of(2000, 5, 20));
		given(consultaDeClientes.cuentas(any())).willAnswer(invocacion -> ((Collection<Long>) invocacion.getArgument(0))
				.stream().filter(CUENTAS::containsKey).collect(Collectors.toMap(Function.identity(), CUENTAS::get)));
		idEvento = eventoConCompras();
		compraDeAna = comprar(ANA, 3, 41L);
		compraDeLuis = comprar(LUIS, 1, 42L);
		// La primera entrada de Ana ya se usó; el evento se cancela
		jdbc.update("UPDATE entrada SET estado = 'UTILIZADA' WHERE id_entrada = (SELECT MIN(id_entrada) FROM entrada)");
		jdbc.update("UPDATE evento SET estado = 'CANCELADO', venta_abierta = FALSE");
	}

	@AfterEach
	void limpiar() {
		jdbc.update("DELETE FROM entrada");
		jdbc.update("DELETE FROM reserva");
		jdbc.update("DELETE FROM tipo_entrada");
		jdbc.update("DELETE FROM evento");
	}

	private int idTipo;

	private int eventoConCompras() throws Exception {
		MvcTestResult alta = mvc.post().uri("/api/eventos").with(admin).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nombre":"Noche de jazz","fecha":"%s","horaInicio":"20:00","horaFin":"23:00","lugar":"Teatro"}
						""".formatted(HOY.plusDays(5))).exchange();
		int id = JsonPath.read(alta.getResponse().getContentAsString(), "$.idEvento");
		MvcTestResult tipo = mvc.post().uri("/api/eventos/" + id + "/tipos-entrada").with(admin)
				.contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"General\",\"precio\":50.00,\"cantidadTotal\":10}")
				.exchange();
		idTipo = JsonPath.read(tipo.getResponse().getContentAsString(), "$.idTipoEntrada");
		mvc.post().uri("/api/eventos/" + id + "/activacion").with(admin).exchange();
		return id;
	}

	private Integer comprar(Long idCliente, int cantidad, long idPago) throws Exception {
		MvcTestResult reserva = mvc.post().uri("/api/reservas").with(cliente(idCliente))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"idTipoEntrada\":%d,\"cantidad\":%d}".formatted(idTipo, cantidad)).exchange();
		Integer id = JsonPath.read(reserva.getResponse().getContentAsString(), "$.idReserva");
		confirmacionDeReservas.confirmar(id.longValue(), idPago);
		return id;
	}

	private MvcTestResult devolverCompra(Integer idReserva) {
		return mvc.post().uri("/api/devoluciones/compras/" + idReserva).with(admin).exchange();
	}

	private static void tieneCodigo(MvcTestResult respuesta, HttpStatus estado, String codigo) {
		assertThat(respuesta).hasStatus(estado);
		assertThat(respuesta).bodyJson().extractingPath("$.codigo").isEqualTo(codigo);
	}

	private List<String> estados() {
		return jdbc.queryForList("SELECT estado FROM entrada ORDER BY id_entrada", String.class);
	}

	// RF-1
	@Test
	void soloElAdministrador() {
		assertThat(mvc.get().uri("/api/devoluciones").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.get().uri("/api/devoluciones").with(cliente(ANA)).exchange()).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.post().uri("/api/devoluciones/compras/" + compraDeAna).with(cliente(ANA)).exchange())
				.hasStatus(HttpStatus.FORBIDDEN);
	}

	// RF-2: con cuántas entradas tiene pendientes
	@Test
	void listaLosCanceladosConSusPendientes() {
		MvcTestResult lista = mvc.get().uri("/api/devoluciones").with(admin).exchange();

		assertThat(lista).hasStatusOk();
		assertThat(lista).bodyJson().extractingPath("$[0].nombre").isEqualTo("Noche de jazz");
		assertThat(lista).bodyJson().extractingPath("$[0].pendientes").isEqualTo(3);
	}

	// RF-4 a RF-6: por compra, con el cliente de Seguridad; la usada no aparece y cada entrada lleva su número
	@Test
	void muestraLasComprasPendientesSinLasUsadas() throws Exception {
		MvcTestResult evento = mvc.get().uri("/api/devoluciones/" + idEvento).with(admin).exchange();

		assertThat(evento).hasStatusOk();
		assertThat(evento).bodyJson().extractingPath("$.pendientes.length()").isEqualTo(2);
		assertThat(evento).bodyJson().extractingPath("$.pendientes[0].cliente").isEqualTo("Ana Rojas");
		assertThat(evento).bodyJson().extractingPath("$.pendientes[0].cantidad").isEqualTo(3);
		assertThat(evento).bodyJson().extractingPath("$.pendientes[0].monto").isEqualTo(150.0);
		assertThat(evento).bodyJson().extractingPath("$.pendientes[0].precioUnitario").isEqualTo(50.0);
		assertThat(evento).bodyJson().extractingPath("$.pendientes[0].entradas[*].numero").asArray()
				.containsExactly(2, 3);
		assertThat(evento).bodyJson().extractingPath("$.devueltas.length()").isEqualTo(0);
		assertThat(evento.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
				.doesNotContain("codigo");
	}

	// RF-8 y RF-12
	@Test
	void unEventoInexistenteONoCanceladoSeRechaza() {
		tieneCodigo(mvc.get().uri("/api/devoluciones/999999").with(admin).exchange(), HttpStatus.NOT_FOUND,
				"EVENTO_NO_ENCONTRADO");
		jdbc.update("UPDATE evento SET estado = 'ACTIVO'");
		tieneCodigo(mvc.get().uri("/api/devoluciones/" + idEvento).with(admin).exchange(), HttpStatus.CONFLICT,
				"EVENTO_NO_CANCELADO");
		tieneCodigo(devolverCompra(compraDeAna), HttpStatus.CONFLICT, "EVENTO_NO_CANCELADO");
		assertThat(estados()).doesNotContain("DEVUELTA");
	}

	// RF-9 y RF-11: la compra entera pasa sus activas a DEVUELTA, una vez; la usada queda
	@Test
	void devolverUnaCompraUnaSolaVez() {
		MvcTestResult devuelta = devolverCompra(compraDeAna);
		assertThat(devuelta).hasStatusOk();
		assertThat(devuelta).bodyJson().extractingPath("$.registradas").isEqualTo(2);
		assertThat(estados()).containsExactly("UTILIZADA", "DEVUELTA", "DEVUELTA", "ACTIVA");

		tieneCodigo(devolverCompra(compraDeAna), HttpStatus.CONFLICT, "ENTRADA_YA_DEVUELTA");
		tieneCodigo(devolverCompra(999999), HttpStatus.NOT_FOUND, "COMPRA_NO_ENCONTRADA");

		MvcTestResult evento = mvc.get().uri("/api/devoluciones/" + idEvento).with(admin).exchange();
		assertThat(evento).bodyJson().extractingPath("$.devueltas[0].entradas[*].numero").asArray().containsExactly(2, 3);
		assertThat(evento).bodyJson().extractingPath("$.pendientes.length()").isEqualTo(1);
	}

	// RF-10 a RF-12: de a una; una usada no se devuelve; una compra con solo usadas tampoco
	@Test
	void devolverUnaEntradaYNoLasUsadas() {
		List<Long> ids = jdbc.queryForList("SELECT id_entrada FROM entrada ORDER BY id_entrada", Long.class);

		assertThat(mvc.post().uri("/api/devoluciones/entradas/" + ids.get(1)).with(admin).exchange()).hasStatusOk();
		tieneCodigo(mvc.post().uri("/api/devoluciones/entradas/" + ids.get(1)).with(admin).exchange(),
				HttpStatus.CONFLICT, "ENTRADA_YA_DEVUELTA");
		tieneCodigo(mvc.post().uri("/api/devoluciones/entradas/" + ids.get(0)).with(admin).exchange(),
				HttpStatus.CONFLICT, "ENTRADA_NO_DEVOLVIBLE");
		tieneCodigo(mvc.post().uri("/api/devoluciones/entradas/999999").with(admin).exchange(), HttpStatus.NOT_FOUND,
				"ENTRADA_NO_ENCONTRADA");

		jdbc.update("UPDATE entrada SET estado = 'UTILIZADA' WHERE id_reserva = ?", compraDeLuis);
		tieneCodigo(devolverCompra(compraDeLuis), HttpStatus.CONFLICT, "ENTRADA_NO_DEVOLVIBLE");
	}

	// RF-13: dos Administradores, la misma compra, a la vez: se registra una sola vez
	@Test
	void dosRegistrosALaVezDevuelvenUnaSolaVez() throws Exception {
		CountDownLatch largada = new CountDownLatch(1);
		ExecutorService hilos = Executors.newFixedThreadPool(2);
		List<String> resultados = new ArrayList<>();
		try {
			List<Future<String>> intentos = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				intentos.add(hilos.submit(() -> {
					largada.await();
					try {
						return "registradas " + gestionDevoluciones.registrarCompra(compraDeAna.longValue());
					} catch (ErrorNegocioException e) {
						return e.getCodigo();
					}
				}));
			}
			largada.countDown();
			for (Future<String> intento : intentos) {
				resultados.add(intento.get(10, TimeUnit.SECONDS));
			}
		} finally {
			hilos.shutdownNow();
		}

		assertThat(resultados).containsExactlyInAnyOrder("registradas 2", "ENTRADA_YA_DEVUELTA");
	}
}
