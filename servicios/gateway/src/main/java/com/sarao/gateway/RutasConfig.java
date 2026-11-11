package com.sarao.gateway;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class RutasConfig {

	private static final Logger log = LoggerFactory.getLogger(RutasConfig.class);

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
						.route(path("/api/eventos/**", "/api/reservas/**", "/api/entradas/**", "/api/accesos/**",
								"/api/reportes/**", "/api/devoluciones/**"), http())
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
						.build())
				.filter(RutasConfig::siElServicioNoResponde);
	}

	// Un servicio apagado o que no responde no es un error del gateway: 503 con el formato y el código de los
	// demás servicios (el frontend lo traduce), y una sola línea en el registro, sin la traza
	static ServerResponse siElServicioNoResponde(ServerRequest pedido, HandlerFunction<ServerResponse> siguiente)
			throws Exception {
		try {
			return siguiente.handle(pedido);
		} catch (RestClientException e) {
			log.warn("No se pudo atender {}: un servicio no responde ({})", pedido.path(), e.getMessage());
			ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
					"El servicio no está disponible en este momento. Intente de nuevo en unos minutos.");
			problema.setInstance(URI.create(pedido.path()));
			problema.setProperty("codigo", "SERVICIO_NO_DISPONIBLE");
			return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_PROBLEM_JSON)
					.body(problema);
		}
	}
}
