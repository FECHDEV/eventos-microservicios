package com.sarao.pagos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.sarao.pagos.entity.EstadoReserva;
import com.sarao.pagos.exception.ReservaNoEncontradaException;
import com.sarao.pagos.exception.ServicioNoDisponibleException;
import com.sarao.pagos.service.ConciliacionAutomaticaDePagos;
import com.sarao.pagos.service.ConsultaDeReservas;
import com.sarao.pagos.service.ConsultaDeReservas.ReservaParaCobrar;
import com.sarao.pagos.service.ConsultaDeReservas.ResultadoConfirmacion;
import com.sarao.pagos.service.PagoService;
import com.sarao.pagos.service.pago.SimuladorDePago;
import com.sarao.pagos.service.pago.SistemaDePagoNoDisponibleException;

// CU-07 del lado de Pagos, contra H2 y el simulador. Eventos y Venta es otro servicio: se reemplaza
// (RF-1 a RF-11, RF-13, RF-15, RF-16, RF-20, RF-22)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PagoIntegracionTest {

	private static final Long ANA = 7L;
	private static final Long LUIS = 8L;
	private static final Long RESERVA = 40L;
	private static final ZoneId LA_PAZ = ZoneId.of("America/La_Paz");

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PagoService pagoService;

	@Autowired
	private ConciliacionAutomaticaDePagos conciliacionAutomatica;

	@MockitoBean
	private ConsultaDeReservas consultaDeReservas;

	@MockitoSpyBean
	private SimuladorDePago simulador;

	private static RequestPostProcessor sesion(Long idUsuario, String rol) {
		return peticion -> {
			peticion.addHeader("X-Clave-Servicio", "clave-de-pruebas");
			peticion.addHeader("X-Usuario-Id", idUsuario.toString());
			peticion.addHeader("X-Usuario-Rol", rol);
			return peticion;
		};
	}

	private final RequestPostProcessor ana = sesion(ANA, "CLIENTE");
	private final RequestPostProcessor luis = sesion(LUIS, "CLIENTE");

	@BeforeEach
	void preparar() {
		given(consultaDeReservas.paraCobrar(RESERVA, ANA))
				.willReturn(new ReservaParaCobrar(EstadoReserva.VIGENTE, 540, new BigDecimal("100.00"), "2 × General"));
		given(consultaDeReservas.paraCobrar(RESERVA, LUIS)).willThrow(new ReservaNoEncontradaException());
		given(consultaDeReservas.confirmar(anyLong(), anyLong())).willReturn(ResultadoConfirmacion.CONFIRMADA);
	}

	@AfterEach
	void limpiar() {
		jdbc.update("DELETE FROM pago");
	}

	private MvcTestResult pagar(RequestPostProcessor sesion) {
		return mvc.post().uri("/api/reservas/" + RESERVA + "/pago").with(sesion).exchange();
	}

	private MvcTestResult consultar(RequestPostProcessor sesion) {
		return mvc.get().uri("/api/reservas/" + RESERVA + "/pago").with(sesion).exchange();
	}

	private MvcTestResult simular(String resultado) {
		return mvc.post().uri("/api/reservas/" + RESERVA + "/pago/simulacion").with(ana)
				.contentType(MediaType.APPLICATION_JSON).content("{\"resultado\":\"" + resultado + "\"}").exchange();
	}

	private static void tiene(MvcTestResult respuesta, String ruta, Object valor) {
		assertThat(respuesta).hasStatusOk();
		assertThat(respuesta).bodyJson().extractingPath(ruta).isEqualTo(valor);
	}

	private static void tieneCodigo(MvcTestResult respuesta, HttpStatus estado, String codigo) {
		assertThat(respuesta).hasStatus(estado);
		assertThat(respuesta).bodyJson().extractingPath("$.codigo").isEqualTo(codigo);
	}

	private String columna(String nombre) {
		return String.valueOf(jdbc.queryForObject("SELECT " + nombre + " FROM pago WHERE id_reserva = ?", Object.class,
				RESERVA));
	}

	private void vencerElPago() {
		jdbc.update("UPDATE pago SET fecha_expiracion = ?", LocalDateTime.now(LA_PAZ).minusSeconds(1));
	}

	// RF-1
	@Test
	void soloElClientePaga() {
		tieneCodigo(mvc.post().uri("/api/reservas/" + RESERVA + "/pago").exchange(), HttpStatus.UNAUTHORIZED,
				"NO_AUTENTICADO");
		tieneCodigo(pagar(sesion(1L, "ADMINISTRADOR")), HttpStatus.FORBIDDEN, "ACCESO_DENEGADO");
		assertThat(mvc.get().uri("/api/otra-cosa").with(ana).exchange()).hasStatus(HttpStatus.FORBIDDEN);
	}

	// RF-3 y RF-4: el cobro por el total, pendiente, y uno solo aunque se pida dos veces
	@Test
	void pagarGeneraUnSoloCobro() throws Exception {
		MvcTestResult cobro = pagar(ana);
		tiene(cobro, "$.estado", "PENDIENTE");
		tiene(cobro, "$.estadoReserva", "VIGENTE");
		tiene(cobro, "$.simulador", true);
		tiene(cobro, "$.requiereDevolucion", false);
		assertThat(cobro).bodyJson().extractingPath("$.codigoQr").asString().startsWith("SIMULADOR|SIM-");
		assertThat(cobro).bodyJson().extractingPath("$.segundosRestantes").asNumber().satisfies(
				segundos -> assertThat(segundos.intValue()).isBetween(535, 540));
		assertThat(cobro.getResponse().getContentAsString()).doesNotContain("referencia").doesNotContain("SIM-\"");

		MvcTestResult otra = pagar(ana);
		assertThat(otra.getResponse().getContentAsString().replaceAll("\"segundosRestantes\":\\d+", ""))
				.isEqualTo(cobro.getResponse().getContentAsString().replaceAll("\"segundosRestantes\":\\d+", ""));
		then(simulador).should(times(1)).generarCobro(eq(RESERVA), any(), eq("2 × General"));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pago", Integer.class)).isEqualTo(1);
		assertThat(columna("monto")).isEqualTo("100.00");
		assertThat(columna("id_usuario")).isEqualTo("7");
	}

	// RF-2: la reserva de otro, igual que una inexistente; su pago tampoco se ve ni se simula
	@Test
	void elPagoAjenoNoSeVe() {
		pagar(ana);

		tieneCodigo(pagar(luis), HttpStatus.NOT_FOUND, "RESERVA_NO_ENCONTRADA");
		tieneCodigo(consultar(luis), HttpStatus.NOT_FOUND, "PAGO_NO_ENCONTRADO");
		tieneCodigo(mvc.post().uri("/api/reservas/" + RESERVA + "/pago/simulacion").with(luis)
				.contentType(MediaType.APPLICATION_JSON).content("{\"resultado\":\"PAGADO\"}").exchange(),
				HttpStatus.NOT_FOUND, "PAGO_NO_ENCONTRADO");
		tieneCodigo(consultar(sesion(9L, "CLIENTE")), HttpStatus.NOT_FOUND, "PAGO_NO_ENCONTRADO");
	}

	// RF-5 y la caída de Eventos y Venta: sin cobro
	@Test
	void sinReservaVigenteNoHayCobro() {
		given(consultaDeReservas.paraCobrar(RESERVA, ANA))
				.willReturn(new ReservaParaCobrar(EstadoReserva.EXPIRADA, 0, new BigDecimal("100.00"), "x"));
		tieneCodigo(pagar(ana), HttpStatus.CONFLICT, "RESERVA_NO_VIGENTE");

		given(consultaDeReservas.paraCobrar(RESERVA, ANA)).willThrow(new ServicioNoDisponibleException());
		tieneCodigo(pagar(ana), HttpStatus.SERVICE_UNAVAILABLE, "SERVICIO_NO_DISPONIBLE");

		tieneCodigo(pagar(luis), HttpStatus.NOT_FOUND, "RESERVA_NO_ENCONTRADA");
		then(simulador).should(never()).generarCobro(any(), any(), any());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pago", Integer.class)).isZero();
	}

	// RF-6: el sistema de pago no genera el cobro: no hay Pago y la reserva se libera
	@Test
	void sinCobroDelSistemaDePagoSeLiberaLaReserva() {
		willThrow(new SistemaDePagoNoDisponibleException("caído", null)).given(simulador)
				.generarCobro(any(), any(), any());

		tieneCodigo(pagar(ana), HttpStatus.BAD_GATEWAY, "ERROR_SISTEMA_PAGO");

		then(consultaDeReservas).should().liberar(RESERVA);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pago", Integer.class)).isZero();
	}

	@Test
	void sinCobroLaConsultaOfrecePagar() {
		tieneCodigo(consultar(ana), HttpStatus.NOT_FOUND, "PAGO_NO_ENCONTRADO");
	}

	// RF-8: el sistema de pago todavía no tiene el pago
	@Test
	void unPagoTodaviaPendienteSigueEsperando() {
		pagar(ana);

		tiene(consultar(ana), "$.estado", "PENDIENTE");
		then(consultaDeReservas).should(never()).confirmar(anyLong(), anyLong());
	}

	// RF-7, RF-11 y RF-13: el pago se confirma consultando al sistema de pago, una sola vez
	@Test
	void unPagoRealizadoConfirmaLaCompraUnaSolaVez() {
		pagar(ana);
		assertThat(simular("PAGADO")).hasStatus(HttpStatus.NO_CONTENT);

		MvcTestResult pagado = consultar(ana);
		tiene(pagado, "$.estado", "PAGADO");
		tiene(pagado, "$.estadoReserva", "CONFIRMADA");
		tiene(pagado, "$.segundosRestantes", 0);
		consultar(ana);
		conciliacionAutomatica.conciliarPendientes();

		Long idPago = jdbc.queryForObject("SELECT id_pago FROM pago", Long.class);
		then(consultaDeReservas).should(times(1)).confirmar(RESERVA, idPago);
		assertThat(columna("estado")).isEqualTo("PAGADO");
		assertThat(columna("fecha_pago")).isNotEqualTo("null");
		assertThat(columna("confirmacion_pendiente")).isEqualTo("false");
	}

	// RF-9: un error del sistema de pago libera la reserva y no confirma nada
	@Test
	void unErrorDelSistemaDePagoLiberaLaReserva() {
		pagar(ana);
		simular("ERROR");

		MvcTestResult conError = consultar(ana);
		tiene(conError, "$.estado", "ERROR");
		tiene(conError, "$.estadoReserva", "EXPIRADA");
		consultar(ana);

		then(consultaDeReservas).should(times(1)).liberar(RESERVA);
		then(consultaDeReservas).should(never()).confirmar(anyLong(), anyLong());
	}

	// RF-10 y RF-20: vence sin pago, la tarea lo sigue mirando, y un pago tardío con cupo se confirma (RF-15)
	@Test
	void unPagoTardioConCupoSeConfirma() {
		pagar(ana);
		vencerElPago();

		tiene(consultar(ana), "$.estado", "EXPIRADO");
		assertThat(columna("estado")).isEqualTo("EXPIRADO");

		simular("PAGADO");
		conciliacionAutomatica.conciliarPendientes();

		assertThat(columna("estado")).isEqualTo("PAGADO");
		assertThat(columna("requiere_devolucion")).isEqualTo("false");
		tiene(consultar(ana), "$.estadoReserva", "CONFIRMADA");
	}

	// RF-16: llegó tarde y sin cupo: pagado, para devolución, sin entradas
	@Test
	void unPagoTardioSinCupoQuedaParaDevolucion() {
		given(consultaDeReservas.confirmar(anyLong(), anyLong())).willReturn(ResultadoConfirmacion.SIN_CUPO);
		pagar(ana);
		vencerElPago();
		simular("PAGADO");

		MvcTestResult devolucion = consultar(ana);
		tiene(devolucion, "$.estado", "PAGADO");
		tiene(devolucion, "$.requiereDevolucion", true);
		tiene(devolucion, "$.estadoReserva", "EXPIRADA");
	}

	// Respuesta perdida: el pago queda pagado y la tarea repite la confirmación, que es idempotente
	@Test
	void unaConfirmacionQueFallaSeRepite() {
		given(consultaDeReservas.confirmar(anyLong(), anyLong())).willThrow(new ServicioNoDisponibleException());
		pagar(ana);
		simular("PAGADO");

		tiene(consultar(ana), "$.estado", "PENDIENTE");
		assertThat(columna("estado")).isEqualTo("PAGADO");
		assertThat(columna("confirmacion_pendiente")).isEqualTo("true");

		reset(consultaDeReservas);
		given(consultaDeReservas.confirmar(anyLong(), anyLong())).willReturn(ResultadoConfirmacion.CONFIRMADA);
		vencerElPago();
		conciliacionAutomatica.conciliarPendientes();

		assertThat(columna("confirmacion_pendiente")).isEqualTo("false");
		tiene(consultar(ana), "$.estadoReserva", "CONFIRMADA");
	}

	// RF-13: la pantalla y la tarea a la vez piden una sola confirmación
	@Test
	void dosVerificacionesALaVezConfirmanUnaSolaVez() throws Exception {
		willAnswer(invocacion -> {
			Thread.sleep(300);
			return ResultadoConfirmacion.CONFIRMADA;
		}).given(consultaDeReservas).confirmar(anyLong(), anyLong());
		pagar(ana);
		simular("PAGADO");

		CountDownLatch largada = new CountDownLatch(1);
		ExecutorService hilos = Executors.newFixedThreadPool(2);
		try {
			List<Future<?>> intentos = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				intentos.add(hilos.submit(() -> {
					largada.await();
					return pagoService.verificar(ANA, RESERVA);
				}));
			}
			largada.countDown();
			for (Future<?> intento : intentos) {
				intento.get(10, TimeUnit.SECONDS);
			}
		} finally {
			hilos.shutdownNow();
		}

		then(consultaDeReservas).should(times(1)).confirmar(anyLong(), anyLong());
		assertThat(columna("confirmacion_pendiente")).isEqualTo("false");
	}

	// RF-20: pasadas las 24 horas un pendiente ya no se consulta; uno pagado sin confirmar, siempre
	@Test
	void laTareaDejaDeMirarLosPendientesViejos() {
		pagar(ana);
		jdbc.update("UPDATE pago SET fecha_expiracion = ?", LocalDateTime.now(LA_PAZ).minusHours(25));

		conciliacionAutomatica.conciliarPendientes();
		then(simulador).should(never()).consultar(any());

		jdbc.update("UPDATE pago SET estado = 'PAGADO', confirmacion_pendiente = TRUE");
		conciliacionAutomatica.conciliarPendientes();
		then(consultaDeReservas).should(times(1)).confirmar(anyLong(), anyLong());
	}

	// RF-22: el simulador solo fuerza un pago o un error, y solo con cobro
	@Test
	void elSimuladorSoloForzaPagoOError() {
		tieneCodigo(simular("PAGADO"), HttpStatus.NOT_FOUND, "PAGO_NO_ENCONTRADO");
		pagar(ana);

		tieneCodigo(simular("PENDIENTE"), HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS");
		tieneCodigo(simular("OTRO"), HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS");
		tieneCodigo(mvc.post().uri("/api/reservas/" + RESERVA + "/pago/simulacion").with(ana)
				.contentType(MediaType.APPLICATION_JSON).content("{}").exchange(), HttpStatus.BAD_REQUEST,
				"DATOS_INVALIDOS");
	}

	@Test
	void unIdQueNoEsNumeroEsDatoInvalido() {
		tieneCodigo(mvc.get().uri("/api/reservas/abc/pago").with(ana).exchange(), HttpStatus.BAD_REQUEST,
				"DATOS_INVALIDOS");
	}

	// El aviso del sistema de pago: sin sesión, siempre 200, y solo adelanta la consulta
	@Test
	void elAvisoAdelantaLaConsultaSinConfirmarPorSiSolo() {
		pagar(ana);
		String referencia = jdbc.queryForObject("SELECT referencia_externa FROM pago", String.class);

		assertThat(mvc.get().uri("/api/pagos/aviso?referencia=" + referencia).exchange()).hasStatusOk();
		assertThat(columna("estado")).isEqualTo("PENDIENTE");

		simular("PAGADO");
		assertThat(mvc.post().uri("/api/pagos/aviso?referencia=" + referencia).exchange()).hasStatusOk();
		assertThat(columna("estado")).isEqualTo("PAGADO");
		then(consultaDeReservas).should(times(1)).confirmar(anyLong(), anyLong());

		for (String otra : new String[] { "", "SIM-ajena", "x".repeat(500) }) {
			assertThat(mvc.get().uri("/api/pagos/aviso?referencia=" + otra).exchange()).hasStatusOk();
		}
		assertThat(mvc.get().uri("/api/pagos/aviso").exchange()).hasStatusOk();
		then(simulador).should(never()).consultar("SIM-ajena");
	}

	// CU-14: los pagos confirmados para los reportes; ventas sin los de devolución; solo para otro servicio
	@Test
	void losPagosCobradosSoloSeLosDaAOtroServicio() throws Exception {
		pagar(ana);
		simular("PAGADO");
		consultar(ana);
		var servicio = (org.springframework.test.web.servlet.request.RequestPostProcessor) peticion -> {
			peticion.addHeader("X-Clave-Servicio", "clave-de-pruebas");
			return peticion;
		};

		MvcTestResult ventas = mvc.get().uri("/interno/pagos/cobrados?conDevolucion=false").with(servicio).exchange();
		assertThat(ventas).hasStatusOk();
		assertThat(ventas).bodyJson().extractingPath("$[0].idReserva").isEqualTo(40);
		assertThat(ventas).bodyJson().extractingPath("$[0].monto").isEqualTo(100.0);
		assertThat(ventas.getResponse().getContentAsString()).doesNotContain("SIM-").doesNotContain("SIMULADOR");

		jdbc.update("UPDATE pago SET requiere_devolucion = TRUE");
		assertThat(mvc.get().uri("/interno/pagos/cobrados?conDevolucion=false").with(servicio).exchange())
				.bodyJson().extractingPath("$.length()").isEqualTo(0);
		assertThat(mvc.get().uri("/interno/pagos/cobrados?conDevolucion=true&desde=2000-01-01T00:00:00").with(servicio)
				.exchange()).bodyJson().extractingPath("$.length()").isEqualTo(1);
		assertThat(mvc.get().uri("/interno/pagos/cobrados?conDevolucion=true&hasta=2000-01-01T00:00:00").with(servicio)
				.exchange()).bodyJson().extractingPath("$.length()").isEqualTo(0);

		assertThat(mvc.get().uri("/interno/pagos/cobrados?conDevolucion=true").exchange())
				.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.get().uri("/interno/pagos/cobrados?conDevolucion=true").with(ana).exchange())
				.hasStatus(HttpStatus.FORBIDDEN);
	}
}
