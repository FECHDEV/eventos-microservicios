package com.sarao.eventos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.sarao.eventos.SesionDelGateway;
import com.sarao.eventos.config.SecurityConfig;
import com.sarao.eventos.entity.Entrada;
import com.sarao.eventos.entity.EstadoEntrada;
import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.entity.Reserva;
import com.sarao.eventos.entity.TipoEntrada;
import com.sarao.eventos.exception.EntradaNoEncontradaException;
import com.sarao.eventos.service.CompraDelCliente;
import com.sarao.eventos.service.ConsultaDeEntradas;
import com.sarao.eventos.service.DetalleEntrada;
import com.sarao.eventos.service.EntradasDelCliente;

@WebMvcTest(EntradasController.class)
@ActiveProfiles("test")
@Import(SecurityConfig.class)
class EntradasControllerTest {

	private static final String ENTRADAS = "/api/entradas";
	private static final LocalDateTime AHORA = LocalDateTime.of(2026, 9, 22, 20, 0);

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private ConsultaDeEntradas consultaDeEntradas;

	private final RequestPostProcessor admin = SesionDelGateway.administrador();
	private final RequestPostProcessor ana = SesionDelGateway.cliente();
	private CompraDelCliente compra;

	private static Entrada entrada(Reserva reserva, long id, String codigo) {
		Entrada entrada = Entrada.emitir(reserva, 30L, codigo, AHORA);
		ReflectionTestUtils.setField(entrada, "idEntrada", id);
		return entrada;
	}

	// Noche de jazz, activa: una compra de 2 General con la primera activa y la segunda utilizada
	@BeforeEach
	void preparar() {
		Evento jazz = Evento.registrar(1L, "Noche de jazz", null, LocalDate.of(2026, 10, 4), LocalTime.of(22, 0),
				LocalTime.of(3, 0), "Teatro Municipal", null, null);
		ReflectionTestUtils.setField(jazz, "idEvento", 5L);
		jazz.activar();
		TipoEntrada general = TipoEntrada.registrar(jazz, "General", new BigDecimal("50"), 100);
		Reserva reserva = Reserva.crear(7L, general, 2, AHORA);
		ReflectionTestUtils.setField(reserva, "idReserva", 40L);
		Entrada activa = entrada(reserva, 81L, "codigo-81");
		Entrada utilizada = entrada(reserva, 82L, "codigo-82");
		ReflectionTestUtils.setField(utilizada, "estado", EstadoEntrada.UTILIZADA);
		compra = new CompraDelCliente(reserva, general, jazz, List.of(activa, utilizada));
	}

	private MvcTestResult get(String uri, RequestPostProcessor sesion) {
		return mvc.get().uri(uri).with(sesion).exchange();
	}

	private static void tieneCodigo(MvcTestResult resultado, HttpStatus estado, String codigo) {
		assertThat(resultado).hasStatus(estado);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(codigo);
	}

	// RF-1
	@Test
	void sinSesionEs401YOtroRolEs403() {
		tieneCodigo(mvc.get().uri(ENTRADAS).exchange(), HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO");
		tieneCodigo(get(ENTRADAS, admin), HttpStatus.FORBIDDEN, "ACCESO_DENEGADO");
		tieneCodigo(get(ENTRADAS + "/81", admin), HttpStatus.FORBIDDEN, "ACCESO_DENEGADO");
		then(consultaDeEntradas).shouldHaveNoInteractions();
	}

	// RF-3 y RF-10: las compras con sus entradas numeradas y su estado, sin los códigos
	@Test
	void listaLasComprasSinLosCodigos() throws Exception {
		given(consultaDeEntradas.delCliente(7L)).willReturn(new EntradasDelCliente(List.of(compra), List.of(), List.of()));

		MvcTestResult resultado = get(ENTRADAS, ana);

		assertThat(resultado).hasStatusOk();
		assertThat(resultado).bodyJson().extractingPath("$.proximas[0].idReserva").isEqualTo(40);
		assertThat(resultado).bodyJson().extractingPath("$.proximas[0].idEvento").isEqualTo(5);
		assertThat(resultado).bodyJson().extractingPath("$.proximas[0].nombreEvento").isEqualTo("Noche de jazz");
		assertThat(resultado).bodyJson().extractingPath("$.proximas[0].fecha").isEqualTo("2026-10-04");
		assertThat(resultado).bodyJson().extractingPath("$.proximas[0].horaInicio").isEqualTo("22:00:00");
		assertThat(resultado).bodyJson().extractingPath("$.proximas[0].horaFin").isEqualTo("03:00:00");
		assertThat(resultado).bodyJson().extractingPath("$.proximas[0].lugar").isEqualTo("Teatro Municipal");
		assertThat(resultado).bodyJson().extractingPath("$.proximas[0].estadoEvento").isEqualTo("ACTIVO");
		assertThat(resultado).bodyJson().extractingPath("$.proximas[0].tipoEntrada").isEqualTo("General");
		assertThat(resultado).bodyJson().extractingPath("$.proximas[0].cantidad").isEqualTo(2);
		assertThat(resultado).bodyJson().extractingPath("$.proximas[0].entradas[0].idEntrada").isEqualTo(81);
		assertThat(resultado).bodyJson().extractingPath("$.proximas[0].entradas[0].numero").isEqualTo(1);
		assertThat(resultado).bodyJson().extractingPath("$.proximas[0].entradas[0].estado").isEqualTo("ACTIVA");
		assertThat(resultado).bodyJson().extractingPath("$.proximas[0].entradas[1].numero").isEqualTo(2);
		assertThat(resultado).bodyJson().extractingPath("$.proximas[0].entradas[1].estado").isEqualTo("UTILIZADA");
		assertThat(resultado).bodyJson().extractingPath("$.pasadas").asArray().isEmpty();
		assertThat(resultado).bodyJson().extractingPath("$.canceladas").asArray().isEmpty();
		assertThat(resultado.getResponse().getContentAsString()).doesNotContainIgnoringCase("codigo");
	}

	// FA-01: sin compras responde 200 con las dos listas vacías
	@Test
	void sinComprasRespondeListasVacias() {
		given(consultaDeEntradas.delCliente(7L)).willReturn(new EntradasDelCliente(List.of(), List.of(), List.of()));

		MvcTestResult resultado = get(ENTRADAS, ana);

		assertThat(resultado).hasStatusOk();
		assertThat(resultado).bodyJson().extractingPath("$.proximas").asArray().isEmpty();
		assertThat(resultado).bodyJson().extractingPath("$.pasadas").asArray().isEmpty();
		assertThat(resultado).bodyJson().extractingPath("$.canceladas").asArray().isEmpty();
	}

	// Los cancelados, en su propia lista
	@Test
	void listaLosCanceladosAparte() {
		compra.evento().cancelar();
		given(consultaDeEntradas.delCliente(7L)).willReturn(new EntradasDelCliente(List.of(), List.of(), List.of(compra)));

		MvcTestResult resultado = get(ENTRADAS, ana);

		assertThat(resultado).bodyJson().extractingPath("$.canceladas[0].idReserva").isEqualTo(40);
		assertThat(resultado).bodyJson().extractingPath("$.canceladas[0].estadoEvento").isEqualTo("CANCELADO");
		assertThat(resultado).bodyJson().extractingPath("$.proximas").asArray().isEmpty();
	}

	// RF-8 y RF-9: el detalle de una entrada activa trae su código
	@Test
	void elDetalleDeUnaActivaTraeSuCodigo() {
		given(consultaDeEntradas.detalle(7L, 81L)).willReturn(new DetalleEntrada(compra.entradas().get(0), 1, 2));

		MvcTestResult resultado = get(ENTRADAS + "/81", ana);

		assertThat(resultado).hasStatusOk();
		assertThat(resultado).bodyJson().extractingPath("$.idEntrada").isEqualTo(81);
		assertThat(resultado).bodyJson().extractingPath("$.numero").isEqualTo(1);
		assertThat(resultado).bodyJson().extractingPath("$.cantidad").isEqualTo(2);
		assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("ACTIVA");
		assertThat(resultado).bodyJson().extractingPath("$.fechaEmision").isEqualTo("2026-09-22T20:00:00");
		assertThat(resultado).bodyJson().extractingPath("$.codigoQr").isEqualTo("codigo-81");
		assertThat(resultado).bodyJson().extractingPath("$.idEvento").isEqualTo(5);
		assertThat(resultado).bodyJson().extractingPath("$.nombreEvento").isEqualTo("Noche de jazz");
		assertThat(resultado).bodyJson().extractingPath("$.fecha").isEqualTo("2026-10-04");
		assertThat(resultado).bodyJson().extractingPath("$.horaInicio").isEqualTo("22:00:00");
		assertThat(resultado).bodyJson().extractingPath("$.horaFin").isEqualTo("03:00:00");
		assertThat(resultado).bodyJson().extractingPath("$.lugar").isEqualTo("Teatro Municipal");
		assertThat(resultado).bodyJson().extractingPath("$.estadoEvento").isEqualTo("ACTIVO");
		assertThat(resultado).bodyJson().extractingPath("$.tipoEntrada").isEqualTo("General");
	}

	// RF-9: una entrada utilizada no entrega su código
	@Test
	void elDetalleDeUnaUtilizadaNoTraeElCodigo() throws Exception {
		given(consultaDeEntradas.detalle(7L, 82L)).willReturn(new DetalleEntrada(compra.entradas().get(1), 2, 2));

		MvcTestResult resultado = get(ENTRADAS + "/82", ana);

		assertThat(resultado).hasStatusOk();
		assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("UTILIZADA");
		assertThat(resultado).bodyJson().extractingPath("$.codigoQr").isNull();
		assertThat(resultado.getResponse().getContentAsString()).doesNotContain("codigo-82");
	}

	// RF-9: la de un evento cancelado tampoco
	@Test
	void elDetalleDeUnaDeEventoCanceladoNoTraeElCodigo() {
		compra.evento().cancelar();
		given(consultaDeEntradas.detalle(7L, 81L)).willReturn(new DetalleEntrada(compra.entradas().get(0), 1, 2));

		MvcTestResult resultado = get(ENTRADAS + "/81", ana);

		assertThat(resultado).bodyJson().extractingPath("$.estadoEvento").isEqualTo("CANCELADO");
		assertThat(resultado).bodyJson().extractingPath("$.codigoQr").isNull();
	}

	// RF-2: la de otro Cliente responde como una inexistente
	@Test
	void unaEntradaAjenaEs404() {
		given(consultaDeEntradas.detalle(7L, 99L)).willThrow(new EntradaNoEncontradaException());

		tieneCodigo(get(ENTRADAS + "/99", ana), HttpStatus.NOT_FOUND, "ENTRADA_NO_ENCONTRADA");
	}
}
