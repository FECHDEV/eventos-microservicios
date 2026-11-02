package com.sarao.pagos.config;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.sarao.pagos.service.pago.AdaptadorLibelula;
import com.sarao.pagos.service.pago.IPago;

// El sistema de pago real (decisiones.md → Adaptador de Libélula). Con plazos: «Pagar» llama sin nada bloqueado, pero
// el Cliente espera la respuesta, y la conciliación de cada minuto no se puede colgar. El cuerpo va entero, con
// Content-Length, y por HTTP/1.1: Libélula no lee un cuerpo enviado por partes y responde «Debe especificar el parámetro
// AppKey» (LibelulaConfigTest)
@Configuration
@ConditionalOnProperty(name = "eventos.pago.proveedor", havingValue = "libelula")
@EnableConfigurationProperties(LibelulaProperties.class)
public class LibelulaConfig {

	static final Duration CONEXION = Duration.ofSeconds(5);
	static final Duration LECTURA = Duration.ofSeconds(15);

	@Bean
	IPago adaptadorLibelula(LibelulaProperties cuenta, Clock clock) {
		HttpClient cliente = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(CONEXION)
				.followRedirects(HttpClient.Redirect.NORMAL).build();
		JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(cliente);
		fabrica.setReadTimeout(LECTURA);
		return new AdaptadorLibelula(RestClient.builder().requestFactory(new BufferingClientHttpRequestFactory(fabrica)),
				cuenta, clock);
	}
}
