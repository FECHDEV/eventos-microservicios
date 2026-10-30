package com.sarao.seguridad.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// El gateway ya resolvió el token. Sus encabezados solo valen con la clave de servicio: alguien que llegue directo a
// este servicio no puede hacerse pasar por un usuario. No es un @Component: lo crea SecurityConfig
public class IdentidadDelGatewayFiltro extends OncePerRequestFilter {

	private final String claveServicio;

	public IdentidadDelGatewayFiltro(String claveServicio) {
		if (claveServicio == null || claveServicio.isBlank()) {
			throw new IllegalStateException("Falta configurar EVENTOS_CLAVE_SERVICIO.");
		}
		this.claveServicio = claveServicio;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
			throws ServletException, IOException {
		if (claveValida(peticion.getHeader("X-Clave-Servicio"))) {
			String idUsuario = peticion.getHeader("X-Usuario-Id");
			if (idUsuario == null) {
				autenticar("servicio", "ROLE_SERVICIO");
			} else {
				autenticar(Long.valueOf(idUsuario), "ROLE_" + peticion.getHeader("X-Usuario-Rol"));
			}
		}
		cadena.doFilter(peticion, respuesta);
	}

	private boolean claveValida(String clave) {
		return clave != null && MessageDigest.isEqual(claveServicio.getBytes(StandardCharsets.UTF_8),
				clave.getBytes(StandardCharsets.UTF_8));
	}

	private static void autenticar(Object principal, String autoridad) {
		SecurityContext contexto = SecurityContextHolder.createEmptyContext();
		contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null,
				List.of(new SimpleGrantedAuthority(autoridad))));
		SecurityContextHolder.setContext(contexto);
	}
}
