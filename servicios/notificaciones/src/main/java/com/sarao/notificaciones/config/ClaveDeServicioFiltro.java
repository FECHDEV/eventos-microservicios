package com.sarao.notificaciones.config;

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

// Este servicio solo atiende a otros servicios: no hay usuarios. No es un @Component: lo crea SecurityConfig
public class ClaveDeServicioFiltro extends OncePerRequestFilter {

	private final String claveServicio;

	public ClaveDeServicioFiltro(String claveServicio) {
		if (claveServicio == null || claveServicio.isBlank()) {
			throw new IllegalStateException("Falta configurar EVENTOS_CLAVE_SERVICIO.");
		}
		this.claveServicio = claveServicio;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
			throws ServletException, IOException {
		if (claveValida(peticion.getHeader("X-Clave-Servicio"))) {
			SecurityContext contexto = SecurityContextHolder.createEmptyContext();
			contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("servicio", null,
					List.of(new SimpleGrantedAuthority("ROLE_SERVICIO"))));
			SecurityContextHolder.setContext(contexto);
		}
		cadena.doFilter(peticion, respuesta);
	}

	private boolean claveValida(String clave) {
		return clave != null && MessageDigest.isEqual(claveServicio.getBytes(StandardCharsets.UTF_8),
				clave.getBytes(StandardCharsets.UTF_8));
	}
}
