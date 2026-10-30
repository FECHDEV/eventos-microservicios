package com.sarao.seguridad.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sarao.seguridad.dto.RegistroClienteRequest;
import com.sarao.seguridad.dto.UsuarioResponse;
import com.sarao.seguridad.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final UsuarioService usuarioService;

	@PostMapping("/registro")
	public ResponseEntity<UsuarioResponse> registrar(@Valid @RequestBody RegistroClienteRequest solicitud) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(UsuarioResponse.from(usuarioService.registrarCliente(solicitud)));
	}
}
