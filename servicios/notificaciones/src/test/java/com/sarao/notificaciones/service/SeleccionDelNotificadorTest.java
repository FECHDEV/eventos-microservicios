package com.sarao.notificaciones.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

import com.sarao.notificaciones.config.CorreoConfig;

class SeleccionDelNotificadorTest {

	private final ApplicationContextRunner contexto = new ApplicationContextRunner()
			.withUserConfiguration(CorreoConfig.class, NotificadorCorreoSimulado.class, NotificadorCorreoSmtp.class)
			.withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
			.withPropertyValues("eventos.correo.remitente=Sarao <no-responder@mail.com>",
					"eventos.correo.url-del-sitio=http://localhost");

	@Test
	void sinProveedorSeUsaElSimulador() {
		contexto.run(ctx -> assertThat(ctx).getBean(NotificadorCorreo.class)
				.isInstanceOf(NotificadorCorreoSimulado.class));
	}

	@Test
	void conSmtpSeUsaElAdaptadorYEnviaEnSegundoPlano() {
		contexto.withPropertyValues("eventos.correo.proveedor=smtp").run(ctx -> {
			assertThat(ctx).hasSingleBean(NotificadorCorreo.class).doesNotHaveBean(NotificadorCorreoSimulado.class);
			assertThat(AopUtils.isAopProxy(ctx.getBean(NotificadorCorreo.class))).isTrue();
		});
	}

	@Test
	void sinLaUrlDelSitioNoArranca() {
		contexto.withPropertyValues("eventos.correo.url-del-sitio=").run(ctx -> assertThat(ctx).hasFailed().getFailure()
				.hasStackTraceContaining("eventos.correo"));
	}
}
