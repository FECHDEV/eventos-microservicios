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
			@Value("${eventos.venta-url}") String venta, @Value("${eventos.pagos-url}") String pagos,
			IdentidadDeSesion identidad) {
		// El pago de una reserva va antes que /api/reservas/**: la primera ruta que coincide es la que atiende
		return route("pagos")
				.route(path("/api/reservas/*/pago", "/api/reservas/*/pago/**"), http())
				.before(uri(pagos))
				.before(identidad::aplicar)
				.build()
				.and(route("seguridad")
						.route(path("/api/auth/**", "/api/cuenta/**", "/api/usuarios/**"), http())
						.before(uri(seguridad))
						.before(identidad::aplicar)
						.build())
				.and(route("eventos-venta")
						.route(path("/api/eventos/**", "/api/reservas/**", "/api/entradas/**"), http())
						.before(uri(venta))
						.before(identidad::aplicar)
						.build())
				// El aviso del sistema de pago: público, y no confirma nada
				.and(route("aviso-de-pago")
						.route(path("/api/pagos/aviso"), http())
						.before(uri(pagos))
						.before(IdentidadDeSesion::sinIdentidad)
						.build())
				// Público: no consulta la sesión, así un token vencido o una caída de Seguridad no afectan a estas rutas
				.and(route("publicas")
						.route(path("/api/catalogo/**", "/api/visitas/**"), http())
						.before(uri(venta))
						.before(IdentidadDeSesion::sinIdentidad)
						.build());
	}
}
