package com.sarao.seguridad.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import jakarta.servlet.DispatcherType;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, @Value("${eventos.clave-servicio}") String claveServicio,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver manejadorExcepciones) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(peticiones -> peticiones
						// El despacho interno a /error no pasa por el filtro de identidad: sin esto, cualquier error de una
						// petición autenticada terminaría como 401
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/registro", "/api/auth/inicio-sesion").permitAll()
						.requestMatchers("/interno/**").hasRole("SERVICIO")
						// El rol SERVICIO no entra a las rutas de usuarios
						.anyRequest().hasAnyRole("CLIENTE", "ADMINISTRADOR", "PERSONAL"))
				// El 401 y el 403 pasan por ManejadorErrores para salir con el mismo formato que el resto de la API
				.exceptionHandling(errores -> errores
						.authenticationEntryPoint((peticion, respuesta, excepcion) -> {
							respuesta.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
							manejadorExcepciones.resolveException(peticion, respuesta, null, excepcion);
						})
						.accessDeniedHandler((peticion, respuesta, excepcion) -> manejadorExcepciones
								.resolveException(peticion, respuesta, null, excepcion)))
				.addFilterBefore(new IdentidadDelGatewayFiltro(claveServicio),
						UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
