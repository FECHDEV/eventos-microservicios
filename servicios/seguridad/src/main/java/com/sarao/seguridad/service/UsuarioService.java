package com.sarao.seguridad.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sarao.seguridad.dto.DatosPersonalesRequest;
import com.sarao.seguridad.dto.RegistroClienteRequest;
import com.sarao.seguridad.entity.Usuario;
import com.sarao.seguridad.exception.CuentaExistenteException;
import com.sarao.seguridad.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsuarioService {

	private final UsuarioRepository usuarioRepository;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;
	private final LimiteRegistros limiteRegistros;

	// Sin @Transactional: el hash BCrypt no retiene una conexión, y la restricción UNIQUE resuelve la carrera
	public Usuario registrarCliente(RegistroClienteRequest solicitud, String ip) {
		// También cuenta el correo ya registrado: frena probar qué correos tienen cuenta
		limiteRegistros.registrarIntento(ip);
		String correo = Usuario.normalizarCorreo(solicitud.correo());
		if (usuarioRepository.existsByCorreo(correo)) {
			throw new CuentaExistenteException();
		}
		Usuario usuario = Usuario.registrarCliente(solicitud.nombre(), solicitud.apellido(), correo,
				passwordEncoder.encode(solicitud.contrasena()), solicitud.telefono(),
				solicitud.fechaNacimiento(), LocalDateTime.now(clock));
		try {
			return usuarioRepository.saveAndFlush(usuario);
		} catch (DataIntegrityViolationException e) {
			if (UsuarioRepository.esCorreoDuplicado(e)) {
				throw new CuentaExistenteException();
			}
			throw e;
		}
	}

	@Transactional(readOnly = true)
	public Usuario datosPersonales(Long idUsuario) {
		return usuarioRepository.findById(idUsuario).orElseThrow();
	}

	@Transactional
	public Usuario actualizarDatos(Long idUsuario, DatosPersonalesRequest solicitud) {
		Usuario usuario = usuarioRepository.findById(idUsuario).orElseThrow();
		usuario.actualizarDatos(solicitud.nombre(), solicitud.apellido(), solicitud.telefono());
		return usuario;
	}
}
