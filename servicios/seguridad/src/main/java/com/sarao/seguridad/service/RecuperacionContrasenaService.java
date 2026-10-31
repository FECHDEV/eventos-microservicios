package com.sarao.seguridad.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sarao.seguridad.dto.RestablecimientoContrasenaRequest;
import com.sarao.seguridad.dto.SolicitudRecuperacionRequest;
import com.sarao.seguridad.entity.RecuperacionContrasena;
import com.sarao.seguridad.entity.Usuario;
import com.sarao.seguridad.exception.TokenRecuperacionInvalidoException;
import com.sarao.seguridad.repository.RecuperacionContrasenaRepository;
import com.sarao.seguridad.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RecuperacionContrasenaService {

	private final UsuarioRepository usuarioRepository;
	private final RecuperacionContrasenaRepository recuperacionRepository;
	private final PasswordEncoder passwordEncoder;
	private final ApplicationEventPublisher eventos;
	private final LimiteSolicitudesRecuperacion limiteSolicitudes;
	private final LimiteIntentos limiteIntentos;
	private final Clock clock;

	// Termina igual exista o no la cuenta, para no revelar qué correos están registrados
	@Transactional
	public void solicitar(SolicitudRecuperacionRequest solicitud, String ip) {
		String correo = Usuario.normalizarCorreo(solicitud.correo());
		limiteSolicitudes.registrarIntento(correo, ip);
		// Con la cuenta bloqueada, una solicitud simultánea espera y ve el token de esta: solo queda uno vigente
		usuarioRepository.findConBloqueoByCorreo(correo).ifPresent(this::iniciarRecuperacion);
	}

	@Transactional
	public void restablecer(RestablecimientoContrasenaRequest solicitud) {
		LocalDateTime ahora = LocalDateTime.now(clock);
		String hashToken = TokenRecuperacion.hash(solicitud.token());
		// Primero la cuenta, como solicitar y cambiarContrasena: el orden inverso produce interbloqueos
		Usuario usuario = recuperacionRepository.findIdUsuarioByHashToken(hashToken)
				.flatMap(usuarioRepository::findConBloqueoByIdUsuario)
				.orElseThrow(TokenRecuperacionInvalidoException::new);
		recuperacionRepository.findByHashToken(hashToken)
				.filter(registro -> registro.estaVigente(ahora))
				.orElseThrow(TokenRecuperacionInvalidoException::new);
		usuario.cambiarContrasena(passwordEncoder.encode(solicitud.contrasena()));
		invalidarPendientes(usuario);
		// Quien restablece tiene el token que llegó al correo: el bloqueo por intentos fallidos ya no protege la cuenta
		limiteIntentos.reiniciarCorreo(usuario.getCorreo());
	}

	private void iniciarRecuperacion(Usuario usuario) {
		invalidarPendientes(usuario);
		String token = TokenRecuperacion.generar();
		recuperacionRepository.save(
				RecuperacionContrasena.crear(usuario, TokenRecuperacion.hash(token), LocalDateTime.now(clock)));
		eventos.publishEvent(new RecuperacionSolicitada(usuario.getCorreo(), usuario.getNombre(), token));
	}

	// Incluye la recuperación que se acaba de usar al restablecer
	private void invalidarPendientes(Usuario usuario) {
		recuperacionRepository.findByUsuarioAndUsadoFalse(usuario).forEach(RecuperacionContrasena::marcarUsada);
	}
}
