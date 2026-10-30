package com.sarao.seguridad.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DatosPersonalesRequestTest {

	// CU-04 RF-8: solo espacios deja la cuenta sin teléfono, sin importar cuántos
	@Test
	void telefonoEnBlancoLargoCuentaComoAusente() {
		assertThat(new DatosPersonalesRequest("Ana", "Rojas", " ".repeat(25)).telefono()).isNull();
	}
}
