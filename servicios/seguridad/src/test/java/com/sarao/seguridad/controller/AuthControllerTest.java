package com.sarao.seguridad.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.sarao.seguridad.config.SecurityConfig;
import com.sarao.seguridad.dto.RegistroClienteRequest;
import com.sarao.seguridad.entity.Usuario;
import com.sarao.seguridad.exception.CuentaExistenteException;
import com.sarao.seguridad.service.UsuarioService;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

	private static final String URL = "/api/auth/registro";

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private UsuarioService usuarioService;

	private static Map<String, String> datosValidos() {
		Map<String, String> datos = new LinkedHashMap<>();
		datos.put("nombre", "Ana");
		datos.put("apellido", "Rojas");
		datos.put("correo", "ana@mail.com");
		datos.put("contrasena", "Secreta12");
		datos.put("fechaNacimiento", "2000-05-20");
		return datos;
	}

	private static String json(Map<String, String> datos) {
		return datos.entrySet().stream()
				.map(campo -> "\"" + campo.getKey() + "\":\"" + campo.getValue() + "\"")
				.collect(Collectors.joining(",", "{", "}"));
	}

	private MvcTestResult registrar(Map<String, String> datos) {
		return mvc.post().uri(URL).contentType(MediaType.APPLICATION_JSON).content(json(datos)).exchange();
	}

	private static Usuario clienteRegistrado() {
		return Usuario.registrarCliente("Ana", "Rojas", "ana@mail.com", "$2a$10$hash", null,
				LocalDate.of(2000, 5, 20), LocalDateTime.of(2026, 9, 12, 11, 0));
	}

	@Test
	void registroValidoSinSesionDevuelve201SinContrasena() {
		given(usuarioService.registrarCliente(any(RegistroClienteRequest.class))).willReturn(clienteRegistrado());

		MvcTestResult resultado = registrar(datosValidos());

		assertThat(resultado).hasStatus(HttpStatus.CREATED);
		assertThat(resultado).bodyJson().extractingPath("$.correo").isEqualTo("ana@mail.com");
		assertThat(resultado).bodyJson().extractingPath("$.rol").isEqualTo("CLIENTE");
		assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("ACTIVA");
		assertThat(resultado).bodyJson().doesNotHavePath("$.contrasena");
	}

	@Test
	void rolYEstadoEnviadosEnLaSolicitudSeIgnoran() {
		given(usuarioService.registrarCliente(any(RegistroClienteRequest.class))).willReturn(clienteRegistrado());
		Map<String, String> datos = datosValidos();
		datos.put("rol", "ADMINISTRADOR");
		datos.put("estado", "DESACTIVADA");

		assertThat(registrar(datos)).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void correoLlegaNormalizadoAlServicio() {
		given(usuarioService.registrarCliente(any(RegistroClienteRequest.class))).willReturn(clienteRegistrado());
		Map<String, String> datos = datosValidos();
		datos.put("correo", "  Ana@Mail.com ");

		assertThat(registrar(datos)).hasStatus(HttpStatus.CREATED);

		ArgumentCaptor<RegistroClienteRequest> solicitud = ArgumentCaptor.forClass(RegistroClienteRequest.class);
		then(usuarioService).should().registrarCliente(solicitud.capture());
		assertThat(solicitud.getValue().correo()).isEqualTo("ana@mail.com");
	}

	@Test
	void datosInvalidosDevuelven400ConTodosLosCamposSinLlamarAlServicio() {
		Map<String, String> datos = datosValidos();
		datos.remove("nombre");
		datos.put("correo", "ana-sin-arroba");
		datos.put("contrasena", "corta");

		MvcTestResult resultado = registrar(datos);

		assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(resultado).headers().hasValue("Content-Type", "application/problem+json");
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("DATOS_INVALIDOS");
		assertThat(resultado).bodyJson().extractingPath("$.errores[*].campo").asArray()
				.containsExactlyInAnyOrder("nombre", "correo", "contrasena");
		then(usuarioService).shouldHaveNoInteractions();
	}

	@Test
	void contrasenaDeMasDe72BytesDevuelve400() {
		Map<String, String> datos = datosValidos();
		datos.put("contrasena", "a".repeat(73));

		assertThat(registrar(datos)).hasStatus(HttpStatus.BAD_REQUEST);
		then(usuarioService).shouldHaveNoInteractions();
	}

	@Test
	void fechaConFormatoInvalidoIndicaElCampoACorregir() {
		Map<String, String> datos = datosValidos();
		datos.put("fechaNacimiento", "20/05/2000");

		MvcTestResult resultado = registrar(datos);

		assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("DATOS_INVALIDOS");
		assertThat(resultado).bodyJson().extractingPath("$.errores[*].campo").asArray()
				.containsExactly("fechaNacimiento");
		then(usuarioService).shouldHaveNoInteractions();
	}

	@Test
	void jsonMalFormadoDevuelveDatosInvalidos() {
		MvcTestResult resultado = mvc.post().uri(URL).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":").exchange();

		assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(resultado).headers().hasValue("Content-Type", "application/problem+json");
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("DATOS_INVALIDOS");
		then(usuarioService).shouldHaveNoInteractions();
	}

	@Test
	void cuentaExistenteDevuelve409() {
		given(usuarioService.registrarCliente(any(RegistroClienteRequest.class)))
				.willThrow(new CuentaExistenteException());

		MvcTestResult resultado = registrar(datosValidos());

		assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
		assertThat(resultado).headers().hasValue("Content-Type", "application/problem+json");
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("CUENTA_EXISTENTE");
	}

	@Test
	void rutaSinSesionDevuelve401EnElFormatoDeLaApi() {
		MvcTestResult resultado = mvc.get().uri("/api/cuenta").exchange();

		assertThat(resultado).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("NO_AUTENTICADO");
	}
}
