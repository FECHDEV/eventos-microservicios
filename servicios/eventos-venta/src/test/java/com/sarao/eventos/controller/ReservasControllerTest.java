package com.sarao.eventos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.sarao.eventos.SesionDelGateway;
import com.sarao.eventos.config.SecurityConfig;
import com.sarao.eventos.dto.ReservaRequest;
import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.entity.Reserva;
import com.sarao.eventos.entity.TipoEntrada;
import com.sarao.eventos.exception.EdadNoPermitidaException;
import com.sarao.eventos.exception.EntradasNoDisponiblesException;
import com.sarao.eventos.exception.LimiteCompraExcedidoException;
import com.sarao.eventos.exception.ReservaNoEncontradaException;
import com.sarao.eventos.exception.ServicioNoDisponibleException;
import com.sarao.eventos.exception.TipoEntradaNoEncontradoException;
import com.sarao.eventos.exception.VentaCerradaException;
import com.sarao.eventos.service.ConsultaDeClientes;
import com.sarao.eventos.service.DetalleReserva;
import com.sarao.eventos.service.ReservaService;

@WebMvcTest(ReservasController.class)
@ActiveProfiles("test")
@Import(SecurityConfig.class)
class ReservasControllerTest {

	private static final String RESERVAS = "/api/reservas";
	private static final LocalDate NACIMIENTO = LocalDate.of(2000, 5, 20);
	private static final LocalDateTime AHORA = LocalDateTime.of(2026, 9, 21, 20, 0);
	private static final String TRES_GENERALES = """
			{"idTipoEntrada":3,"cantidad":3}
			""";

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private ConsultaDeClientes consultaDeClientes;

	@MockitoBean
	private ReservaService reservaService;

	private final RequestPostProcessor admin = SesionDelGateway.administrador();
	private final RequestPostProcessor ana = SesionDelGateway.cliente();
	private DetalleReserva detalle;

	@BeforeEach
	void preparar() {
		given(consultaDeClientes.fechaNacimiento(7L)).willReturn(NACIMIENTO);
		Evento jazz = Evento.registrar(1L, "Noche de jazz", null, LocalDate.of(2026, 10, 4), LocalTime.of(22, 0),
				LocalTime.of(3, 0), "Teatro Municipal", null, null);
		ReflectionTestUtils.setField(jazz, "idEvento", 5L);
		TipoEntrada general = TipoEntrada.registrar(jazz, "General", new BigDecimal("50"), 100);
		ReflectionTestUtils.setField(general, "idTipoEntrada", 3L);
		Reserva reserva = Reserva.crear(7L, general, 3, AHORA.minusSeconds(19));
		ReflectionTestUtils.setField(reserva, "idReserva", 40L);
		detalle = new DetalleReserva(reserva, general, jazz, AHORA);
	}

	private MvcTestResult reservar(String cuerpo, RequestPostProcessor sesion) {
		return mvc.post().uri(RESERVAS).with(sesion)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
	}

	private static void tieneCodigo(MvcTestResult resultado, HttpStatus estado, String codigo) {
		assertThat(resultado).hasStatus(estado);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(codigo);
	}

	// --- Acceso (RF-1, RF-2) ---

	@Test
	void sinSesionDevuelve401() {
		tieneCodigo(mvc.post().uri(RESERVAS).contentType(MediaType.APPLICATION_JSON).content(TRES_GENERALES)
				.exchange(), HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO");
		tieneCodigo(mvc.get().uri(RESERVAS + "/40").exchange(), HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO");
		then(reservaService).shouldHaveNoInteractions();
	}

	@Test
	void unAdministradorRecibe403() {
		tieneCodigo(reservar(TRES_GENERALES, admin), HttpStatus.FORBIDDEN, "ACCESO_DENEGADO");
		tieneCodigo(mvc.get().uri(RESERVAS + "/40").with(admin).exchange(),
				HttpStatus.FORBIDDEN, "ACCESO_DENEGADO");
		then(reservaService).shouldHaveNoInteractions();
	}

	// --- Reservar (RF-12, RF-15) ---

	@Test
	void reservaConElClienteDeLaSesionYDevuelve201ConElDetalle() {
		given(reservaService.reservar(7L, NACIMIENTO, new ReservaRequest(3L, 3))).willReturn(detalle);

		MvcTestResult resultado = reservar(TRES_GENERALES, ana);

		assertThat(resultado).hasStatus(HttpStatus.CREATED);
		assertThat(resultado).bodyJson().extractingPath("$.idReserva").isEqualTo(40);
		assertThat(resultado).bodyJson().extractingPath("$.idEvento").isEqualTo(5);
		assertThat(resultado).bodyJson().extractingPath("$.nombreEvento").isEqualTo("Noche de jazz");
		assertThat(resultado).bodyJson().extractingPath("$.fecha").isEqualTo("2026-10-04");
		assertThat(resultado).bodyJson().extractingPath("$.horaInicio").isEqualTo("22:00:00");
		assertThat(resultado).bodyJson().extractingPath("$.horaFin").isEqualTo("03:00:00");
		assertThat(resultado).bodyJson().extractingPath("$.lugar").isEqualTo("Teatro Municipal");
		assertThat(resultado).bodyJson().extractingPath("$.idTipoEntrada").isEqualTo(3);
		assertThat(resultado).bodyJson().extractingPath("$.tipoEntrada").isEqualTo("General");
		assertThat(resultado).bodyJson().extractingPath("$.cantidad").isEqualTo(3);
		assertThat(resultado).bodyJson().extractingPath("$.precioUnitario").isEqualTo(50.0);
		assertThat(resultado).bodyJson().extractingPath("$.total").isEqualTo(150.0);
		assertThat(resultado).bodyJson().extractingPath("$.fechaExpiracion").isEqualTo("2026-09-21T20:09:41");
		assertThat(resultado).bodyJson().extractingPath("$.segundosRestantes").isEqualTo(581);
		assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("VIGENTE");
	}

	// Un id de cliente en el cuerpo no cambia a quién se le reserva
	@Test
	void ignoraUnClienteEnElCuerpo() {
		given(reservaService.reservar(7L, NACIMIENTO, new ReservaRequest(3L, 3))).willReturn(detalle);

		assertThat(reservar("{\"idTipoEntrada\":3,\"cantidad\":3,\"idUsuario\":1}", ana)).hasStatus(HttpStatus.CREATED);
		then(reservaService).should().reservar(7L, NACIMIENTO, new ReservaRequest(3L, 3));
	}

	// RF-4
	@Test
	void unaCantidadInvalidaEs400ConElCampo() {
		for (String cuerpo : new String[] { "{\"idTipoEntrada\":3}", "{\"idTipoEntrada\":3,\"cantidad\":0}",
				"{\"idTipoEntrada\":3,\"cantidad\":2.5}", "{\"idTipoEntrada\":3,\"cantidad\":\"dos\"}",
				"{\"idTipoEntrada\":3,\"cantidad\":99999999999}" }) {
			MvcTestResult resultado = reservar(cuerpo, ana);
			tieneCodigo(resultado, HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS");
			assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").as(cuerpo).isEqualTo("cantidad");
		}
		then(reservaService).shouldHaveNoInteractions();
	}

	// Sin saber la edad del Cliente no se reserva, y la transacción ni empieza
	@Test
	void conSeguridadCaidaNoReserva() {
		given(consultaDeClientes.fechaNacimiento(7L)).willThrow(new ServicioNoDisponibleException());

		tieneCodigo(reservar(TRES_GENERALES, ana), HttpStatus.SERVICE_UNAVAILABLE, "SERVICIO_NO_DISPONIBLE");
		then(reservaService).shouldHaveNoInteractions();
	}

	@Test
	void sinTipoEs400() {
		MvcTestResult resultado = reservar("{\"cantidad\":1}", ana);

		tieneCodigo(resultado, HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS");
		assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("idTipoEntrada");
	}

	// RF-5, RF-6, RF-8 a RF-10: cada rechazo con su código, y los datos para explicarlo
	@Test
	void cadaRechazoSaleConSuCodigo() {
		given(reservaService.reservar(eq(7L), eq(NACIMIENTO), any())).willThrow(new TipoEntradaNoEncontradoException());
		tieneCodigo(reservar(TRES_GENERALES, ana), HttpStatus.NOT_FOUND, "TIPO_ENTRADA_NO_ENCONTRADO");

		given(reservaService.reservar(eq(7L), eq(NACIMIENTO), any())).willThrow(new VentaCerradaException());
		tieneCodigo(reservar(TRES_GENERALES, ana), HttpStatus.CONFLICT, "VENTA_CERRADA");

		given(reservaService.reservar(eq(7L), eq(NACIMIENTO), any())).willThrow(new EntradasNoDisponiblesException());
		tieneCodigo(reservar(TRES_GENERALES, ana), HttpStatus.CONFLICT, "ENTRADAS_NO_DISPONIBLES");

		given(reservaService.reservar(eq(7L), eq(NACIMIENTO), any())).willThrow(new EdadNoPermitidaException(18));
		MvcTestResult edad = reservar(TRES_GENERALES, ana);
		tieneCodigo(edad, HttpStatus.CONFLICT, "EDAD_NO_PERMITIDA");
		assertThat(edad).bodyJson().extractingPath("$.edadMinima").isEqualTo(18);

		given(reservaService.reservar(eq(7L), eq(NACIMIENTO), any())).willThrow(new LimiteCompraExcedidoException(4, 1));
		MvcTestResult limite = reservar(TRES_GENERALES, ana);
		tieneCodigo(limite, HttpStatus.CONFLICT, "LIMITE_COMPRA_EXCEDIDO");
		assertThat(limite).bodyJson().extractingPath("$.limite").isEqualTo(4);
		assertThat(limite).bodyJson().extractingPath("$.puedeAgregar").isEqualTo(1);
	}

	// --- Consultar (RF-20) ---

	@Test
	void consultaLaReservaDelClienteDeLaSesion() {
		given(reservaService.consultar(7L, 40L)).willReturn(detalle);

		MvcTestResult resultado = mvc.get().uri(RESERVAS + "/40").with(ana)
				.exchange();

		assertThat(resultado).hasStatusOk();
		assertThat(resultado).bodyJson().extractingPath("$.idReserva").isEqualTo(40);
		assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("VIGENTE");
	}

	// Pasado el plazo, la respuesta dice EXPIRADA y 0 segundos aunque el barrido no la haya marcado (RF-18)
	@Test
	void unaReservaVencidaSaleExpiradaYSinSegundos() {
		given(reservaService.consultar(7L, 40L)).willReturn(new DetalleReserva(detalle.reserva(), detalle.tipo(),
				detalle.evento(), AHORA.plusMinutes(10)));

		MvcTestResult resultado = mvc.get().uri(RESERVAS + "/40").with(ana)
				.exchange();

		assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("EXPIRADA");
		assertThat(resultado).bodyJson().extractingPath("$.segundosRestantes").isEqualTo(0);
	}

	@Test
	void unaReservaAjenaEs404() {
		given(reservaService.consultar(7L, 41L)).willThrow(new ReservaNoEncontradaException());

		tieneCodigo(mvc.get().uri(RESERVAS + "/41").with(ana).exchange(),
				HttpStatus.NOT_FOUND, "RESERVA_NO_ENCONTRADA");
	}

	@Test
	void unIdNoNumericoEs400() {
		tieneCodigo(mvc.get().uri(RESERVAS + "/abc").with(ana).exchange(),
				HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS");
	}
}
