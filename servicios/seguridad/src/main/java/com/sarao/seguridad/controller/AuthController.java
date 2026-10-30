package com.sarao.seguridad.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sarao.seguridad.dto.InicioSesionRequest;
import com.sarao.seguridad.dto.InicioSesionResponse;
import com.sarao.seguridad.dto.MensajeResponse;
import com.sarao.seguridad.dto.RegistroClienteRequest;
import com.sarao.seguridad.dto.RestablecimientoContrasenaRequest;
import com.sarao.seguridad.dto.SolicitudRecuperacionRequest;
import com.sarao.seguridad.dto.UsuarioResponse;
import com.sarao.seguridad.service.AutenticacionService;
import com.sarao.seguridad.service.RecuperacionContrasenaService;
import com.sarao.seguridad.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private static final MensajeResponse RECUPERACION_SOLICITADA = new MensajeResponse(
			"Si el correo está registrado, le enviamos las instrucciones para recuperar la contraseña.");
	private static final MensajeResponse CONTRASENA_RESTABLECIDA = new MensajeResponse(
			"La contraseña se actualizó. Ya puede iniciar sesión con la nueva contraseña.");

	private final UsuarioService usuarioService;
	private final AutenticacionService autenticacionService;
	private final RecuperacionContrasenaService recuperacionContrasenaService;

	@PostMapping("/registro")
	public ResponseEntity<UsuarioResponse> registrar(@Valid @RequestBody RegistroClienteRequest solicitud,
			HttpServletRequest peticion) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(UsuarioResponse.from(usuarioService.registrarCliente(solicitud, peticion.getRemoteAddr())));
	}

	// La IP real la pone el gateway en X-Forwarded-For (server.forward-headers-strategy)
	@PostMapping("/inicio-sesion")
	public InicioSesionResponse iniciarSesion(@Valid @RequestBody InicioSesionRequest solicitud,
			HttpServletRequest peticion) {
		return InicioSesionResponse.from(autenticacionService.iniciarSesion(solicitud, peticion.getRemoteAddr()));
	}

	// Misma respuesta exista o no la cuenta
	@PostMapping("/recuperacion")
	public ResponseEntity<MensajeResponse> solicitarRecuperacion(
			@Valid @RequestBody SolicitudRecuperacionRequest solicitud, HttpServletRequest peticion) {
		recuperacionContrasenaService.solicitar(solicitud, peticion.getRemoteAddr());
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(RECUPERACION_SOLICITADA);
	}

	@PostMapping("/recuperacion/restablecimiento")
	public MensajeResponse restablecerContrasena(@Valid @RequestBody RestablecimientoContrasenaRequest solicitud) {
		recuperacionContrasenaService.restablecer(solicitud);
		return CONTRASENA_RESTABLECIDA;
	}
}
