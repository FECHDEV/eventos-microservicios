package com.sarao.notificaciones.service;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.sarao.notificaciones.dto.CorreoDeCompra;
import lombok.extern.slf4j.Slf4j;

// La implementación por defecto: nadie recibe el correo. El token y los códigos solo salen con este logger en DEBUG, porque
// dan acceso a una cuenta y a un evento
@Slf4j
@Service
@ConditionalOnProperty(name = "eventos.correo.proveedor", havingValue = "simulador", matchIfMissing = true)
public class NotificadorCorreoSimulado implements NotificadorCorreo {

	@Override
	public void enviarRecuperacion(String correo, String nombre, String token) {
		log.info("[Correo simulado] Recuperación de contraseña para {} <{}>.", nombre, correo);
		log.debug("[Correo simulado] Token: {}", token);
	}

	@Override
	public void enviarDetalleDeCompra(CorreoDeCompra compra) {
		log.info("[Correo simulado] Detalle de compra para {} <{}>: {} × {} de {} ({} {} en {}), Bs {} c/u, total Bs {}",
				compra.nombre(), compra.correo(), compra.cantidad(), compra.tipo(), compra.evento(), compra.fecha(),
				compra.horaInicio(), compra.lugar(), compra.precioUnitario().toPlainString(),
				compra.total().toPlainString());
	}

	@Override
	public void enviarEntradas(CorreoDeCompra compra, List<String> codigos) {
		log.info("[Correo simulado] {} entradas para {} <{}> de {}.", codigos.size(), compra.nombre(), compra.correo(),
				compra.evento());
		log.debug("[Correo simulado] Códigos: {}", String.join(", ", codigos));
	}
}
