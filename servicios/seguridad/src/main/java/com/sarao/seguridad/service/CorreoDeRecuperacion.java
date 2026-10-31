package com.sarao.seguridad.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;

// Después del commit: no se manda un enlace cuyo token se revirtió
@Component
@RequiredArgsConstructor
public class CorreoDeRecuperacion {

	private final NotificadorCorreo notificador;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void alSolicitarse(RecuperacionSolicitada solicitud) {
		notificador.enviarRecuperacion(solicitud.correo(), solicitud.nombre(), solicitud.token());
	}
}
