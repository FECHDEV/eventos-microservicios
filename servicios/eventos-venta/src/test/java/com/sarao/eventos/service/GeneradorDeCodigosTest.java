package com.sarao.eventos.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

// CU-07 RF-12: al azar, de 128 bits y aptos para una URL
class GeneradorDeCodigosTest {

	@Test
	void generaCodigosDistintosDe128Bits() {
		GeneradorDeCodigos generador = new GeneradorDeCodigos();
		Set<String> codigos = new HashSet<>();
		for (int i = 0; i < 1000; i++) {
			codigos.add(generador.nuevoCodigo());
		}

		assertThat(codigos).hasSize(1000).allMatch(codigo -> codigo.matches("[A-Za-z0-9_-]{22}"));
	}
}
