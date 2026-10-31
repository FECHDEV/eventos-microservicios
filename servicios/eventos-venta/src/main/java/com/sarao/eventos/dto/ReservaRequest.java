package com.sarao.eventos.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// Lo que el Cliente elige (CU-06 paso 1): un solo tipo y una cantidad. El Cliente es el de la sesión, nunca un dato de
// la petición
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReservaRequest(

		@NotNull(message = "es obligatorio")
		Long idTipoEntrada,

		@NotNull(message = "es obligatoria")
		@Min(value = 1, message = "debe ser de al menos 1")
		Integer cantidad) {
}
