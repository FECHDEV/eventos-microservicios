package com.sarao.seguridad.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.sarao.seguridad.entity.EstadoCuenta;
import com.sarao.seguridad.entity.Rol;
import com.sarao.seguridad.entity.Usuario;
import com.sarao.seguridad.dto.AltaPersonalRequest;
import com.sarao.seguridad.exception.ClienteYPersonalSeparadosException;
import com.sarao.seguridad.exception.CuentaExistenteException;
import com.sarao.seguridad.exception.CuentaPropiaNoDesactivableException;
import com.sarao.seguridad.exception.RolDeCuentaDesactivadaException;
import com.sarao.seguridad.exception.RolPropioNoModificableException;
import com.sarao.seguridad.exception.UltimoAdministradorActivoException;
import com.sarao.seguridad.exception.UsuarioNoEncontradoException;
import com.sarao.seguridad.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class GestionUsuariosServiceTest {

	private static final Sort ORDEN = Sort.by(Sort.Direction.DESC, "fechaRegistro", "idUsuario");
	private static final LocalDate NACIMIENTO = LocalDate.of(2000, 5, 20);
	private static final LocalDateTime REGISTRO = LocalDateTime.of(2026, 9, 12, 11, 0);

	@Mock
	private UsuarioRepository usuarioRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	private static final ZoneId ZONA = ZoneId.of("America/La_Paz");
	private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 26, 9, 30);

	private GestionUsuariosService servicio;

	@BeforeEach
	void crearServicio() {
		servicio = new GestionUsuariosService(usuarioRepository, passwordEncoder,
				Clock.fixed(AHORA.atZone(ZONA).toInstant(), ZONA));
	}

	private static Usuario conId(Usuario usuario, long id) {
		ReflectionTestUtils.setField(usuario, "idUsuario", id);
		return usuario;
	}

	private void listadoVacio() {
		given(usuarioRepository.buscar(any(), any(), any(), any(), any())).willReturn(new PageImpl<>(List.of()));
	}

	@Test
	void listarPideLaPaginaIndicadaDelRegistroMasRecienteAlMasAntiguo() {
		Page<Usuario> pagina = new PageImpl<>(List.of(conId(Usuario.registrarCliente("Ana", "Rojas", "ana@mail.com",
				"$2a$10$hash", null, NACIMIENTO, REGISTRO), 7L)));
		given(usuarioRepository.buscar(any(), any(), any(), any(), any())).willReturn(pagina);

		assertThat(servicio.listar(Rol.CLIENTE, null, EstadoCuenta.ACTIVA, null, 2, 30)).isSameAs(pagina);

		then(usuarioRepository).should().buscar(Rol.CLIENTE, null, EstadoCuenta.ACTIVA, null, PageRequest.of(2, 30, ORDEN));
	}

	@Test
	void tamanoMayorA100UsaCienYPaginaOTamanoInvalidosUsanLosPorDefecto() {
		listadoVacio();

		servicio.listar(null, null, null, null, -1, 500);
		servicio.listar(null, null, null, null, 0, 0);

		then(usuarioRepository).should().buscar(null, null, null, null, PageRequest.of(0, 100, ORDEN));
		then(usuarioRepository).should().buscar(null, null, null, null, PageRequest.of(0, 20, ORDEN));
	}

	@Test
	void textoSeBuscaRecortadoEnMinusculasYConLosComodinesEscapados() {
		listadoVacio();

		servicio.listar(null, null, null, "  Ana_50%! ", 0, 20);

		then(usuarioRepository).should().buscar(null, null, null, "%ana!_50!%!!%", PageRequest.of(0, 20, ORDEN));
	}

	@Test
	void textoVacioOEnBlancoNoFiltra() {
		listadoVacio();

		servicio.listar(null, null, null, "", 0, 20);
		servicio.listar(null, null, null, "   ", 0, 20);

		then(usuarioRepository).should(times(2)).buscar(null, null, null, null, PageRequest.of(0, 20, ORDEN));
	}

	private static final long ID_ADMIN = 1L;
	private static final long ID_OTRO_ADMIN = 2L;
	private static final long ID_ANA = 7L;

	private static Usuario administrador(long id) {
		return conId(Usuario.crearAdministrador("Administrador", "Inicial", "admin" + id + "@eventos.com", "$2a$10$hash",
				REGISTRO), id);
	}

	private static Usuario ana() {
		return conId(Usuario.registrarCliente("Ana", "Rojas", "ana@mail.com", "$2a$10$hash", null, NACIMIENTO, REGISTRO),
				ID_ANA);
	}

	private void administradoresActivos(Usuario... administradores) {
		given(usuarioRepository.findConBloqueoByRolAndEstadoOrderByIdUsuario(Rol.ADMINISTRADOR, EstadoCuenta.ACTIVA))
				.willReturn(List.of(administradores));
	}

	private void cuenta(Usuario usuario) {
		given(usuarioRepository.findConBloqueoByIdUsuario(usuario.getIdUsuario())).willReturn(Optional.of(usuario));
	}

	// --- cambiarRol ---

	private static Usuario personal(long id, Rol rol) {
		return conId(Usuario.crearPersonal("Jorge", "Méndez", "jorge" + id + "@mail.com", "$2a$10$hash", null, rol,
				REGISTRO), id);
	}

	@Test
	void asignaOtroRolDelPersonalBloqueandoPrimeroLosAdministradores() {
		Usuario jorge = personal(ID_ANA, Rol.PERSONAL);
		administradoresActivos(administrador(ID_ADMIN));
		cuenta(jorge);

		assertThat(servicio.cambiarRol(ID_ADMIN, ID_ANA, Rol.CONTADOR)).isSameAs(jorge);

		assertThat(jorge.getRol()).isEqualTo(Rol.CONTADOR);
		InOrder orden = inOrder(usuarioRepository);
		orden.verify(usuarioRepository).findConBloqueoByRolAndEstadoOrderByIdUsuario(Rol.ADMINISTRADOR, EstadoCuenta.ACTIVA);
		orden.verify(usuarioRepository).findConBloqueoByIdUsuario(ID_ANA);
	}

	// RF-25
	@Test
	void unClienteNoPasaAlPersonal() {
		Usuario ana = ana();
		administradoresActivos(administrador(ID_ADMIN));
		cuenta(ana);

		assertThatThrownBy(() -> servicio.cambiarRol(ID_ADMIN, ID_ANA, Rol.ORGANIZADOR))
				.isInstanceOf(ClienteYPersonalSeparadosException.class);
		assertThat(ana.getRol()).isEqualTo(Rol.CLIENTE);
	}

	// RF-25
	@Test
	void elPersonalNoPasaACliente() {
		Usuario jorge = personal(ID_ANA, Rol.CONTADOR);
		administradoresActivos(administrador(ID_ADMIN));
		cuenta(jorge);

		assertThatThrownBy(() -> servicio.cambiarRol(ID_ADMIN, ID_ANA, Rol.CLIENTE))
				.isInstanceOf(ClienteYPersonalSeparadosException.class);
		assertThat(jorge.getRol()).isEqualTo(Rol.CONTADOR);
	}

	@Test
	void propioRolSeRechazaAunqueSeaElMismoSinTocarLaBase() {
		assertThatThrownBy(() -> servicio.cambiarRol(ID_ADMIN, ID_ADMIN, Rol.ADMINISTRADOR))
				.isInstanceOf(RolPropioNoModificableException.class);
		then(usuarioRepository).shouldHaveNoInteractions();
	}

	@Test
	void usuarioInexistenteAlCambiarElRol() {
		administradoresActivos(administrador(ID_ADMIN));
		given(usuarioRepository.findConBloqueoByIdUsuario(99L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.cambiarRol(ID_ADMIN, 99L, Rol.PERSONAL))
				.isInstanceOf(UsuarioNoEncontradoException.class);
	}

	@Test
	void quitarElRolAlUltimoAdministradorActivoSeRechaza() {
		// Solo se alcanza con operaciones simultáneas: quien opera ya dejó de ser administrador al llegar el bloqueo
		Usuario ultimo = administrador(ID_OTRO_ADMIN);
		administradoresActivos(ultimo);
		cuenta(ultimo);

		assertThatThrownBy(() -> servicio.cambiarRol(ID_ADMIN, ID_OTRO_ADMIN, Rol.PERSONAL))
				.isInstanceOf(UltimoAdministradorActivoException.class);
		assertThat(ultimo.getRol()).isEqualTo(Rol.ADMINISTRADOR);
	}

	@Test
	void quitarElRolAUnAdministradorConOtroActivoSePermite() {
		Usuario otro = administrador(ID_OTRO_ADMIN);
		administradoresActivos(administrador(ID_ADMIN), otro);
		cuenta(otro);

		assertThat(servicio.cambiarRol(ID_ADMIN, ID_OTRO_ADMIN, Rol.PERSONAL).getRol()).isEqualTo(Rol.PERSONAL);
	}

	@Test
	void filaQueYaNoEsAdministradorActivoNoCuentaComoOtroAdministrador() {
		Usuario yaDegradado = administrador(ID_ADMIN);
		yaDegradado.asignarRol(Rol.PERSONAL);
		Usuario otro = administrador(ID_OTRO_ADMIN);
		administradoresActivos(yaDegradado, otro);
		cuenta(otro);

		assertThatThrownBy(() -> servicio.cambiarRol(ID_ADMIN, ID_OTRO_ADMIN, Rol.PERSONAL))
				.isInstanceOf(UltimoAdministradorActivoException.class);
	}

	@Test
	void rolDeCuentaDesactivadaSeRechazaAunqueSeaElMismo() {
		Usuario ana = ana();
		ana.desactivar();
		administradoresActivos(administrador(ID_ADMIN));
		cuenta(ana);

		assertThatThrownBy(() -> servicio.cambiarRol(ID_ADMIN, ID_ANA, Rol.CLIENTE))
				.isInstanceOf(RolDeCuentaDesactivadaException.class);
	}

	@Test
	void unAdministradorNoPasaACliente() {
		Usuario otro = administrador(ID_OTRO_ADMIN);
		administradoresActivos(administrador(ID_ADMIN), otro);
		cuenta(otro);

		assertThatThrownBy(() -> servicio.cambiarRol(ID_ADMIN, ID_OTRO_ADMIN, Rol.CLIENTE))
				.isInstanceOf(ClienteYPersonalSeparadosException.class);
		assertThat(otro.getRol()).isEqualTo(Rol.ADMINISTRADOR);
	}

	@Test
	void ultimoAdministradorAClienteRespondePrimeroPorElUltimoAdministrador() {
		Usuario ultimo = administrador(ID_OTRO_ADMIN);
		administradoresActivos(ultimo);
		cuenta(ultimo);

		assertThatThrownBy(() -> servicio.cambiarRol(ID_ADMIN, ID_OTRO_ADMIN, Rol.CLIENTE))
				.isInstanceOf(UltimoAdministradorActivoException.class);
	}

	@Test
	void cuentaDesactivadaAClienteRespondePrimeroPorLaCuentaDesactivada() {
		Usuario desactivado = administrador(ID_OTRO_ADMIN);
		desactivado.desactivar();
		administradoresActivos(administrador(ID_ADMIN));
		cuenta(desactivado);

		assertThatThrownBy(() -> servicio.cambiarRol(ID_ADMIN, ID_OTRO_ADMIN, Rol.CLIENTE))
				.isInstanceOf(RolDeCuentaDesactivadaException.class);
	}

	// --- darDeAlta (RF-23, RF-26) ---

	private static AltaPersonalRequest alta(Rol rol) {
		return new AltaPersonalRequest("Mariela", "Suárez", " Mariela.Suarez.90@Mail.com ", "Secreta12", " ", rol);
	}

	@Test
	void daDeAltaAlPersonalActivoConLaContrasenaCifradaYSinFechaDeNacimiento() {
		given(passwordEncoder.encode("Secreta12")).willReturn("$2a$10$cifrada");
		given(usuarioRepository.saveAndFlush(any())).willAnswer(invocacion -> invocacion.getArgument(0));

		Usuario mariela = servicio.darDeAlta(alta(Rol.CONTADOR));

		assertThat(mariela.getCorreo()).isEqualTo("mariela.suarez.90@mail.com");
		assertThat(mariela.getRol()).isEqualTo(Rol.CONTADOR);
		assertThat(mariela.estaActiva()).isTrue();
		assertThat(mariela.getContrasena()).isEqualTo("$2a$10$cifrada");
		assertThat(mariela.getTelefono()).isNull();
		assertThat(mariela.getFechaNacimiento()).isNull();
		assertThat(mariela.getFechaRegistro()).isEqualTo(AHORA);
	}

	@Test
	void correoQueYaTieneCuentaSeRechazaSinCifrarNiGuardar() {
		given(usuarioRepository.existsByCorreo("mariela.suarez.90@mail.com")).willReturn(true);

		assertThatThrownBy(() -> servicio.darDeAlta(alta(Rol.ORGANIZADOR))).isInstanceOf(CuentaExistenteException.class);

		then(passwordEncoder).shouldHaveNoInteractions();
		then(usuarioRepository).should(never()).saveAndFlush(any());
	}

	@Test
	void dosAltasSimultaneasConElMismoCorreoTerminanEnCuentaExistente() {
		given(passwordEncoder.encode(any())).willReturn("$2a$10$cifrada");
		given(usuarioRepository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("duplicado",
				new org.hibernate.exception.ConstraintViolationException("duplicado",
						new java.sql.SQLException("Duplicate entry", "23000", 1062),
						org.hibernate.exception.ConstraintViolationException.ConstraintKind.UNIQUE, "uk_usuario_correo")));

		assertThatThrownBy(() -> servicio.darDeAlta(alta(Rol.PERSONAL))).isInstanceOf(CuentaExistenteException.class);
	}

	@Test
	void unClienteNoSeDaDeAlta() {
		given(passwordEncoder.encode(any())).willReturn("$2a$10$cifrada");

		assertThatThrownBy(() -> servicio.darDeAlta(alta(Rol.CLIENTE))).isInstanceOf(IllegalArgumentException.class);
		then(usuarioRepository).should(never()).saveAndFlush(any());
	}

	// RF-27
	@Test
	void listarPasaElFiltroDePersonal() {
		listadoVacio();

		servicio.listar(null, true, null, null, 0, 20);

		then(usuarioRepository).should().buscar(null, true, null, null, PageRequest.of(0, 20, ORDEN));
	}

	// --- cambiarEstado ---

	@Test
	void desactivaLaCuentaDeOtroUsuarioSinCambiarSuRol() {
		Usuario ana = ana();
		administradoresActivos(administrador(ID_ADMIN));
		cuenta(ana);

		assertThat(servicio.cambiarEstado(ID_ADMIN, ID_ANA, EstadoCuenta.DESACTIVADA)).isSameAs(ana);

		assertThat(ana.getEstado()).isEqualTo(EstadoCuenta.DESACTIVADA);
		assertThat(ana.getRol()).isEqualTo(Rol.CLIENTE);
		InOrder orden = inOrder(usuarioRepository);
		orden.verify(usuarioRepository).findConBloqueoByRolAndEstadoOrderByIdUsuario(Rol.ADMINISTRADOR, EstadoCuenta.ACTIVA);
		orden.verify(usuarioRepository).findConBloqueoByIdUsuario(ID_ANA);
	}

	@Test
	void reactivaUnaCuentaDesactivadaSinOtrasReglas() {
		Usuario ana = ana();
		ana.desactivar();
		cuenta(ana);

		assertThat(servicio.cambiarEstado(ID_ADMIN, ID_ANA, EstadoCuenta.ACTIVA).getEstado())
				.isEqualTo(EstadoCuenta.ACTIVA);
	}

	@Test
	void desactivarLaPropiaCuentaSeRechazaSinTocarLaBase() {
		assertThatThrownBy(() -> servicio.cambiarEstado(ID_ADMIN, ID_ADMIN, EstadoCuenta.DESACTIVADA))
				.isInstanceOf(CuentaPropiaNoDesactivableException.class);
		then(usuarioRepository).shouldHaveNoInteractions();
	}

	@Test
	void activarLaPropiaCuentaNoTieneReglaExtra() {
		Usuario admin = administrador(ID_ADMIN);
		cuenta(admin);

		assertThat(servicio.cambiarEstado(ID_ADMIN, ID_ADMIN, EstadoCuenta.ACTIVA).getEstado())
				.isEqualTo(EstadoCuenta.ACTIVA);
	}

	@Test
	void reactivarNoCuentaNiBloqueaALosAdministradores() {
		// Reactivar nunca puede dejar al sistema sin administradores activos, así que no hay nada que contar: bloquearlos
		// haría esperar a las demás operaciones de CU-09 sin motivo
		Usuario ana = ana();
		ana.desactivar();
		cuenta(ana);

		servicio.cambiarEstado(ID_ADMIN, ID_ANA, EstadoCuenta.ACTIVA);

		then(usuarioRepository).should(never()).findConBloqueoByRolAndEstadoOrderByIdUsuario(any(), any());
	}

	@Test
	void desactivarAlUltimoAdministradorActivoSeRechaza() {
		Usuario ultimo = administrador(ID_OTRO_ADMIN);
		administradoresActivos(ultimo);
		cuenta(ultimo);

		assertThatThrownBy(() -> servicio.cambiarEstado(ID_ADMIN, ID_OTRO_ADMIN, EstadoCuenta.DESACTIVADA))
				.isInstanceOf(UltimoAdministradorActivoException.class);
		assertThat(ultimo.estaActiva()).isTrue();
	}

	@Test
	void desactivarAUnAdministradorConOtroActivoSePermite() {
		Usuario otro = administrador(ID_OTRO_ADMIN);
		administradoresActivos(administrador(ID_ADMIN), otro);
		cuenta(otro);

		assertThat(servicio.cambiarEstado(ID_ADMIN, ID_OTRO_ADMIN, EstadoCuenta.DESACTIVADA).estaActiva()).isFalse();
	}

	@Test
	void desactivarUnAdministradorYaDesactivadoNoLoCuentaComoElUltimo() {
		Usuario desactivado = administrador(ID_OTRO_ADMIN);
		desactivado.desactivar();
		administradoresActivos(administrador(ID_ADMIN));
		cuenta(desactivado);

		assertThat(servicio.cambiarEstado(ID_ADMIN, ID_OTRO_ADMIN, EstadoCuenta.DESACTIVADA).estaActiva()).isFalse();
	}

	@Test
	void usuarioInexistenteAlCambiarElEstado() {
		administradoresActivos(administrador(ID_ADMIN));
		given(usuarioRepository.findConBloqueoByIdUsuario(99L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.cambiarEstado(ID_ADMIN, 99L, EstadoCuenta.DESACTIVADA))
				.isInstanceOf(UsuarioNoEncontradoException.class);
	}
}
