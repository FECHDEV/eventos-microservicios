package com.sarao.seguridad.exception;

import org.springframework.http.HttpStatus;

// CU-09 RF-25 (decisiones.md → Roles del personal)
public class ClienteYPersonalSeparadosException extends ErrorNegocioException {

	public ClienteYPersonalSeparadosException() {
		super("CLIENTE_Y_PERSONAL_SEPARADOS", HttpStatus.CONFLICT,
				"Una cuenta de cliente no pasa a ser del personal ni al revés: al personal lo da de alta el Administrador.");
	}
}
