package com.sarao.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import com.sarao.seguridad.entity.Rol;
import com.sarao.seguridad.exception.UltimoAdministradorActivoException;
import com.sarao.seguridad.repository.UsuarioRepository;
import com.sarao.seguridad.service.AdministradorInicial;
import com.sarao.seguridad.service.GestionUsuariosService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GestionUsuariosIntegracionTest {

	private static final String REGISTRO_DE_ANA = """
			{"nombre":"Ana","apellido":"Rojas","correo":"ana@mail.com","contrasena":"Secreta12","telefono":"70000000","fechaNacimiento":"2000-05-20"}
			""";
	private static final String ADMIN = "admin@eventos.test";
	private static final String CLAVE_ADMIN = "Clave-admin-1";
	// IP propia: los contadores del límite de intentos viven en el contexto compartido con los otros tests de integración
	private static final String IP = "10.9.0.1";

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private AdministradorInicial administradorInicial;

	@Autowired
	private GestionUsuariosService gestionUsuariosService;

	@Autowired
	private JdbcTemplate jdbc;

	private Long idAdmin;
	private Long idAna;

	@BeforeEach
	void prepararAdministradorYAna() {
		// La H2 es compartida y otro contexto pudo recrearla sin el administrador inicial; repetir el arranque es
		// idempotente
		administradorInicial.run(new DefaultApplicationArguments());
		assertThat(mvc.post().uri("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
				.content(REGISTRO_DE_ANA).exchange()).hasStatus(HttpStatus.CREATED);
		idAdmin = usuarioRepository.findByCorreo(ADMIN).orElseThrow().getIdUsuario();
		idAna = usuarioRepository.findByCorreo("ana@mail.com").orElseThrow().getIdUsuario();
	}

	@AfterEach
	void limpiar() {
		// Los otros tests de integración inician sesión con el administrador inicial: vuelve a su rol y estado
		jdbc.update("UPDATE usuario SET rol = 'ADMINISTRADOR', estado = 'ACTIVA' WHERE correo = ?", ADMIN);
		usuarioRepository.findByCorreo("ana@mail.com").ifPresent(usuarioRepository::delete);
	}

	private MvcTestResult iniciarSesion(String correo, String contrasena) {
		return mvc.post().uri("/api/auth/inicio-sesion").contentType(MediaType.APPLICATION_JSON)
				.with(peticion -> {
					peticion.setRemoteAddr(IP);
					return peticion;
				})
				.content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, contrasena)).exchange();
	}

	private String token(String correo, String contrasena) throws Exception {
		MvcTestResult sesion = iniciarSesion(correo, contrasena);
		assertThat(sesion).hasStatus(HttpStatus.OK);
		return JsonPath.read(sesion.getResponse().getContentAsString(), "$.token");
	}

	private MvcTestResult listar(String token, String consulta) {
		return mvc.get().uri("/api/usuarios" + consulta).with(SesionDelGateway.sesion(mvc, token)).exchange();
	}

	private MvcTestResult cambiarRol(String token, Long idUsuario, String rol) {
		return mvc.put().uri("/api/usuarios/{idUsuario}/rol", idUsuario)
				.with(SesionDelGateway.sesion(mvc, token)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"rol\":\"%s\"}".formatted(rol)).exchange();
	}

	private MvcTestResult cambiarEstado(String token, Long idUsuario, String estado) {
		return mvc.put().uri("/api/usuarios/{idUsuario}/estado", idUsuario)
				.with(SesionDelGateway.sesion(mvc, token)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"estado\":\"%s\"}".formatted(estado)).exchange();
	}

	private MvcTestResult datosPersonales(String token) {
		return mvc.get().uri("/api/cuenta/datos-personales").with(SesionDelGateway.sesion(mvc, token))
				.exchange();
	}

	private Integer administradoresActivos() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE rol = 'ADMINISTRADOR' AND estado = 'ACTIVA'",
				Integer.class);
	}

	@Test
	void sinSesionDevuelve401YUnClienteRecibe403() throws Exception {
		assertThat(mvc.get().uri("/api/usuarios").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);

		MvcTestResult cliente = listar(token("ana@mail.com", "Secreta12"), "");

		assertThat(cliente).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(cliente).bodyJson().extractingPath("$.codigo").isEqualTo("ACCESO_DENEGADO");
	}

	@Test
	void administradorListaFiltraYBuscaSinDatosSensibles() throws Exception {
		String tokenAdmin = token(ADMIN, CLAVE_ADMIN);

		MvcTestResult clientes = listar(tokenAdmin, "?rol=CLIENTE&texto=ANA@mail");

		assertThat(clientes).hasStatus(HttpStatus.OK);
		assertThat(clientes).bodyJson().extractingPath("$.contenido[*].correo").asArray().containsExactly("ana@mail.com");
		assertThat(clientes).bodyJson().doesNotHavePath("$.contenido[0].telefono");
		assertThat(clientes).bodyJson().doesNotHavePath("$.contenido[0].fechaNacimiento");
		assertThat(clientes.getResponse().getContentAsString()).doesNotContain("70000000");
		assertThat(listar(tokenAdmin, "?rol=ADMINISTRADOR&texto=ana@mail")).bodyJson()
				.extractingPath("$.totalElementos").isEqualTo(0);
		assertThat(listar(tokenAdmin, "?tamano=500")).bodyJson().extractingPath("$.tamano").isEqualTo(100);
		MvcTestResult invalido = listar(tokenAdmin, "?rol=SUPERADMIN");
		assertThat(invalido).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(invalido).bodyJson().extractingPath("$.codigo").isEqualTo("DATOS_INVALIDOS");
	}

	@Test
	void cambioDeRolRigeEnLaSiguientePeticionDelMismoTokenYConservaLaFecha() throws Exception {
		String tokenAdmin = token(ADMIN, CLAVE_ADMIN);
		String tokenAna = token("ana@mail.com", "Secreta12");

		MvcTestResult aPersonal = cambiarRol(tokenAdmin, idAna, "PERSONAL");

		assertThat(aPersonal).hasStatus(HttpStatus.OK);
		assertThat(aPersonal).bodyJson().extractingPath("$.rol").isEqualTo("PERSONAL");
		assertThat(datosPersonales(tokenAna)).bodyJson().extractingPath("$.rol").isEqualTo("PERSONAL");
		assertThat(listar(tokenAna, "")).hasStatus(HttpStatus.FORBIDDEN);

		assertThat(cambiarRol(tokenAdmin, idAna, "ADMINISTRADOR")).hasStatus(HttpStatus.OK);
		assertThat(listar(tokenAna, "")).hasStatus(HttpStatus.OK);

		assertThat(cambiarRol(tokenAdmin, idAna, "CLIENTE")).hasStatus(HttpStatus.OK);
		MvcTestResult datos = datosPersonales(tokenAna);
		assertThat(datos).bodyJson().extractingPath("$.rol").isEqualTo("CLIENTE");
		assertThat(datos).bodyJson().extractingPath("$.fechaNacimiento").isEqualTo("2000-05-20");
		assertThat(listar(tokenAna, "")).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void desactivarRechazaElTokenYElInicioDeSesionYReactivarLoDevuelve() throws Exception {
		String tokenAdmin = token(ADMIN, CLAVE_ADMIN);
		String tokenAna = token("ana@mail.com", "Secreta12");

		MvcTestResult desactivada = cambiarEstado(tokenAdmin, idAna, "DESACTIVADA");

		assertThat(desactivada).hasStatus(HttpStatus.OK);
		assertThat(desactivada).bodyJson().extractingPath("$.estado").isEqualTo("DESACTIVADA");
		assertThat(datosPersonales(tokenAna)).hasStatus(HttpStatus.UNAUTHORIZED);
		MvcTestResult inicio = iniciarSesion("ana@mail.com", "Secreta12");
		assertThat(inicio).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(inicio).bodyJson().extractingPath("$.codigo").isEqualTo("CUENTA_DESACTIVADA");
		MvcTestResult rol = cambiarRol(tokenAdmin, idAna, "PERSONAL");
		assertThat(rol).hasStatus(HttpStatus.CONFLICT);
		assertThat(rol).bodyJson().extractingPath("$.codigo").isEqualTo("ROL_DE_CUENTA_DESACTIVADA");

		assertThat(cambiarEstado(tokenAdmin, idAna, "ACTIVA")).hasStatus(HttpStatus.OK);
		MvcTestResult reactivada = iniciarSesion("ana@mail.com", "Secreta12");
		assertThat(reactivada).hasStatus(HttpStatus.OK);
		assertThat(reactivada).bodyJson().extractingPath("$.usuario.rol").isEqualTo("CLIENTE");
	}

	@Test
	void operacionesSobreLaPropiaCuentaYUsuarioInexistenteSeRechazan() throws Exception {
		String tokenAdmin = token(ADMIN, CLAVE_ADMIN);

		MvcTestResult propioRol = cambiarRol(tokenAdmin, idAdmin, "PERSONAL");
		MvcTestResult propiaCuenta = cambiarEstado(tokenAdmin, idAdmin, "DESACTIVADA");
		MvcTestResult inexistente = cambiarEstado(tokenAdmin, Long.MAX_VALUE, "DESACTIVADA");

		assertThat(propioRol).hasStatus(HttpStatus.CONFLICT);
		assertThat(propioRol).bodyJson().extractingPath("$.codigo").isEqualTo("ROL_PROPIO_NO_MODIFICABLE");
		assertThat(propiaCuenta).hasStatus(HttpStatus.CONFLICT);
		assertThat(propiaCuenta).bodyJson().extractingPath("$.codigo").isEqualTo("CUENTA_PROPIA_NO_DESACTIVABLE");
		assertThat(inexistente).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(inexistente).bodyJson().extractingPath("$.codigo").isEqualTo("USUARIO_NO_ENCONTRADO");
		MvcTestResult datos = datosPersonales(tokenAdmin);
		assertThat(datos).bodyJson().extractingPath("$.rol").isEqualTo("ADMINISTRADOR");
		assertThat(datos).bodyJson().extractingPath("$.estado").isEqualTo("ACTIVA");
	}

	@Test
	void otroAdministradorNoPuedePasarAClienteAlAdministradorInicialPeroSiAPersonal() throws Exception {
		assertThat(cambiarRol(token(ADMIN, CLAVE_ADMIN), idAna, "ADMINISTRADOR")).hasStatus(HttpStatus.OK);
		String tokenAna = token("ana@mail.com", "Secreta12");
		String tokenAdmin = token(ADMIN, CLAVE_ADMIN);

		MvcTestResult aCliente = cambiarRol(tokenAna, idAdmin, "CLIENTE");

		assertThat(aCliente).hasStatus(HttpStatus.CONFLICT);
		assertThat(aCliente).bodyJson().extractingPath("$.codigo").isEqualTo("FECHA_NACIMIENTO_REQUERIDA");
		assertThat(cambiarRol(tokenAna, idAdmin, "PERSONAL")).hasStatus(HttpStatus.OK);
		assertThat(listar(tokenAdmin, "")).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void dosAdministradoresQueSeQuitanElRolAlMismoTiempoNoDejanAlSistemaSinAdministradores() throws Exception {
		gestionUsuariosService.cambiarRol(idAdmin, idAna, Rol.ADMINISTRADOR);
		assertThat(administradoresActivos()).isEqualTo(2);
		record Operacion(Long administrador, Long afectado) {
		}
		CountDownLatch largada = new CountDownLatch(1);
		ExecutorService hilos = Executors.newFixedThreadPool(2);
		try {
			// Sin bloqueo, cada operación cuenta dos administradores activos y las dos se registran
			List<Future<String>> operaciones = Stream.of(new Operacion(idAdmin, idAna), new Operacion(idAna, idAdmin))
					.map(operacion -> hilos.submit(() -> {
						largada.await();
						try {
							gestionUsuariosService.cambiarRol(operacion.administrador(), operacion.afectado(),
									Rol.PERSONAL);
							return "cambiado";
						} catch (UltimoAdministradorActivoException | PessimisticLockingFailureException e) {
							// Agotar la espera del bloqueo deja el mismo resultado que el rechazo: el cambio no se aplica
							return "rechazado";
						}
					}))
					.toList();
			largada.countDown();
			List<String> resultados = new ArrayList<>();
			for (Future<String> operacion : operaciones) {
				resultados.add(operacion.get(10, TimeUnit.SECONDS));
			}

			assertThat(resultados).containsExactlyInAnyOrder("cambiado", "rechazado");
			assertThat(administradoresActivos()).isEqualTo(1);
		} finally {
			hilos.shutdownNow();
		}
	}
}
