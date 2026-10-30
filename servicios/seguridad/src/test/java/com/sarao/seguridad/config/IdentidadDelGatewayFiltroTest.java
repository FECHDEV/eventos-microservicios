package com.sarao.seguridad.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IdentidadDelGatewayFiltroTest {

	@Test
	void claveDeServicioEnBlancoNoArranca() {
		assertThatThrownBy(() -> new IdentidadDelGatewayFiltro(" "))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("EVENTOS_CLAVE_SERVICIO");
	}
}
