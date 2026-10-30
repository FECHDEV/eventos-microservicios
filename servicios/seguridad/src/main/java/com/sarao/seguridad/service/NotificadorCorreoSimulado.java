package com.sarao.seguridad.service;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

// shortcut: nadie recibe el correo; el token solo sale con este logger en DEBUG. HU-17 lo reemplaza por Notificaciones,
// enviando después del commit: con correo real, una falla o la demora revelarían qué cuentas existen
@Slf4j
@Service
public class NotificadorCorreoSimulado implements NotificadorCorreo {

	@Override
	public void enviarRecuperacion(String correo, String nombre, String token) {
		log.info("[Correo simulado] Recuperación de contraseña para <{}>.", correo);
		log.debug("[Correo simulado] Token para {}: {}", nombre, token);
	}
}
