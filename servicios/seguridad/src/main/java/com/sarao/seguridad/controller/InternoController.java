package com.sarao.seguridad.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sarao.seguridad.dto.FechaNacimientoResponse;
import com.sarao.seguridad.dto.IdentidadResponse;
import com.sarao.seguridad.dto.SesionInternaRequest;
import com.sarao.seguridad.entity.Usuario;
import com.sarao.seguridad.service.AutenticacionService;
import lombok.RequiredArgsConstructor;

// Solo para otros servicios: SecurityConfig exige la clave de servicio y el gateway no publica /interno
@RestController
@RequestMapping("/interno")
@RequiredArgsConstructor
public class InternoController {

	private final AutenticacionService autenticacionService;

	// El token va en el cuerpo para que no quede en los registros de acceso
	@PostMapping("/sesiones")
	public ResponseEntity<IdentidadResponse> identificar(@RequestBody SesionInternaRequest solicitud) {
		return ResponseEntity.of(autenticacionService.usuarioDelToken(solicitud.token()).map(IdentidadResponse::from));
	}

	// CU-06: solo el Cliente la tiene. Sin cuenta activa, o sin fecha, no hay nada que responder
	@GetMapping("/usuarios/{idUsuario}/fecha-nacimiento")
	public ResponseEntity<FechaNacimientoResponse> fechaNacimiento(@PathVariable Long idUsuario) {
		return ResponseEntity.of(autenticacionService.usuarioActivo(idUsuario).map(Usuario::getFechaNacimiento)
				.map(FechaNacimientoResponse::new));
	}
}
