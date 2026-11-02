package com.sarao.eventos;

import static com.sarao.eventos.SesionDelGateway.administrador;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import com.sarao.eventos.exception.ServicioNoDisponibleException;
import com.sarao.eventos.service.ConfirmacionDeReservas;
import com.sarao.eventos.service.ConsultaDeClientes;
import com.sarao.eventos.service.ConsultaDePagos;
import com.sarao.eventos.service.CorreosDeCompra;
import com.sarao.eventos.service.Cuenta;
import com.sarao.eventos.service.PagoCobrado;
import com.sarao.eventos.service.PedidoDeCorreoDeReporte;
import com.sarao.eventos.service.ValidacionDeEntradas;

// CU-14 contra H2. Seguridad, Pagos y Notificaciones son otros servicios: se reemplazan (RF-1 a RF-24)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportesIntegracionTest {

	private static final Long ADMIN = 1L;
	private static final Long ANA = 7L;
	private static final Long LUIS = 8L;
	private static final Long PEDRO = 20L;
	private static final ZoneId LA_PAZ = ZoneId.of("America/La_Paz");
	private static final LocalDate HOY = LocalDate.now(LA_PAZ);
	private static final LocalDateTime PAGADO = HOY.atTime(9, 30);

	private static final Map<Long, Cuenta> CUENTAS = List.of(
			new Cuenta(ADMIN, "Administrador", "Inicial", "admin@mail.com", null, HOY.atStartOfDay(), "ACTIVA"),
			new Cuenta(ANA, "Ana", "Rojas", "ana@mail.com", "70000000", HOY.atTime(8, 0), "ACTIVA"),
			new Cuenta(LUIS, "Luis", "Paz", "luis@mail.com", null, HOY.atTime(8, 5), "DESACTIVADA"),
			new Cuenta(PEDRO, "Pedro", "Suárez", "pedro@mail.com", null, HOY.atStartOfDay(), "ACTIVA"))
			.stream().collect(Collectors.toMap(Cuenta::idUsuario, Function.identity()));

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

	@MockitoBean
	private ConsultaDePagos consultaDePagos;

	@MockitoBean
	private PedidoDeCorreoDeReporte pedidoDeCorreo;

	@MockitoBean
	private CorreosDeCompra correosDeCompra;

	private final RequestPostProcessor admin = administrador();
	private int idEvento;
	private int idTipo;
	private Integer reservaDeAna;

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
		given(consultaDeClientes.clientes(any(), any())).willReturn(List.of(CUENTAS.get(ANA), CUENTAS.get(LUIS)));

		// Un evento de hoy con un tipo; Ana compra 2 entradas (pago 41), usa una y devuelve otra
		MvcTestResult alta = mvc.post().uri("/api/eventos").with(admin).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nombre":"Noche de jazz","fecha":"%s","horaInicio":"20:00","horaFin":"23:00","lugar":"Teatro"}
						""".formatted(HOY.plusDays(5))).exchange();
		idEvento = JsonPath.read(alta.getResponse().getContentAsString(), "$.idEvento");
		MvcTestResult tipo = mvc.post().uri("/api/eventos/" + idEvento + "/tipos-entrada").with(admin)
				.contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"General\",\"precio\":50.00,\"cantidadTotal\":10}")
				.exchange();
		idTipo = JsonPath.read(tipo.getResponse().getContentAsString(), "$.idTipoEntrada");
		mvc.post().uri("/api/eventos/" + idEvento + "/activacion").with(admin).exchange();
		MvcTestResult reserva = mvc.post().uri("/api/reservas").with(cliente(ANA)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"idTipoEntrada\":%d,\"cantidad\":2}".formatted(idTipo)).exchange();
		reservaDeAna = JsonPath.read(reserva.getResponse().getContentAsString(), "$.idReserva");
		confirmacionDeReservas.confirmar(reservaDeAna.longValue(), 41L);
		jdbc.update("UPDATE evento SET fecha = ?, hora_inicio = '23:59:00', hora_fin = '23:58:00'", HOY);
		List<String> codigos = jdbc.queryForList("SELECT codigo_qr FROM entrada ORDER BY id_entrada", String.class);
		validacionDeEntradas.validar(PEDRO, (long) idEvento, codigos.get(0));
		jdbc.update("UPDATE entrada SET estado = 'DEVUELTA' WHERE codigo_qr = ?", codigos.get(1));

		given(consultaDePagos.cobrados(anyBoolean(), any(), any())).willReturn(List.of(
				new PagoCobrado(41L, reservaDeAna.longValue(), new BigDecimal("100.00"), PAGADO, false),
				// Un pago de una reserva que Eventos y Venta no conoce: no aparece
				new PagoCobrado(99L, 999999L, new BigDecimal("10.00"), PAGADO, false)));
	}

	@AfterEach
	void limpiar() {
		jdbc.update("DELETE FROM reporte");
		jdbc.update("DELETE FROM acceso");
		jdbc.update("DELETE FROM entrada");
		jdbc.update("DELETE FROM reserva");
		jdbc.update("DELETE FROM tipo_entrada");
		jdbc.update("DELETE FROM evento");
	}

	private MvcTestResult generar(String cuerpo) {
		return mvc.post().uri("/api/reportes").with(admin).contentType(MediaType.APPLICATION_JSON).content(cuerpo)
				.exchange();
	}

	private static void tiene(MvcTestResult respuesta, String ruta, Object valor) {
		assertThat(respuesta).bodyJson().extractingPath(ruta).isEqualTo(valor);
	}

	private int reportes() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM reporte", Integer.class);
	}

	private Integer idReporte(MvcTestResult generado) throws Exception {
		assertThat(generado).hasStatus(HttpStatus.CREATED);
		return JsonPath.read(generado.getResponse().getContentAsString(), "$.idReporte");
	}

	// RF-1
	@Test
	void soloElAdministradorGeneraReportes() {
		assertThat(mvc.get().uri("/api/reportes/opciones").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.get().uri("/api/reportes/opciones").with(cliente(ANA)).exchange()).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.post().uri("/api/reportes").with(cliente(ANA)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"tipo\":\"VENTAS\"}").exchange()).hasStatus(HttpStatus.FORBIDDEN);
	}

	// RF-2: los eventos que venden, con sus tipos, el Personal que validó y el dominio del correo
	@Test
	void lasOpcionesTraenEventosPersonalYDominio() {
		MvcTestResult opciones = mvc.get().uri("/api/reportes/opciones").with(admin).exchange();

		assertThat(opciones).hasStatusOk();
		tiene(opciones, "$.eventos[0].nombre", "Noche de jazz");
		tiene(opciones, "$.eventos[0].tipos[0].nombre", "General");
		tiene(opciones, "$.personal[0].nombre", "Pedro Suárez");
		tiene(opciones, "$.dominioDeCorreo", "mail.com");
	}

	// RF-7 y RF-13: ventas con resumen y detalle, y queda registrado
	@Test
	void ventasMuestraLasComprasYSeRegistra() throws Exception {
		MvcTestResult ventas = generar("{\"tipo\":\"VENTAS\",\"desde\":\"%s\"}".formatted(HOY.minusDays(1)));

		idReporte(ventas);
		tiene(ventas, "$.resumen.filas.length()", 1);
		tiene(ventas, "$.resumen.filas[0][2]", 1);
		tiene(ventas, "$.resumen.filas[0][3]", 2);
		tiene(ventas, "$.resumen.filas[0][4]", 100.0);
		tiene(ventas, "$.detalle.filas[0][1]", "Ana Rojas");
		tiene(ventas, "$.detalle.filas[0][2]", "ana@mail.com");
		assertThat(reportes()).isEqualTo(1);
		then(consultaDePagos).should().cobrados(false, HOY.minusDays(1).atStartOfDay(), null);
		assertThat(jdbc.queryForObject("SELECT id_usuario FROM reporte", Long.class)).isEqualTo(ADMIN);
	}

	// RF-12: sin resultados no se registra; el filtro por otro evento deja afuera el pago
	@Test
	void sinResultadosNoSeRegistra() throws Exception {
		MvcTestResult otro = mvc.post().uri("/api/eventos").with(admin).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nombre":"Tango","fecha":"%s","horaInicio":"20:00","horaFin":"23:00","lugar":"Teatro"}
						""".formatted(HOY.plusDays(5))).exchange();
		int tango = JsonPath.read(otro.getResponse().getContentAsString(), "$.idEvento");

		MvcTestResult vacio = generar("{\"tipo\":\"VENTAS\",\"idEvento\":%d}".formatted(tango));

		assertThat(vacio).hasStatus(HttpStatus.NOT_FOUND);
		tiene(vacio, "$.codigo", "REPORTE_SIN_RESULTADOS");
		assertThat(reportes()).isZero();
	}

	// RF-8: el bruto, lo devuelto (la entrada devuelta, a su precio congelado) y el neto
	@Test
	void ingresosSeparaLoDevuelto() throws Exception {
		MvcTestResult ingresos = generar("{\"tipo\":\"INGRESOS\"}");

		assertThat(ingresos).hasStatus(HttpStatus.CREATED);
		tiene(ingresos, "$.resumen.totales", List.of("Total", 100.0, 50.0, 50.0));
		then(consultaDePagos).should().cobrados(true, null, null);
	}

	// RF-9: por estado, sin el código de la entrada
	@Test
	void entradasCuentaPorEstadoSinElCodigo() throws Exception {
		MvcTestResult entradas = generar("{\"tipo\":\"ENTRADAS\",\"idEvento\":%d}".formatted(idEvento));

		assertThat(entradas).hasStatus(HttpStatus.CREATED);
		tiene(entradas, "$.resumen.filas[0]", List.of("Noche de jazz", "General", 0, 1, 1, 2));
		tiene(entradas, "$.detalle.filas[0][3]", "Ana Rojas");
		for (String codigo : jdbc.queryForList("SELECT codigo_qr FROM entrada", String.class)) {
			assertThat(entradas.getResponse().getContentAsString()).doesNotContain(codigo);
		}
	}

	// RF-10: con evento, solo los que tienen entradas de ese evento
	@Test
	void clientesConEventoSoloLosQueCompraron() throws Exception {
		MvcTestResult todos = generar("{\"tipo\":\"CLIENTES\"}");
		tiene(todos, "$.detalle.filas.length()", 2);
		tiene(todos, "$.resumen.totales", List.of("Total", 2, 1, 2));

		MvcTestResult delEvento = generar("{\"tipo\":\"CLIENTES\",\"idEvento\":%d}".formatted(idEvento));
		tiene(delEvento, "$.detalle.filas.length()", 1);
		tiene(delEvento, "$.detalle.filas[0][0]", "Ana Rojas");
	}

	// RF-11: con el nombre del Personal y del Cliente
	@Test
	void accesosConElPersonal() throws Exception {
		MvcTestResult accesos = generar("{\"tipo\":\"ACCESOS\",\"idPersonal\":%d}".formatted(PEDRO));

		assertThat(accesos).hasStatus(HttpStatus.CREATED);
		tiene(accesos, "$.resumen.filas[0]", List.of("Noche de jazz", "Pedro Suárez", 1));
		tiene(accesos, "$.detalle.filas[0][3]", "Ana Rojas");
		tiene(accesos, "$.criterios[0].valor", "Pedro Suárez");
	}

	// RF-4
	@Test
	void criteriosInvalidosSeRechazan() {
		assertThat(generar("{\"tipo\":\"VENTAS\",\"desde\":\"2026-10-05\",\"hasta\":\"2026-10-04\"}"))
				.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(generar("{\"tipo\":\"VENTAS\",\"idTipoEntrada\":%d}".formatted(idTipo)))
				.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(generar("{\"tipo\":\"VENTAS\",\"estado\":\"ACTIVA\"}")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(generar("{\"tipo\":\"VENTAS\",\"idEvento\":999999}")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(reportes()).isZero();
	}

	// RF-14 a RF-16: PDF y Excel, sin registrar otro reporte
	@Test
	void exportarNoRegistraOtroReporte() throws Exception {
		Integer id = idReporte(generar("{\"tipo\":\"VENTAS\"}"));

		MvcTestResult pdf = mvc.get().uri("/api/reportes/" + id + "/pdf").with(admin).exchange();
		assertThat(pdf).hasStatusOk().hasContentType(MediaType.APPLICATION_PDF);
		assertThat(new String(pdf.getResponse().getContentAsByteArray(), 0, 5, StandardCharsets.US_ASCII))
				.isEqualTo("%PDF-");
		assertThat(pdf.getResponse().getHeader("Content-Disposition")).contains("reporte-ventas-" + id + ".pdf");

		MvcTestResult excel = mvc.get().uri("/api/reportes/" + id + "/excel").with(admin).exchange();
		assertThat(excel).hasStatusOk();
		assertThat(new String(excel.getResponse().getContentAsByteArray(), 0, 2, StandardCharsets.US_ASCII))
				.isEqualTo("PK");
		assertThat(reportes()).isEqualTo(1);
		assertThat(mvc.get().uri("/api/reportes/999999/pdf").with(admin).exchange()).hasStatus(HttpStatus.NOT_FOUND);
	}

	// RF-19 a RF-23: un solo pedido a Notificaciones, con los dos adjuntos y solo al dominio propio
	@SuppressWarnings("unchecked")
	@Test
	void enviarPorCorreoPideUnSoloCorreo() throws Exception {
		Integer id = idReporte(generar("{\"tipo\":\"VENTAS\"}"));

		MvcTestResult enviado = mvc.post().uri("/api/reportes/" + id + "/correo").with(admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"destinatarios\":[\"Gerencia@mail.com\",\" caja@mail.com \",\"gerencia@mail.com\"],\"formato\":\"AMBOS\"}")
				.exchange();

		assertThat(enviado).hasStatus(HttpStatus.ACCEPTED);
		tiene(enviado, "$.destinatarios", 2);
		ArgumentCaptor<Object> correo = ArgumentCaptor.forClass(Object.class);
		then(pedidoDeCorreo).should().pedir(correo.capture());
		Map<String, Object> pedido = (Map<String, Object>) correo.getValue();
		assertThat(pedido.get("destinatarios")).isEqualTo(List.of("gerencia@mail.com", "caja@mail.com"));
		assertThat(pedido.get("enviadoPor")).isEqualTo("Administrador Inicial");
		assertThat((List<Map<String, String>>) pedido.get("adjuntos")).extracting(adjunto -> adjunto.get("nombre"))
				.containsExactly("reporte-ventas-" + id + ".pdf", "reporte-ventas-" + id + ".xlsx");
		assertThat(reportes()).isEqualTo(1);
	}

	// RF-22: otra dirección se rechaza sin pedir nada
	@Test
	void unDestinatarioDeOtroDominioSeRechaza() throws Exception {
		Integer id = idReporte(generar("{\"tipo\":\"VENTAS\"}"));

		MvcTestResult rechazo = mvc.post().uri("/api/reportes/" + id + "/correo").with(admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"destinatarios\":[\"alguien@gmail.com\"],\"formato\":\"PDF\"}").exchange();

		assertThat(rechazo).hasStatus(HttpStatus.BAD_REQUEST);
		tiene(rechazo, "$.errores[0].campo", "destinatarios");
		then(pedidoDeCorreo).should(never()).pedir(any());
	}

	// Notificaciones o Seguridad caídos: 503, sin un «enviado» falso ni un reporte a medias
	@Test
	void sinLosOtrosServiciosRespondeNoDisponible() throws Exception {
		Integer id = idReporte(generar("{\"tipo\":\"VENTAS\"}"));
		willThrow(new ServicioNoDisponibleException()).given(pedidoDeCorreo).pedir(any());

		assertThat(mvc.post().uri("/api/reportes/" + id + "/correo").with(admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"destinatarios\":[\"caja@mail.com\"],\"formato\":\"PDF\"}").exchange())
				.hasStatus(HttpStatus.SERVICE_UNAVAILABLE);

		given(consultaDePagos.cobrados(anyBoolean(), any(), any())).willThrow(new ServicioNoDisponibleException());
		assertThat(generar("{\"tipo\":\"VENTAS\"}")).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(reportes()).isEqualTo(1);
	}
}
