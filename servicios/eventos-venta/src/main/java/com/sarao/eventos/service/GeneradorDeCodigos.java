package com.sarao.eventos.service;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

// El código de cada entrada (CU-07 RF-12; decisiones.md → Realizar pago): 128 bits al azar, imposibles de adivinar,
// en Base64 apto para URL y sin relleno. La unicidad la garantiza además el UNIQUE de entrada.codigo_qr
@Component
public class GeneradorDeCodigos {

	private final SecureRandom azar = new SecureRandom();

	public String nuevoCodigo() {
		byte[] bytes = new byte[16];
		azar.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
