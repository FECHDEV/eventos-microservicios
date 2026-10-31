package com.sarao.notificaciones.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// Lo que llevan los dos correos de una compra (CU-07 RF-17): el detalle de venta de RF-42
public record CorreoDeCompra(@NotBlank @Email String correo, @NotBlank String nombre, @NotBlank String evento,
		@NotNull LocalDate fecha, @NotNull LocalTime horaInicio, @NotBlank String lugar, @NotBlank String tipo,
		@Min(1) int cantidad, @NotNull BigDecimal precioUnitario, @NotNull BigDecimal total) {
}
