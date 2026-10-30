package com.sarao.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import com.sarao.seguridad.dto.CambioContrasenaRequest;
import com.sarao.seguridad.exception.ContrasenaActualIncorrectaException;
import com.sarao.seguridad.repository.UsuarioRepository;
import com.sarao.seguridad.service.AdministradorInicial;
import com.sarao.seguridad.service.AutenticacionService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DatosPersonalesIntegracionTest {

	private static final String REGISTRO_DE_ANA = """
			{"nombre":"Ana","apellido":"Rojas","correo":"ana@mail.com","contrasena":"Secreta12","telefono":"70000000","fechaNacimiento":"2000-05-20"}
			""";
	private static final String DATOS = "/api/cuenta/datos-personales";
	private static final String CONTRASENA = "/api/cuenta/contrasena";

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private AdministradorInicial administradorInicial;

	@Autowired
	private AutenticacionService autenticacionService;

	@BeforeEach
	void registrarAna() {
		assertThat(mvc.post().uri("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
				.content(REGISTRO_DE_ANA).exchange()).hasStatus(HttpStatus.CREATED);
	}

	@AfterEach
	void limpiar() {
		usuarioRepository.findByCorreo("ana@mail.com").ifPresent(usuarioRepository::delete);
	}

	private MvcTestResult iniciarSesionDesde(String ip, String correo, String contrasena) {
		return mvc.post().uri("/api/auth/inicio-sesion").contentType(MediaType.APPLICATION_JSON)
				.with(peticion -> {
					peticion.setRemoteAddr(ip);
					return peticion;
				})
				.content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, contrasena)).exchange();
	}

	private String token(String correo, String contrasena) throws Exception {
		return JsonPath.read(iniciarSesionDesde("127.0.0.1", correo, contrasena).getResponse().getContentAsString(),
				"$.token");
	}

	private MvcTestResult consultar(String token) {
		return mvc.get().uri(DATOS).with(SesionDelGateway.sesion(mvc, token)).exchange();
	}

	private MvcTestResult cambiarContrasena(String token, String ip, String actual, String nueva) {
		return mvc.put().uri(CONTRASENA).with(SesionDelGateway.sesion(mvc, token))
				.contentType(MediaType.APPLICATION_JSON)
				.with(peticion -> {
					peticion.setRemoteAddr(ip);
					return peticion;
				})
				.content("{\"contrasenaActual\":\"%s\",\"contrasenaNueva\":\"%s\"}".formatted(actual, nueva))
				.exchange();
	}

	@Test
	void sinSesionNoSeConsultanLosDatos() {
		assertThat(mvc.get().uri(DATOS).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void actualizaNombreApellidoYTelefonoSinTocarCorreoFechaRolEstadoNiContrasena() throws Exception {
		String token = token("ana@mail.com", "Secreta12");

		MvcTestResult resultado = mvc.put().uri(DATOS).with(SesionDelGateway.sesion(mvc, token))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nombre":"Ana María","apellido":"Rojas Paz","telefono":"","correo":"otra@mail.com","fechaNacimiento":"1990-01-01","rol":"ADMINISTRADOR","estado":"DESACTIVADA","contrasena":"otra-clave-1"}
						""")
				.exchange();

		assertThat(resultado).hasStatus(HttpStatus.OK);
		assertThat(resultado).bodyJson().extractingPath("$.nombre").isEqualTo("Ana María");
		assertThat(resultado).bodyJson().extractingPath("$.apellido").isEqualTo("Rojas Paz");
		assertThat(resultado).bodyJson().extractingPath("$.telefono").isNull();
		assertThat(resultado).bodyJson().extractingPath("$.correo").isEqualTo("ana@mail.com");
		assertThat(resultado).bodyJson().extractingPath("$.fechaNacimiento").isEqualTo("2000-05-20");
		assertThat(resultado).bodyJson().extractingPath("$.rol").isEqualTo("CLIENTE");
		assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("ACTIVA");
		assertThat(consultar(token)).bodyJson().extractingPath("$.nombre").isEqualTo("Ana María");
		assertThat(iniciarSesionDesde("127.0.0.1", "ana@mail.com", "Secreta12")).hasStatus(HttpStatus.OK);
	}

	@Test
	void datosInvalidosNoModificanLaCuenta() throws Exception {
		String token = token("ana@mail.com", "Secreta12");

		assertThat(mvc.put().uri(DATOS).with(SesionDelGateway.sesion(mvc, token))
				.contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"\",\"apellido\":\"Rojas\"}")
				.exchange()).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(consultar(token)).bodyJson().extractingPath("$.nombre").isEqualTo("Ana");
	}

	@Test
	void cambioDeContrasenaEntregaUnTokenNuevoYElAnteriorDejaDeValer() throws Exception {
		String tokenAnterior = token("ana@mail.com", "Secreta12");

		MvcTestResult cambio = cambiarContrasena(tokenAnterior, "127.0.0.1", "Secreta12", "Nueva-clave-1");

		assertThat(cambio).hasStatus(HttpStatus.OK);
		String tokenNuevo = JsonPath.read(cambio.getResponse().getContentAsString(), "$.token");
		assertThat(consultar(tokenAnterior)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(consultar(tokenNuevo)).hasStatus(HttpStatus.OK);
		assertThat(iniciarSesionDesde("127.0.0.1", "ana@mail.com", "Nueva-clave-1")).hasStatus(HttpStatus.OK);
		assertThat(iniciarSesionDesde("127.0.0.1", "ana@mail.com", "Secreta12")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void contrasenaActualIncorrectaNoCambiaLaContrasena() throws Exception {
		String token = token("ana@mail.com", "Secreta12");

		MvcTestResult cambio = cambiarContrasena(token, "127.0.0.1", "otra-clave", "Nueva-clave-1");

		assertThat(cambio).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(cambio).bodyJson().extractingPath("$.codigo").isEqualTo("CONTRASENA_ACTUAL_INCORRECTA");
		assertThat(consultar(token)).hasStatus(HttpStatus.OK);
		assertThat(iniciarSesionDesde("127.0.0.1", "ana@mail.com", "Secreta12")).hasStatus(HttpStatus.OK);
	}

	@Test
	void cincoContrasenasActualesIncorrectasBloqueanElCambioYElInicioDeSesionDesdeEsaIp() throws Exception {
		// IP propia: los contadores del límite viven en el contexto compartido con los otros tests de integración
		String ip = "10.4.0.1";
		String token = token("ana@mail.com", "Secreta12");
		for (int i = 0; i < 5; i++) {
			assertThat(cambiarContrasena(token, ip, "otra-clave", "Nueva-clave-1")).hasStatus(HttpStatus.BAD_REQUEST);
		}

		MvcTestResult bloqueado = cambiarContrasena(token, ip, "Secreta12", "Nueva-clave-1");

		assertThat(bloqueado).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(bloqueado).bodyJson().extractingPath("$.codigo").isEqualTo("INTENTOS_EXCEDIDOS");
		assertThat(iniciarSesionDesde(ip, "ana@mail.com", "Secreta12")).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(iniciarSesionDesde("127.0.0.1", "ana@mail.com", "Secreta12")).hasStatus(HttpStatus.OK);
	}

	@Test
	void administradorConsultaSusDatosSinFechaDeNacimiento() throws Exception {
		// La H2 es compartida y otro contexto pudo recrearla sin el administrador inicial; repetir el arranque es
		// idempotente
		administradorInicial.run(new DefaultApplicationArguments());

		MvcTestResult resultado = consultar(token("admin@eventos.test", "Clave-admin-1"));

		assertThat(resultado).hasStatus(HttpStatus.OK);
		assertThat(resultado).bodyJson().extractingPath("$.rol").isEqualTo("ADMINISTRADOR");
		assertThat(resultado).bodyJson().extractingPath("$.fechaNacimiento").isNull();
	}

	@Test
	void dosCambiosDeContrasenaSimultaneosSoloAplicanUno() throws Exception {
		Long idAna = usuarioRepository.findByCorreo("ana@mail.com").orElseThrow().getIdUsuario();
		CountDownLatch largada = new CountDownLatch(1);
		ExecutorService hilos = Executors.newFixedThreadPool(2);
		try {
			// Las comparaciones BCrypt tardan: sin bloqueo, las dos peticiones leen el mismo hash anterior
			List<Future<String>> cambios = List.of("Nueva-clave-A", "Nueva-clave-B").stream()
					.map(nueva -> hilos.submit(() -> {
						largada.await();
						try {
							autenticacionService.cambiarContrasena(idAna,
									new CambioContrasenaRequest("Secreta12", nueva), "10.4.0.2");
							return "cambiada";
						} catch (ContrasenaActualIncorrectaException e) {
							return "rechazada";
						}
					}))
					.toList();
			largada.countDown();
			List<String> resultados = new ArrayList<>();
			for (Future<String> cambio : cambios) {
				resultados.add(cambio.get(10, TimeUnit.SECONDS));
			}

			assertThat(resultados).containsExactlyInAnyOrder("cambiada", "rechazada");
		} finally {
			hilos.shutdownNow();
		}
	}
}
