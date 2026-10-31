package com.sarao.gateway;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class RutasConfig {

	// Solo se enrutan rutas publicas de /api: las de /interno nunca salen por aca
	@Bean
	RouterFunction<ServerResponse> rutas(@Value("${eventos.seguridad-url}") String seguridad,
			@Value("${eventos.venta-url}") String venta, IdentidadDeSesion identidad) {
		return route("seguridad")
				.route(path("/api/auth/**", "/api/cuenta/**", "/api/usuarios/**"), http())
				.before(uri(seguridad))
				.before(identidad::aplicar)
				.build()
				.and(route("eventos-venta")
						.route(path("/api/eventos/**", "/api/reservas/**"), http())
						.before(uri(venta))
						.before(identidad::aplicar)
						.build())
				// Público: no consulta la sesión, así un token vencido o una caída de Seguridad no afectan a estas rutas
				.and(route("publicas")
						.route(path("/api/catalogo/**", "/api/visitas/**"), http())
						.before(uri(venta))
						.before(IdentidadDeSesion::sinIdentidad)
						.build());
	}
}
