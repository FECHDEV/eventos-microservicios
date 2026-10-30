package com.sarao.seguridad.service;

public interface NotificadorCorreo {

	void enviarRecuperacion(String correo, String nombre, String token);
}
