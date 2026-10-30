package com.sarao.seguridad.service;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

// shortcut: escribe el token en claro en el registro, HU-17 lo reemplaza por el servicio de Notificaciones antes del despliegue
@Slf4j
@Service
public class NotificadorCorreoSimulado implements NotificadorCorreo {

	@Override
	public void enviarRecuperacion(String correo, String nombre, String token) {
		log.info("[Correo simulado] Recuperación de contraseña para {} <{}>. Token: {}", nombre, correo, token);
	}
}
