package com.sarao.pagos.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.sarao.pagos.repository.PagoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// CU-07 RF-20 (decisiones.md → Realizar pago: verificación en segundo plano). Un pago tardío se detecta aunque el
// Cliente haya cerrado la pantalla, y una confirmación que falló se repite
@Slf4j
@Component
@RequiredArgsConstructor
public class ConciliacionAutomaticaDePagos {

	// La deuda del sistema de pago vence por día: un pago sin procesar se mira hasta 24 horas después de vencer la reserva
	static final Duration VENTANA = Duration.ofHours(24);

	private final PagoRepository pagoRepository;
	private final ConciliacionDePagos conciliacionDePagos;
	private final Clock clock;

	// Cada pago por su lado: uno que falla no frena al resto, y la tarea no se cae
	@Scheduled(fixedDelayString = "${eventos.pago.conciliacion.intervalo}",
			initialDelayString = "${eventos.pago.conciliacion.intervalo}")
	public void conciliarPendientes() {
		for (Long idPago : pagoRepository.idsPorConciliar(LocalDateTime.now(clock).minus(VENTANA))) {
			try {
				conciliacionDePagos.conciliar(idPago);
			} catch (RuntimeException error) {
				log.error("No se pudo conciliar el pago {}", idPago, error);
			}
		}
	}
}
