package com.sarao.seguridad.service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.sarao.seguridad.config.TokenJwt;
import com.sarao.seguridad.dto.InicioSesionRequest;
import com.sarao.seguridad.entity.Usuario;
import com.sarao.seguridad.exception.CredencialesIncorrectasException;
import com.sarao.seguridad.exception.CuentaDesactivadaException;
import com.sarao.seguridad.repository.UsuarioRepository;

@Service
public class AutenticacionService {

	// BCrypt ignora lo que pasa de 72 bytes: una contraseña más larga podría coincidir sin ser la registrada
	private static final int MAXIMO_BYTES_CONTRASENA = 72;

	private final UsuarioRepository usuarioRepository;
	private final PasswordEncoder passwordEncoder;
	private final TokenJwt tokenJwt;
	private final LimiteIntentos limiteIntentos;
	// Se compara cuando el correo no existe, para que la demora no revele qué correos tienen cuenta
	private final String hashParaCorreoInexistente;

	public AutenticacionService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
			TokenJwt tokenJwt, LimiteIntentos limiteIntentos) {
		this.usuarioRepository = usuarioRepository;
		this.passwordEncoder = passwordEncoder;
		this.tokenJwt = tokenJwt;
		this.limiteIntentos = limiteIntentos;
		this.hashParaCorreoInexistente = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	// Sin transacción: el hash BCrypt no retiene una conexión
	public SesionIniciada iniciarSesion(InicioSesionRequest solicitud, String ip) {
		String correo = Usuario.normalizarCorreo(solicitud.correo());
		// Se cuenta antes de la comparación, que tarda: si no, una ráfaga simultánea superaría el límite
		limiteIntentos.registrarIntento(correo, ip);
		Optional<Usuario> cuenta = usuarioRepository.findByCorreo(correo);
		boolean coincide = passwordEncoder.matches(solicitud.contrasena(),
				cuenta.map(Usuario::getContrasena).orElse(hashParaCorreoInexistente));
		boolean largoAdmitido = tieneLargoAdmitido(solicitud.contrasena());
		if (cuenta.isEmpty() || !coincide || !largoAdmitido) {
			throw new CredencialesIncorrectasException();
		}
		Usuario usuario = cuenta.get();
		if (!usuario.estaActiva()) {
			limiteIntentos.anularIntento(correo, ip);
			throw new CuentaDesactivadaException();
		}
		limiteIntentos.registrarExito(correo, ip);
		return new SesionIniciada(usuario, tokenJwt.emitir(usuario));
	}

	private static boolean tieneLargoAdmitido(String contrasena) {
		return contrasena.getBytes(StandardCharsets.UTF_8).length <= MAXIMO_BYTES_CONTRASENA;
	}

	public Optional<Usuario> usuarioActivo(Long idUsuario) {
		return usuarioRepository.findById(idUsuario).filter(Usuario::estaActiva);
	}

	// El gateway pregunta por cada petición con token: rige el rol y el estado que la cuenta tiene ahora
	public Optional<Usuario> usuarioDelToken(String token) {
		return tokenJwt.leer(token)
				.flatMap(leido -> usuarioActivo(leido.idUsuario()).filter(usuario -> tokenJwt.correspondeA(leido, usuario)));
	}
}
