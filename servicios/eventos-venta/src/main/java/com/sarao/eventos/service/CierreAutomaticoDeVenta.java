package com.sarao.eventos.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

// El actor Tiempo de CU-12 FA-01 (decisiones.md → Cierre automático de la venta con @Scheduled)
@Component
@RequiredArgsConstructor
public class CierreAutomaticoDeVenta {

	private final GestionEstadoEventosService gestionEstadoEventosService;

	@Scheduled(fixedDelayString = "${eventos.cierre-de-venta.intervalo}",
			initialDelayString = "${eventos.cierre-de-venta.intervalo}")
	public void cerrarLasVencidas() {
		gestionEstadoEventosService.cerrarVentasVencidas();
	}
}
