package com.sarao.eventos.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// El actor Tiempo de CU-12 FA-01 (decisiones.md → Cierre automático de la venta con @Scheduled)
@Slf4j
@Component
@RequiredArgsConstructor
public class CierreAutomaticoDeVenta {

	private final GestionEstadoEventosService gestionEstadoEventosService;

	// Si la excepción saliera, Spring no volvería a programar la tarea y la venta quedaría abierta para siempre
	@Scheduled(fixedDelayString = "${eventos.cierre-de-venta.intervalo}",
			initialDelayString = "${eventos.cierre-de-venta.intervalo}")
	public void cerrarLasVencidas() {
		try {
			gestionEstadoEventosService.cerrarVentasVencidas();
		} catch (RuntimeException error) {
			log.error("No se pudo cerrar la venta de los eventos vencidos", error);
		}
	}
}
