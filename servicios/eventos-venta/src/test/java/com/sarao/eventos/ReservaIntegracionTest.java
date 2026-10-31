package com.sarao.eventos;

import static com.sarao.eventos.SesionDelGateway.administrador;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
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
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;

import com.sarao.eventos.dto.ReservaRequest;
import com.sarao.eventos.exception.ErrorNegocioException;
import com.sarao.eventos.service.ConsultaDeClientes;
import com.sarao.eventos.service.ReservaService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReservaIntegracionTest {

	private static final Long ANA = 7L;
	private static final Long LUIS = 8L;
	private static final ZoneId LA_PAZ = ZoneId.of("America/La_Paz");
	private static final LocalDate HOY = LocalDate.now(LA_PAZ);

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private ReservaService reservaService;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private ConsultaDeClientes consultaDeClientes;

	// Ana es mayor de edad y Luis, menor: sirve para la restricción. Seguridad es otro servicio
	private static final LocalDate MAYOR = LocalDate.of(2000, 5, 20);
	private final RequestPostProcessor ana = sesion(ANA, "CLIENTE");
	private final RequestPostProcessor luis = sesion(LUIS, "CLIENTE");

	private static RequestPostProcessor sesion(Long idUsuario, String rol) {
		return peticion -> {
			peticion.addHeader("X-Clave-Servicio", "clave-de-pruebas");
			peticion.addHeader("X-Usuario-Id", idUsuario.toString());
			peticion.addHeader("X-Usuario-Rol", rol);
			return peticion;
		};
	}

	@BeforeEach
	void prepararClientes() {
		given(consultaDeClientes.fechaNacimiento(any())).willReturn(MAYOR);
		given(consultaDeClientes.fechaNacimiento(LUIS)).willReturn(HOY.minusYears(14));
	}

	@AfterEach
	void limpiar() {
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

	// Un evento activo en 5 días con los tipos indicados ("General:3"); devuelve los ids de los tipos
	private List<Integer> activo(Integer restriccionEdad, Integer limiteCompra, String... tipos) throws Exception {
		String opcionales = (restriccionEdad == null ? "" : ",\"restriccionEdad\":" + restriccionEdad)
				+ (limiteCompra == null ? "" : ",\"limiteCompra\":" + limiteCompra);
		MvcTestResult alta = postComoAdmin("/api/eventos", """
				{"nombre":"Noche de jazz","fecha":"%s","horaInicio":"20:00","horaFin":"23:00","lugar":"Teatro"%s}
				""".formatted(HOY.plusDays(5), opcionales));
		assertThat(alta).hasStatus(HttpStatus.CREATED);
		Integer idEvento = JsonPath.read(alta.getResponse().getContentAsString(), "$.idEvento");
		List<Integer> ids = new ArrayList<>();
		for (String tipo : tipos) {
			String[] partes = tipo.split(":");
			MvcTestResult registrado = postComoAdmin("/api/eventos/" + idEvento + "/tipos-entrada",
					"{\"nombre\":\"%s\",\"precio\":50.00,\"cantidadTotal\":%s}".formatted(partes[0], partes[1]));
			assertThat(registrado).hasStatus(HttpStatus.CREATED);
			ids.add(JsonPath.read(registrado.getResponse().getContentAsString(), "$.idTipoEntrada"));
		}
		assertThat(postComoAdmin("/api/eventos/" + idEvento + "/activacion", null)).hasStatus(HttpStatus.OK);
		return ids;
	}

	private Integer idEventoDelTipo(Integer idTipo) {
		return jdbc.queryForObject("SELECT id_evento FROM tipo_entrada WHERE id_tipo_entrada = ?", Integer.class,
				idTipo);
	}

	private MvcTestResult reservar(RequestPostProcessor sesion, Integer idTipo, int cantidad) {
		return mvc.post().uri("/api/reservas").with(sesion)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"idTipoEntrada\":%d,\"cantidad\":%d}".formatted(idTipo, cantidad)).exchange();
	}

	private Integer idReserva(MvcTestResult reserva) throws Exception {
		assertThat(reserva).hasStatus(HttpStatus.CREATED);
		return JsonPath.read(reserva.getResponse().getContentAsString(), "$.idReserva");
	}

	private MvcTestResult consultar(RequestPostProcessor sesion, Integer idReserva) {
		return mvc.get().uri("/api/reservas/" + idReserva).with(sesion).exchange();
	}

	private boolean disponibleEnElCatalogo(Integer idTipo) throws Exception {
		MvcTestResult detalle = mvc.get().uri("/api/catalogo/" + idEventoDelTipo(idTipo)).exchange();
		assertThat(detalle).hasStatus(HttpStatus.OK);
		List<Object> disponible = JsonPath.read(detalle.getResponse().getContentAsString(),
				"$.tiposEntrada[?(@.idTipoEntrada == %d)].disponible".formatted(idTipo));
		return (Boolean) disponible.getFirst();
	}

	private int disponibleEnLaBase(Integer idTipo) {
		return jdbc.queryForObject("SELECT cantidad_disponible FROM tipo_entrada WHERE id_tipo_entrada = ?",
				Integer.class, idTipo);
	}

	// Mueve el vencimiento de todas las reservas un segundo al pasado
	private void vencerLasReservas() {
		jdbc.update("UPDATE reserva SET fecha_expiracion = ?", LocalDateTime.now(LA_PAZ).minusSeconds(1));
	}

	private static void tieneCodigo(MvcTestResult resultado, HttpStatus estado, String codigo) {
		assertThat(resultado).hasStatus(estado);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(codigo);
	}

	// Flujo principal y postcondición 2: reservar descuenta, el catálogo lo refleja y la reserva es solo del dueño
	@Test
	void reservarDescuentaYElCatalogoLoRefleja() throws Exception {
		Integer general = activo(null, null, "General:3").getFirst();

		MvcTestResult reserva = reservar(ana, general, 2);
		Integer id = idReserva(reserva);
		assertThat(reserva).bodyJson().extractingPath("$.estado").isEqualTo("VIGENTE");
		assertThat(reserva).bodyJson().extractingPath("$.total").isEqualTo(100.0);
		assertThat(disponibleEnLaBase(general)).isEqualTo(1);
		assertThat(disponibleEnElCatalogo(general)).isTrue();

		idReserva(reservar(ana, general, 1));
		assertThat(disponibleEnElCatalogo(general)).isFalse();
		tieneCodigo(reservar(ana, general, 1), HttpStatus.CONFLICT, "ENTRADAS_NO_DISPONIBLES");

		MvcTestResult propia = consultar(ana, id);
		assertThat(propia).hasStatusOk();
		assertThat(propia).bodyJson().extractingPath("$.cantidad").isEqualTo(2);
		tieneCodigo(consultar(luis, id), HttpStatus.NOT_FOUND, "RESERVA_NO_ENCONTRADA");
	}

	// FA-04 con verificación perezosa: pasado el plazo, cualquier lectura libera y el cupo vuelve una sola vez
	@Test
	void unaReservaVencidaDevuelveSuCupo() throws Exception {
		Integer general = activo(null, null, "General:2").getFirst();
		Integer id = idReserva(reservar(ana, general, 2));
		assertThat(disponibleEnElCatalogo(general)).isFalse();

		vencerLasReservas();

		assertThat(disponibleEnElCatalogo(general)).isTrue();
		assertThat(disponibleEnElCatalogo(general)).isTrue();
		assertThat(disponibleEnLaBase(general)).isEqualTo(2);
		MvcTestResult vencida = consultar(ana, id);
		assertThat(vencida).bodyJson().extractingPath("$.estado").isEqualTo("EXPIRADA");
		assertThat(vencida).bodyJson().extractingPath("$.segundosRestantes").isEqualTo(0);
		assertThat(jdbc.queryForObject("SELECT estado FROM reserva WHERE id_reserva = ?", String.class, id))
				.isEqualTo("EXPIRADA");
		idReserva(reservar(ana, general, 2));
		assertThat(disponibleEnLaBase(general)).isZero();
	}

	// Caso límite de CU-11: el mínimo de la cantidad total no cuenta las reservas vencidas
	@Test
	void laCantidadTotalPuedeBajarDespuesDeQueVenceUnaReserva() throws Exception {
		Integer general = activo(null, null, "General:5").getFirst();
		idReserva(reservar(ana, general, 3));
		vencerLasReservas();

		MvcTestResult cambio = mvc.put()
				.uri("/api/eventos/%d/tipos-entrada/%d".formatted(idEventoDelTipo(general), general))
				.with(administrador()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"General\",\"precio\":50.00,\"cantidadTotal\":1}").exchange();

		assertThat(cambio).hasStatus(HttpStatus.OK);
		assertThat(disponibleEnLaBase(general)).isEqualTo(1);
	}

	// RF-2, RF-6, RF-9 y RF-10 contra la base real
	@Test
	void rechazaPorRolEdadLimiteYVentaCerrada() throws Exception {
		List<Integer> tipos = activo(18, 3, "General:10", "VIP:10");
		Integer general = tipos.get(0);
		Integer vip = tipos.get(1);

		tieneCodigo(reservar(administrador(), general, 1), HttpStatus.FORBIDDEN, "ACCESO_DENEGADO");
		MvcTestResult menor = reservar(luis, general, 1);
		tieneCodigo(menor, HttpStatus.CONFLICT, "EDAD_NO_PERMITIDA");
		assertThat(menor).bodyJson().extractingPath("$.edadMinima").isEqualTo(18);

		idReserva(reservar(ana, general, 2));
		MvcTestResult excedido = reservar(ana, vip, 2);
		tieneCodigo(excedido, HttpStatus.CONFLICT, "LIMITE_COMPRA_EXCEDIDO");
		assertThat(excedido).bodyJson().extractingPath("$.puedeAgregar").isEqualTo(1);
		idReserva(reservar(ana, vip, 1));

		assertThat(postComoAdmin("/api/eventos/%d/cierre-de-venta".formatted(idEventoDelTipo(general)), null))
				.hasStatus(HttpStatus.OK);
		tieneCodigo(reservar(ana, general, 1), HttpStatus.CONFLICT, "VENTA_CERRADA");
	}

	// Caso límite: dos clientes por la última entrada. Uno la reserva; el otro no, y la disponible no queda negativa
	@Test
	void dosClientesPorElUltimoCupoSoloUnoLoConsigue() throws Exception {
		long general = activo(null, null, "General:1").getFirst();
		// La edad no importa acá: el evento no tiene restricción
		List<String> resultados = enParalelo(List.of(ANA, LUIS),
				List.of(new ReservaRequest(general, 1), new ReservaRequest(general, 1)));

		assertThat(resultados).containsExactlyInAnyOrder("reservada", "rechazada");
		assertThat(disponibleEnLaBase((int) general)).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reserva", Integer.class)).isEqualTo(1);
	}

	// Caso límite: el mismo cliente, dos compras simultáneas de tipos distintos que juntas pasan el límite
	@Test
	void dosComprasSimultaneasDelMismoClienteNoPasanElLimite() throws Exception {
		List<Integer> tipos = activo(null, 2, "General:10", "VIP:10");

		List<String> resultados = enParalelo(List.of(ANA, ANA), List.of(
				new ReservaRequest(tipos.get(0).longValue(), 2), new ReservaRequest(tipos.get(1).longValue(), 2)));

		assertThat(resultados).containsExactlyInAnyOrder("reservada", "rechazada");
		assertThat(jdbc.queryForObject("SELECT COALESCE(SUM(cantidad), 0) FROM reserva", Integer.class)).isEqualTo(2);
	}

	// Cada solicitud en su hilo, largando juntas. Un rechazo de negocio o una espera de bloqueo agotada cuentan igual:
	// la reserva no se hizo
	private List<String> enParalelo(List<Long> clientes, List<ReservaRequest> solicitudes) throws Exception {
		CountDownLatch largada = new CountDownLatch(1);
		ExecutorService hilos = Executors.newFixedThreadPool(clientes.size());
		try {
			List<Future<String>> intentos = new ArrayList<>();
			for (int i = 0; i < clientes.size(); i++) {
				Long idCliente = clientes.get(i);
				ReservaRequest solicitud = solicitudes.get(i);
				intentos.add(hilos.submit(() -> {
					largada.await();
					try {
						reservaService.reservar(idCliente, MAYOR, solicitud);
						return "reservada";
					} catch (ErrorNegocioException | PessimisticLockingFailureException e) {
						return "rechazada";
					}
				}));
			}
			largada.countDown();
			List<String> resultados = new ArrayList<>();
			for (Future<String> intento : intentos) {
				resultados.add(intento.get(10, TimeUnit.SECONDS));
			}
			return resultados;
		} finally {
			hilos.shutdownNow();
		}
	}
}
