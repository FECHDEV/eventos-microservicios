package com.sarao.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import com.sarao.seguridad.repository.UsuarioRepository;
import com.sarao.seguridad.service.AdministradorInicial;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InicioSesionIntegracionTest {

	private static final String REGISTRO_DE_ANA = """
			{"nombre":"Ana","apellido":"Rojas","correo":"ana@mail.com","contrasena":"Secreta12","fechaNacimiento":"2000-05-20"}
			""";

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private AdministradorInicial administradorInicial;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ApplicationContext contexto;

	@BeforeEach
	void registrarAna() {
		assertThat(mvc.post().uri("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
				.content(REGISTRO_DE_ANA).exchange()).hasStatus(HttpStatus.CREATED);
	}

	@AfterEach
	void limpiar() {
		usuarioRepository.findByCorreo("ana@mail.com").ifPresent(usuarioRepository::delete);
	}

	private MvcTestResult iniciarSesion(String correo, String contrasena) {
		return mvc.post().uri("/api/auth/inicio-sesion").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, contrasena)).exchange();
	}

	private static String token(MvcTestResult sesion) throws Exception {
		return JsonPath.read(sesion.getResponse().getContentAsString(), "$.token");
	}

	private MvcTestResult identificar(String token) {
		return mvc.post().uri("/interno/sesiones").header("X-Clave-Servicio", "clave-de-pruebas")
				.contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"%s\"}".formatted(token)).exchange();
	}

	@Test
	void clienteRegistradoIniciaSesionYUsaElTokenEnLasPeticionesSiguientes() throws Exception {
		MvcTestResult sesion = iniciarSesion(" Ana@Mail.COM ", "Secreta12");

		assertThat(sesion).hasStatus(HttpStatus.OK);
		assertThat(sesion).bodyJson().extractingPath("$.usuario.rol").isEqualTo("CLIENTE");
		MvcTestResult identidad = identificar(token(sesion));
		assertThat(identidad).hasStatus(HttpStatus.OK);
		assertThat(identidad).bodyJson().extractingPath("$.rol").isEqualTo("CLIENTE");
	}

	@Test
	void contrasenaIncorrectaDevuelveCredencialesIncorrectas() {
		MvcTestResult resultado = iniciarSesion("ana@mail.com", "secreta12");

		assertThat(resultado).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("CREDENCIALES_INCORRECTAS");
	}

	@Test
	void administradorInicialIniciaSesionConLasVariablesDelPerfil() {
		// UsuarioRepositoryTest comparte la H2 en memoria y recrea el esquema al arrancar su contexto, lo que
		// puede borrar el administrador creado al arrancar este. Repetir el arranque es idempotente.
		administradorInicial.run(new DefaultApplicationArguments());

		MvcTestResult resultado = iniciarSesion("admin@eventos.test", "Clave-admin-1");

		assertThat(resultado).hasStatus(HttpStatus.OK);
		assertThat(resultado).bodyJson().extractingPath("$.usuario.rol").isEqualTo("ADMINISTRADOR");
		assertThat(resultado).bodyJson().extractingPath("$.usuario.nombre").isEqualTo("Administrador");
	}

	@Test
	void cuentaDesactivadaNoIniciaSesionYSuTokenAnteriorDejaDeValer() throws Exception {
		String tokenAnterior = token(iniciarSesion("ana@mail.com", "Secreta12"));
		// Se desactiva directo en la base, sin pasar por la gestión de usuarios (CU-09), que se prueba aparte
		jdbc.update("UPDATE usuario SET estado = 'DESACTIVADA' WHERE correo = ?", "ana@mail.com");

		MvcTestResult resultado = iniciarSesion("ana@mail.com", "Secreta12");

		assertThat(resultado).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("CUENTA_DESACTIVADA");
		assertThat(identificar(tokenAnterior)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void cambioDeContrasenaInvalidaElTokenAnterior() throws Exception {
		String tokenAnterior = token(iniciarSesion("ana@mail.com", "Secreta12"));
		// CU-03 y CU-04 agregan el cambio de contraseña; mientras tanto se reemplaza el hash como en la prueba manual
		String hashNuevo = contexto.getBean(PasswordEncoder.class).encode("Secreta12");
		jdbc.update("UPDATE usuario SET contrasena = ? WHERE correo = ?", hashNuevo, "ana@mail.com");

		assertThat(identificar(tokenAnterior)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(identificar(token(iniciarSesion("ana@mail.com", "Secreta12")))).hasStatus(HttpStatus.OK);
	}

	@Test
	void cincoIntentosFallidosBloqueanElCorreoSoloDesdeEsaIp() {
		// Correo propio de este test: los contadores del límite viven en el contexto compartido
		String correo = "bloqueo@mail.com";
		for (int i = 0; i < 5; i++) {
			assertThat(iniciarSesion(correo, "otra-clave")).hasStatus(HttpStatus.UNAUTHORIZED);
		}

		MvcTestResult bloqueado = iniciarSesion(correo, "otra-clave");
		MvcTestResult desdeOtraIp = mvc.post().uri("/api/auth/inicio-sesion").contentType(MediaType.APPLICATION_JSON)
				.with(peticion -> {
					peticion.setRemoteAddr("10.0.0.2");
					return peticion;
				})
				.content("{\"correo\":\"%s\",\"contrasena\":\"otra-clave\"}".formatted(correo)).exchange();

		assertThat(bloqueado).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(bloqueado).bodyJson().extractingPath("$.codigo").isEqualTo("INTENTOS_EXCEDIDOS");
		assertThat(desdeOtraIp).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void tokenInvalidoEnElHeaderNoImpideIniciarSesion() {
		MvcTestResult resultado = mvc.post().uri("/api/auth/inicio-sesion").contentType(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, "Bearer token-viejo-o-invalido")
				.content("{\"correo\":\"ana@mail.com\",\"contrasena\":\"Secreta12\"}").exchange();

		assertThat(resultado).hasStatus(HttpStatus.OK);
	}

	@Test
	void rutaInternaSinClaveDeServicioDevuelve401() {
		assertThat(mvc.post().uri("/interno/sesiones").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"x\"}").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post().uri("/interno/sesiones").header("X-Clave-Servicio", "otra-clave")
				.contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"x\"}").exchange())
				.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// CU-06: otro servicio pregunta la fecha de nacimiento, y solo otro servicio
	@Test
	void laFechaDeNacimientoSoloSeLaDaAOtroServicio() {
		Long id = usuarioRepository.findByCorreo("ana@mail.com").orElseThrow().getIdUsuario();
		String ruta = "/interno/usuarios/" + id + "/fecha-nacimiento";

		MvcTestResult fecha = mvc.get().uri(ruta).header("X-Clave-Servicio", "clave-de-pruebas").exchange();
		assertThat(fecha).hasStatus(HttpStatus.OK);
		assertThat(fecha).bodyJson().extractingPath("$.fechaNacimiento")
				.isEqualTo(usuarioRepository.findById(id).orElseThrow().getFechaNacimiento().toString());

		assertThat(mvc.get().uri("/interno/usuarios/999999/fecha-nacimiento").header("X-Clave-Servicio", "clave-de-pruebas")
				.exchange()).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mvc.get().uri(ruta).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.get().uri(ruta).header("X-Clave-Servicio", "clave-de-pruebas").header("X-Usuario-Id", id.toString())
				.header("X-Usuario-Rol", "CLIENTE").exchange()).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void encabezadosDeUsuarioSinClaveDeServicioNoAutentican() {
		assertThat(mvc.get().uri("/api/cuenta").header("X-Usuario-Id", "1").header("X-Usuario-Rol", "ADMINISTRADOR")
				.exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void identidadDelGatewayAtiendeComoUsuarioYRespetaSuRol() {
		assertThat(mvc.get().uri("/api/no-existe").header("X-Clave-Servicio", "clave-de-pruebas")
				.header("X-Usuario-Id", "1").header("X-Usuario-Rol", "CLIENTE").exchange())
				.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mvc.post().uri("/interno/sesiones").header("X-Clave-Servicio", "clave-de-pruebas")
				.header("X-Usuario-Id", "1").header("X-Usuario-Rol", "ADMINISTRADOR")
				.contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"x\"}").exchange())
				.hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void laClaveDeServicioSolaNoEntraARutasDeUsuarios() {
		assertThat(mvc.get().uri("/api/no-existe").header("X-Clave-Servicio", "clave-de-pruebas").exchange())
				.hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void noSeCreaElUsuarioEnMemoriaConContrasenaGenerada() {
		assertThat(contexto.getBeansOfType(UserDetailsService.class)).isEmpty();
	}
}
