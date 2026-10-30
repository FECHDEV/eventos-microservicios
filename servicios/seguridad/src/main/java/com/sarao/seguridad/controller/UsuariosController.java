package com.sarao.seguridad.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sarao.seguridad.dto.CambioEstadoRequest;
import com.sarao.seguridad.dto.CambioRolRequest;
import com.sarao.seguridad.dto.PaginaResponse;
import com.sarao.seguridad.dto.UsuarioResumenResponse;
import com.sarao.seguridad.entity.EstadoCuenta;
import com.sarao.seguridad.entity.Rol;
import com.sarao.seguridad.service.GestionUsuariosService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// Solo el Administrador llega acá (SecurityConfig). Quien opera sale de la sesión; el usuario afectado, de la ruta
@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuariosController {

	private final GestionUsuariosService gestionUsuariosService;

	@GetMapping
	public PaginaResponse<UsuarioResumenResponse> listar(@RequestParam(required = false) Rol rol,
			@RequestParam(required = false) EstadoCuenta estado, @RequestParam(required = false) String texto,
			@RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamano) {
		return PaginaResponse.from(gestionUsuariosService.listar(rol, estado, texto, pagina, tamano),
				UsuarioResumenResponse::from);
	}

	@PutMapping("/{idUsuario}/rol")
	public UsuarioResumenResponse cambiarRol(@AuthenticationPrincipal Long idAdministrador,
			@PathVariable Long idUsuario, @Valid @RequestBody CambioRolRequest solicitud) {
		return UsuarioResumenResponse.from(gestionUsuariosService.cambiarRol(idAdministrador, idUsuario, solicitud.rol()));
	}

	@PutMapping("/{idUsuario}/estado")
	public UsuarioResumenResponse cambiarEstado(@AuthenticationPrincipal Long idAdministrador,
			@PathVariable Long idUsuario, @Valid @RequestBody CambioEstadoRequest solicitud) {
		return UsuarioResumenResponse
				.from(gestionUsuariosService.cambiarEstado(idAdministrador, idUsuario, solicitud.estado()));
	}
}
