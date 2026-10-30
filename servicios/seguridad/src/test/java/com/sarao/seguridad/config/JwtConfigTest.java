package com.sarao.seguridad.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class JwtConfigTest {

	private final ApplicationContextRunner contexto = new ApplicationContextRunner()
			.withUserConfiguration(JwtConfig.class)
			.withBean(Clock.class, Clock::systemUTC)
			.withPropertyValues("eventos.jwt.duracion=8h");

	@Test
	void sinClaveLaAplicacionNoArranca() {
		contexto.run(ctx -> assertThat(ctx).hasFailed());
	}

	@Test
	void sinLaVariableDeEntornoElErrorIndicaQueHayQueConfigurarla() {
		// Sin la variable, Spring deja el texto "${EVENTOS_JWT_SECRETO}" sin resolver en la propiedad
		contexto.withPropertyValues("eventos.jwt.secreto=${EVENTOS_JWT_SECRETO_AUSENTE_EN_PRUEBAS}")
				.run(ctx -> assertThat(ctx).getFailure().rootCause().hasMessageContaining("EVENTOS_JWT_SECRETO"));
	}

	@Test
	void conClaveCortaLaAplicacionNoArranca() {
		contexto.withPropertyValues("eventos.jwt.secreto=Y2xhdmUtY29ydGEtZGUtMzEtYnl0ZXMtZXhhY3Rvcw==")
				.run(ctx -> assertThat(ctx).getFailure().rootCause().hasMessageContaining("EVENTOS_JWT_SECRETO"));
	}

	@Test
	void conClaveValidaQuedaDisponibleElEmisorDeTokens() {
		contexto.withPropertyValues("eventos.jwt.secreto=Y2xhdmUtZGUtcHJ1ZWJhcy1wYXJhLXRva2Vucy1qd3QtZXZlbnRvcw==")
				.run(ctx -> assertThat(ctx).hasSingleBean(TokenJwt.class));
	}
}
