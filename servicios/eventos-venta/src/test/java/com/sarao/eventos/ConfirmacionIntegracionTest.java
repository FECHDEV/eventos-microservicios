package com.sarao.eventos;

import static com.sarao.eventos.SesionDelGateway.administrador;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.sarao.eventos.service.ConfirmacionDeReservas;
import com.sarao.eventos.service.ConsultaDeClientes;
import com.sarao.eventos.service.ConsultaDeClientes.Contacto;
import com.sarao.eventos.service.ResultadoConfirmacion;
import com.sun.net.httpserver.HttpServer;

// CU-07 del lado de Eventos y Venta: la API interna que usa Pagos, contra H2, y los correos contra un Notificaciones
// falso (RF-2, RF-5, RF-9, RF-11 a RF-19, RF-21)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConfirmacionIntegracionTest {

	private static final Long ANA = 7L;
	private static final Long LUIS = 8L;
	private static final ZoneId LA_PAZ = ZoneId.of("America/La_Paz");
	private static final LocalDate HOY = LocalDate.now(LA_PAZ);

	// Notificaciones falso: guarda lo que le piden, por ruta
	private static final HttpServer NOTIFICACIONES;
	private static final List<Map.Entry<String, String>> PEDIDOS = new CopyOnWriteArrayList<>();

	static {
		try {
			NOTIFICACIONES = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		NOTIFICACIONES.createContext("/", intercambio -> {
			PEDIDOS.add(Map.entry(intercambio.getRequestURI().getPath(),
					new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
			intercambio.sendResponseHeaders(202, -1);
			intercambio.close();
		});
		NOTIFICACIONES.start();
	}

	@DynamicPropertySource
	static void notificaciones(DynamicPropertyRegistry propiedades) {
		propiedades.add("eventos.notificaciones-url", () -> "http://localhost:" + NOTIFICACIONES.getAddress().getPort());
	}

	@AfterAll
	static void apagar() {
		NOTIFICACIONES.stop(0);
	}

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ConfirmacionDeReservas confirmacionDeReservas;

	@MockitoBean
	private ConsultaDeClientes consultaDeClientes;

	private final Map<Long, RequestPostProcessor> sesiones = new ConcurrentHashMap<>();

	@BeforeEach
	void preparar() {
		given(consultaDeClientes.fechaNacimiento(any())).willReturn(LocalDate.of(2000, 5, 20));
		given(consultaDeClientes.contacto(ANA)).willReturn(new Contacto("ana@mail.com", "Ana"));
		PEDIDOS.clear();
	}

	@AfterEach
	void limpiar() {
		jdbc.update("DELETE FROM entrada");
		jdbc.update("DELETE FROM reserva");
		jdbc.update("DELETE FROM tipo_entrada");
		jdbc.update("DELETE FROM evento");
	}

	private RequestPostProcessor cliente(Long id) {
		return sesiones.computeIfAbsent(id, clave -> peticion -> {
			peticion.addHeader("X-Clave-Servicio", "clave-de-pruebas");
			peticion.addHeader("X-Usuario-Id", clave.toString());
			peticion.addHeader("X-Usuario-Rol", "CLIENTE");
			return peticion;
		});
	}

	// Como llama Pagos: con la clave y sin usuario
	private static RequestPostProcessor servicio() {
		return peticion -> {
			peticion.addHeader("X-Clave-Servicio", "clave-de-pruebas");
			return peticion;
		};
	}

	private MvcTestResult postComoAdmin(String uri, String cuerpo) {
		var peticion = mvc.post().uri(uri).with(administrador());
		return cuerpo == null ? peticion.exchange()
				: peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
	}

	// Un evento activo en 5 días con un tipo de la cantidad indicada; devuelve el id del tipo
	private Integer tipoActivo(int cantidad, Integer limiteCompra) throws Exception {
		String limite = limiteCompra == null ? "" : ",\"limiteCompra\":" + limiteCompra;
		MvcTestResult alta = postComoAdmin("/api/eventos", """
				{"nombre":"Noche de jazz","fecha":"%s","horaInicio":"20:00","horaFin":"23:00","lugar":"Teatro"%s}
				""".formatted(HOY.plusDays(5), limite));
		assertThat(alta).hasStatus(HttpStatus.CREATED);
		Integer idEvento = JsonPath.read(alta.getResponse().getContentAsString(), "$.idEvento");
		MvcTestResult tipo = postComoAdmin("/api/eventos/" + idEvento + "/tipos-entrada",
				"{\"nombre\":\"General\",\"precio\":50.00,\"cantidadTotal\":%d}".formatted(cantidad));
		assertThat(tipo).hasStatus(HttpStatus.CREATED);
		assertThat(postComoAdmin("/api/eventos/" + idEvento + "/activacion", null)).hasStatus(HttpStatus.OK);
		return JsonPath.read(tipo.getResponse().getContentAsString(), "$.idTipoEntrada");
	}

	private MvcTestResult reservar(Long idCliente, Integer idTipo, int cantidad) {
		return mvc.post().uri("/api/reservas").with(cliente(idCliente)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"idTipoEntrada\":%d,\"cantidad\":%d}".formatted(idTipo, cantidad)).exchange();
	}

	private Integer idReserva(Long idCliente, Integer idTipo, int cantidad) throws Exception {
		MvcTestResult reserva = reservar(idCliente, idTipo, cantidad);
		assertThat(reserva).hasStatus(HttpStatus.CREATED);
		return JsonPath.read(reserva.getResponse().getContentAsString(), "$.idReserva");
	}

	private MvcTestResult confirmar(Integer idReserva, long idPago) {
		return mvc.post().uri("/interno/reservas/" + idReserva + "/confirmacion").with(servicio())
				.contentType(MediaType.APPLICATION_JSON).content("{\"idPago\":" + idPago + "}").exchange();
	}

	private MvcTestResult liberar(Integer idReserva) {
		return mvc.post().uri("/interno/reservas/" + idReserva + "/liberacion").with(servicio()).exchange();
	}

	private static void resulta(MvcTestResult respuesta, String resultado) {
		assertThat(respuesta).hasStatusOk();
		assertThat(respuesta).bodyJson().extractingPath("$.resultado").isEqualTo(resultado);
	}

	private int disponible(Integer idTipo) {
		return jdbc.queryForObject("SELECT cantidad_disponible FROM tipo_entrada WHERE id_tipo_entrada = ?",
				Integer.class, idTipo);
	}

	private String estadoReserva(Integer idReserva) {
		return jdbc.queryForObject("SELECT estado FROM reserva WHERE id_reserva = ?", String.class, idReserva);
	}

	private int entradas(Integer idReserva) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM entrada WHERE id_reserva = ?", Integer.class, idReserva);
	}

	private void vencerLasReservas() {
		jdbc.update("UPDATE reserva SET fecha_expiracion = ?", LocalDateTime.now(LA_PAZ).minusSeconds(1));
	}

	@Test
	void laApiInternaEsSoloParaOtrosServicios() throws Exception {
		Integer id = idReserva(ANA, tipoActivo(3, null), 1);

		assertThat(mvc.post().uri("/interno/reservas/" + id + "/liberacion").exchange())
				.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post().uri("/interno/reservas/" + id + "/liberacion").with(cliente(ANA)).exchange())
				.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(estadoReserva(id)).isEqualTo("VIGENTE");
	}

	// RF-2, RF-3 y RF-5: lo que Pagos necesita para cobrar, solo del dueño
	@Test
	void datosParaCobrarSoloDelDueno() throws Exception {
		Integer id = idReserva(ANA, tipoActivo(3, null), 2);

		MvcTestResult propia = mvc.get().uri("/interno/reservas/" + id + "?cliente=" + ANA).with(servicio()).exchange();
		assertThat(propia).hasStatusOk();
		assertThat(propia).bodyJson().extractingPath("$.estado").isEqualTo("VIGENTE");
		assertThat(propia).bodyJson().extractingPath("$.total").isEqualTo(100.0);
		assertThat(propia).bodyJson().extractingPath("$.concepto").isEqualTo("2 × General — Noche de jazz");
		assertThat(mvc.get().uri("/interno/reservas/" + id + "?cliente=" + LUIS).with(servicio()).exchange())
				.hasStatus(HttpStatus.NOT_FOUND);

		vencerLasReservas();
		assertThat(mvc.get().uri("/interno/reservas/" + id + "?cliente=" + ANA).with(servicio()).exchange())
				.bodyJson().extractingPath("$.estado").isEqualTo("EXPIRADA");
	}

	// RF-11, RF-12 y RF-17: una entrada activa por unidad, con su código, y los dos correos después del commit
	@Test
	void confirmarEmiteLasEntradasYPideLosDosCorreos() throws Exception {
		Integer tipo = tipoActivo(5, null);
		Integer id = idReserva(ANA, tipo, 3);

		resulta(confirmar(id, 41), "CONFIRMADA");

		assertThat(estadoReserva(id)).isEqualTo("CONFIRMADA");
		assertThat(disponible(tipo)).isEqualTo(2);
		List<Map<String, Object>> emitidas = jdbc.queryForList(
				"SELECT id_pago, id_usuario, estado, codigo_qr FROM entrada WHERE id_reserva = ?", id);
		assertThat(emitidas).hasSize(3).allSatisfy(entrada -> {
			assertThat(((Number) entrada.get("id_pago")).longValue()).isEqualTo(41L);
			assertThat(((Number) entrada.get("id_usuario")).longValue()).isEqualTo(ANA);
			assertThat(entrada.get("estado")).isEqualTo("ACTIVA");
		});
		List<String> codigos = emitidas.stream().map(entrada -> (String) entrada.get("codigo_qr")).toList();
		assertThat(codigos).doesNotHaveDuplicates();

		await().atMost(Duration.ofSeconds(5)).until(() -> PEDIDOS.size() == 2);
		Map<String, String> porRuta = new java.util.HashMap<>();
		PEDIDOS.forEach(pedido -> porRuta.put(pedido.getKey(), pedido.getValue()));
		assertThat(porRuta.get("/interno/correos/detalle-de-compra")).contains("\"correo\":\"ana@mail.com\"")
				.contains("\"evento\":\"Noche de jazz\"").contains("\"cantidad\":3").contains("\"total\":150");
		assertThat(porRuta.get("/interno/correos/entradas")).contains(codigos);
	}

	// RF-13: repetir el pedido (la respuesta se perdió) no emite de nuevo ni manda otros correos
	@Test
	void confirmarDosVecesEmiteUnaSola() throws Exception {
		Integer tipo = tipoActivo(5, null);
		Integer id = idReserva(ANA, tipo, 2);
		resulta(confirmar(id, 41), "CONFIRMADA");
		await().atMost(Duration.ofSeconds(5)).until(() -> PEDIDOS.size() == 2);

		resulta(confirmar(id, 41), "CONFIRMADA");

		assertThat(entradas(id)).isEqualTo(2);
		assertThat(disponible(tipo)).isEqualTo(3);
		Thread.sleep(300);
		assertThat(PEDIDOS).hasSize(2);
	}

	// RF-13 y RF-14: dos confirmaciones a la vez (la pantalla y la tarea) emiten una sola vez
	@Test
	void dosConfirmacionesALaVezEmitenUnaSolaVez() throws Exception {
		Integer tipo = tipoActivo(5, null);
		Integer id = idReserva(ANA, tipo, 2);

		CountDownLatch largada = new CountDownLatch(1);
		ExecutorService hilos = Executors.newFixedThreadPool(2);
		try {
			List<Future<ResultadoConfirmacion>> intentos = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				intentos.add(hilos.submit(() -> {
					largada.await();
					return confirmacionDeReservas.confirmar(id.longValue(), 41L);
				}));
			}
			largada.countDown();
			for (Future<ResultadoConfirmacion> intento : intentos) {
				assertThat(intento.get(10, TimeUnit.SECONDS)).isEqualTo(ResultadoConfirmacion.CONFIRMADA);
			}
		} finally {
			hilos.shutdownNow();
		}

		assertThat(entradas(id)).isEqualTo(2);
		assertThat(disponible(tipo)).isEqualTo(3);
	}

	// RF-15: el pago llegó tarde y el evento sigue vendiendo con cupo: se vuelve a descontar
	@Test
	void pagoTardioConCupoSeConfirma() throws Exception {
		Integer tipo = tipoActivo(3, null);
		Integer id = idReserva(ANA, tipo, 2);
		vencerLasReservas();

		resulta(confirmar(id, 41), "CONFIRMADA");

		assertThat(estadoReserva(id)).isEqualTo("CONFIRMADA");
		assertThat(entradas(id)).isEqualTo(2);
		assertThat(disponible(tipo)).isEqualTo(1);
	}

	// RF-16: sin cupo no se emite nada y la reserva queda vencida
	@Test
	void pagoTardioSinCupoNoEmite() throws Exception {
		Integer tipo = tipoActivo(2, null);
		Integer id = idReserva(ANA, tipo, 2);
		vencerLasReservas();
		idReserva(LUIS, tipo, 2);

		resulta(confirmar(id, 41), "SIN_CUPO");

		assertThat(estadoReserva(id)).isEqualTo("EXPIRADA");
		assertThat(entradas(id)).isZero();
		assertThat(disponible(tipo)).isZero();
		Thread.sleep(300);
		assertThat(PEDIDOS).isEmpty();
	}

	// RF-16 (decisión con el usuario): con la venta cerrada tampoco, aunque haya cupo
	@Test
	void pagoTardioConLaVentaCerradaNoEmite() throws Exception {
		Integer tipo = tipoActivo(5, null);
		Integer id = idReserva(ANA, tipo, 1);
		vencerLasReservas();
		jdbc.update("UPDATE evento SET venta_abierta = FALSE");

		resulta(confirmar(id, 41), "SIN_CUPO");

		assertThat(entradas(id)).isZero();
	}

	// RF-9 y CU-06 FA-05: un error del pago devuelve el cupo una sola vez; una confirmada no se toca
	@Test
	void liberarDevuelveElCupoDeUnaVigente() throws Exception {
		Integer tipo = tipoActivo(5, null);
		Integer id = idReserva(ANA, tipo, 2);

		assertThat(liberar(id)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(liberar(id)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(estadoReserva(id)).isEqualTo("EXPIRADA");
		assertThat(disponible(tipo)).isEqualTo(5);

		Integer confirmada = idReserva(ANA, tipo, 1);
		resulta(confirmar(confirmada, 42), "CONFIRMADA");
		assertThat(liberar(confirmada)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(estadoReserva(confirmada)).isEqualTo("CONFIRMADA");
		assertThat(disponible(tipo)).isEqualTo(4);
	}

	@Test
	void unaReservaInexistenteRespondeNoEncontrada() {
		assertThat(confirmar(999999, 41)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(liberar(999999)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mvc.post().uri("/interno/reservas/1/confirmacion").with(servicio())
				.contentType(MediaType.APPLICATION_JSON).content("{}").exchange()).hasStatus(HttpStatus.BAD_REQUEST);
	}

	// RF-21: las entradas ya emitidas cuentan para el límite de la siguiente reserva
	@Test
	void lasEntradasEmitidasCuentanParaElLimite() throws Exception {
		Integer tipo = tipoActivo(10, 3);
		resulta(confirmar(idReserva(ANA, tipo, 2), 41), "CONFIRMADA");

		assertThat(reservar(ANA, tipo, 2)).hasStatus(HttpStatus.CONFLICT);
		assertThat(reservar(ANA, tipo, 1)).hasStatus(HttpStatus.CREATED);
	}
}
