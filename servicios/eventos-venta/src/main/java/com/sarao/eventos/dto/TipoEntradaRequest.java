package com.sarao.eventos.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// Datos que el Administrador envía al registrar o modificar un tipo de entrada (CU-11). La cantidad disponible no está
// acá: si llega en la petición se ignora, porque solo se deriva de la total (RF-18). El mínimo de la cantidad total
// depende de lo ya vendido y se valida en GestionTiposEntradaService (RF-15)
@JsonIgnoreProperties(ignoreUnknown = true)
public record TipoEntradaRequest(

		@NotBlank(message = "es obligatorio")
		@Size(max = 80, message = "admite hasta 80 caracteres")
		String nombre,

		// DECIMAL(10,2) y sin entradas gratis (decisiones.md → Precio de un tipo de entrada)
		@NotNull(message = "es obligatorio")
		@Positive(message = "debe ser mayor que 0")
		@Digits(integer = 8, fraction = 2, message = "admite hasta 8 cifras enteras y 2 decimales")
		BigDecimal precio,

		@NotNull(message = "es obligatoria")
		@Min(value = 1, message = "debe ser de al menos 1")
		Integer cantidadTotal) {
}
