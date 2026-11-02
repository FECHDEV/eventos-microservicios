package com.sarao.pagos.service.pago;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.sarao.pagos.config.ClockConfig;
import com.sarao.pagos.config.LibelulaConfig;
import com.sarao.pagos.config.LibelulaProperties;

// decisiones.md → Pago: interfaz propia con dos implementaciones, elegidas con eventos.pago.proveedor
class SeleccionDelProveedorDePagoTest {

	private final ApplicationContextRunner contexto = new ApplicationContextRunner()
			.withUserConfiguration(ClockConfig.class, SimuladorDePago.class, LibelulaConfig.class)
			.withPropertyValues("eventos.pago.libelula.url=https://api.libelula.bo",
					"eventos.pago.libelula.url-del-sitio=https://sarao.site");

	private ApplicationContextRunner conLibelula() {
		return contexto.withPropertyValues("eventos.pago.proveedor=libelula",
				"eventos.pago.libelula.appkey=clave-de-prueba", "eventos.pago.libelula.email=pagos@sarao.site");
	}

	@Test
	void sinProveedorSeUsaElSimulador() {
		contexto.run(ctx -> assertThat(ctx).getBean(IPago.class).isInstanceOf(SimuladorDePago.class));
	}

	@Test
	void conLibelulaSeUsaElAdaptador() {
		conLibelula().run(ctx -> {
			assertThat(ctx).hasSingleBean(IPago.class).doesNotHaveBean(SimuladorDePago.class);
			assertThat(ctx).getBean(IPago.class).isInstanceOf(AdaptadorLibelula.class);
		});
	}

	@Test
	void conLibelulaSinAppkeyNoArranca() {
		conLibelula().withPropertyValues("eventos.pago.libelula.appkey=").run(ctx -> assertThat(ctx).hasFailed()
				.getFailure().hasStackTraceContaining("eventos.pago.libelula.appkey"));
	}

	@Test
	void conLibelulaSinCorreoNoArranca() {
		conLibelula().withPropertyValues("eventos.pago.libelula.email=").run(ctx -> assertThat(ctx).hasFailed()
				.getFailure().hasStackTraceContaining("eventos.pago.libelula.email"));
	}

	// La appkey no aparece si se imprime la configuración
	@Test
	void laConfiguracionNoMuestraLaAppkey() {
		conLibelula().run(ctx -> assertThat(ctx.getBean(LibelulaProperties.class)
				.toString()).doesNotContain("clave-de-prueba"));
	}
}
