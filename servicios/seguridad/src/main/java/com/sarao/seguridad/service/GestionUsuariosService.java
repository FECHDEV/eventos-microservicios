package com.sarao.seguridad.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.sarao.seguridad.dto.AltaPersonalRequest;
import com.sarao.seguridad.entity.EstadoCuenta;
import com.sarao.seguridad.entity.Rol;
import com.sarao.seguridad.entity.Usuario;
import com.sarao.seguridad.exception.ClienteYPersonalSeparadosException;
import com.sarao.seguridad.exception.CuentaExistenteException;
import com.sarao.seguridad.exception.CuentaPropiaNoDesactivableException;
import com.sarao.seguridad.exception.RolDeCuentaDesactivadaException;
import com.sarao.seguridad.exception.RolPropioNoModificableException;
import com.sarao.seguridad.exception.UltimoAdministradorActivoException;
import com.sarao.seguridad.exception.UsuarioNoEncontradoException;
import com.sarao.seguridad.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;

// CU-09: solo el Administrador llega acá (SecurityConfig). Reglas en decisiones.md → Gestión de usuarios (CU-09)
@Service
@RequiredArgsConstructor
public class GestionUsuariosService {

	private static final int TAMANO_POR_DEFECTO = 20;
	private static final int TAMANO_MAXIMO = 100;
	private static final Sort ORDEN = Sort.by(Sort.Direction.DESC, "fechaRegistro", "idUsuario");

	private final UsuarioRepository usuarioRepository;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;

	@Transactional(readOnly = true)
	// RF-27: personal en true deja solo al personal; en false, solo a los clientes; null no filtra
	public Page<Usuario> listar(Rol rol, Boolean personal, EstadoCuenta estado, String texto, int pagina, int tamano) {
		// Igual que el Pageable de Spring Data: una página negativa es la primera y un tamaño inválido, el por defecto
		int tamanoValido = tamano < 1 ? TAMANO_POR_DEFECTO : Math.min(tamano, TAMANO_MAXIMO);
		// El offset tiene que entrar en un int: una página más alta también queda vacía, con los totales correctos
		int paginaValida = Math.min(Math.max(pagina, 0), Integer.MAX_VALUE / tamanoValido);
		PageRequest paginacion = PageRequest.of(paginaValida, tamanoValido, ORDEN);
		return usuarioRepository.buscar(rol, personal, estado, patronDeBusqueda(texto), paginacion);
	}

	// RF-23 y RF-26. Sin @Transactional, como el registro de CU-01: el hash BCrypt no retiene una conexión, y la
	// restricción UNIQUE resuelve dos altas simultáneas con el mismo correo
	public Usuario darDeAlta(AltaPersonalRequest solicitud) {
		if (usuarioRepository.existsByCorreo(solicitud.correo())) {
			throw new CuentaExistenteException();
		}
		Usuario usuario = Usuario.crearPersonal(solicitud.nombre(), solicitud.apellido(), solicitud.correo(),
				passwordEncoder.encode(solicitud.contrasena()), solicitud.telefono(), solicitud.rol(),
				LocalDateTime.now(clock));
		try {
			return usuarioRepository.saveAndFlush(usuario);
		} catch (DataIntegrityViolationException e) {
			if (UsuarioRepository.esCorreoDuplicado(e)) {
				throw new CuentaExistenteException();
			}
			throw e;
		}
	}

	// READ_COMMITTED: los bloqueos quedan en las filas, sin los huecos del índice que frenan registros y causan interbloqueos
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public Usuario cambiarRol(Long idAdministrador, Long idUsuario, Rol rolNuevo) {
		if (idAdministrador.equals(idUsuario)) {
			throw new RolPropioNoModificableException();
		}
		List<Usuario> administradores = bloquearAdministradoresActivos();
		Usuario usuario = bloquearCuenta(idUsuario);
		if (esAdministradorActivo(usuario) && rolNuevo != Rol.ADMINISTRADOR && !hayOtro(administradores, idUsuario)) {
			throw new UltimoAdministradorActivoException();
		}
		if (!usuario.estaActiva()) {
			throw new RolDeCuentaDesactivadaException();
		}
		if (usuario.getRol().esDelPersonal() != rolNuevo.esDelPersonal()) {
			throw new ClienteYPersonalSeparadosException();
		}
		usuario.asignarRol(rolNuevo);
		return usuario;
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public Usuario cambiarEstado(Long idAdministrador, Long idUsuario, EstadoCuenta estadoNuevo) {
		if (estadoNuevo == EstadoCuenta.DESACTIVADA && idAdministrador.equals(idUsuario)) {
			throw new CuentaPropiaNoDesactivableException();
		}
		if (estadoNuevo == EstadoCuenta.ACTIVA) {
			// Reactivar nunca reduce la cantidad de administradores activos: no hay nada que contar, y así esta
			// operación no toma el bloqueo que haría esperar a las demás de CU-09
			Usuario reactivada = bloquearCuenta(idUsuario);
			reactivada.activar();
			return reactivada;
		}
		List<Usuario> administradores = bloquearAdministradoresActivos();
		Usuario usuario = bloquearCuenta(idUsuario);
		if (esAdministradorActivo(usuario) && !hayOtro(administradores, idUsuario)) {
			throw new UltimoAdministradorActivoException();
		}
		usuario.desactivar();
		return usuario;
	}

	// Primero los administradores y después la cuenta, siempre en ese orden: dos operaciones simultáneas se esperan
	// en vez de bloquearse mutuamente. Se vuelve a mirar cada fila porque, tras esperar el bloqueo, puede llegar una
	// que otra operación acaba de cambiar
	private List<Usuario> bloquearAdministradoresActivos() {
		return usuarioRepository.findConBloqueoByRolAndEstadoOrderByIdUsuario(Rol.ADMINISTRADOR, EstadoCuenta.ACTIVA)
				.stream().filter(GestionUsuariosService::esAdministradorActivo).toList();
	}

	// Compara por id: una cuenta reactivada mientras tanto no está en la lista y no cuenta como la última
	private static boolean hayOtro(List<Usuario> administradores, Long idUsuario) {
		return administradores.stream().anyMatch(administrador -> !administrador.getIdUsuario().equals(idUsuario));
	}

	private Usuario bloquearCuenta(Long idUsuario) {
		return usuarioRepository.findConBloqueoByIdUsuario(idUsuario).orElseThrow(UsuarioNoEncontradoException::new);
	}

	private static boolean esAdministradorActivo(Usuario usuario) {
		return usuario.getRol() == Rol.ADMINISTRADOR && usuario.estaActiva();
	}

	private static String patronDeBusqueda(String texto) {
		if (texto == null || texto.isBlank()) {
			return null;
		}
		// '!' primero, para no volver a escapar los que agregan las otras dos sustituciones
		String literal = texto.strip().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
		return "%" + literal + "%";
	}
}
