package com.sarao.eventos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// CU-15: el código leído y el evento elegido. El código de una entrada es de 22 caracteres; 255 es el largo de la
// columna
public record ValidacionRequest(
		@NotNull(message = "es obligatorio") Long idEvento,
		@NotBlank(message = "es obligatorio") @Size(max = 255, message = "admite hasta 255 caracteres") String codigo) {
}
