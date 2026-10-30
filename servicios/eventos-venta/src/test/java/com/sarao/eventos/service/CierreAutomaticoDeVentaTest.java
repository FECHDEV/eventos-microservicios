package com.sarao.eventos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.sarao.eventos.config.ProgramacionConfig;

@ExtendWith(MockitoExtension.class)
class CierreAutomaticoDeVentaTest {

	@Mock
	private GestionEstadoEventosService gestionEstadoEventosService;

	@InjectMocks
	private CierreAutomaticoDeVenta cierreAutomaticoDeVenta;

	@Test
	void cadaVezQueCorreCierraLasVentasVencidas() {
		cierreAutomaticoDeVenta.cerrarLasVencidas();

		then(gestionEstadoEventosService).should().cerrarVentasVencidas();
	}

	// RF-20: el intervalo es configurable, y la primera corrida espera lo mismo que las siguientes, para que en las
	// pruebas la tarea no dispare al levantar el contexto
	@Test
	void seProgramaConLaPropiedadDelIntervalo() throws Exception {
		Scheduled programacion = CierreAutomaticoDeVenta.class.getDeclaredMethod("cerrarLasVencidas")
				.getAnnotation(Scheduled.class);

		assertThat(programacion).isNotNull();
		assertThat(programacion.fixedDelayString()).isEqualTo("${eventos.cierre-de-venta.intervalo}");
		assertThat(programacion.initialDelayString()).isEqualTo("${eventos.cierre-de-venta.intervalo}");
	}

	@Test
	void laProgramacionEstaHabilitada() {
		assertThat(ProgramacionConfig.class.getAnnotation(EnableScheduling.class)).isNotNull();
	}
}
