package com.sarao.eventos.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
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
						// CU-05: el catálogo se consulta sin sesión. Solo lectura: cualquier otro método sigue rechazado
						.requestMatchers(HttpMethod.GET, "/api/catalogo/**").permitAll()
						.requestMatchers(HttpMethod.HEAD, "/api/catalogo/**").permitAll()
						// El contador del pie de cada página, también sin sesión. Solo sumar una visita
						.requestMatchers(HttpMethod.POST, "/api/visitas/*").permitAll()
						.requestMatchers("/api/eventos/**").hasRole("ADMINISTRADOR")
						.anyRequest().denyAll())
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
}
