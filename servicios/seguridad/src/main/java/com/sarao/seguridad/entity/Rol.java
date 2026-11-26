package com.sarao.seguridad.entity;

// PERSONAL es Control de acceso: el código se conserva para no migrar datos (decisiones.md → Roles del personal)
public enum Rol {
	CLIENTE, ADMINISTRADOR, PERSONAL, ORGANIZADOR, CONTADOR;

	// El Cliente es la cuenta de quien compra; los demás son roles del personal
	public boolean esDelPersonal() {
		return this != CLIENTE;
	}
}
