package com.sarao.eventos.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.sarao.eventos.dto.validacion.MaximoBytesUtf8;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Datos que el Administrador envía al registrar o modificar un evento (CU-10). Estado, venta abierta, fecha original y
// creador no están acá: si llegan en la petición se ignoran (RF-20). La fecha contra hoy y las horas iguales se validan
// en GestionEventosService, porque dependen del reloj y del evento que ya existe (RF-10, RF-11, RF-21)
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventoRequest(

		@NotBlank(message = "es obligatorio")
		@Size(max = 150, message = "admite hasta 150 caracteres")
		String nombre,

		@MaximoBytesUtf8(value = 65_535, message = "es demasiado larga")
		String descripcion,

		@NotNull(message = "es obligatoria")
		LocalDate fecha,

		@NotNull(message = "es obligatoria")
		LocalTime horaInicio,

		@NotNull(message = "es obligatoria")
		LocalTime horaFin,

		@NotBlank(message = "es obligatorio")
		@Size(max = 200, message = "admite hasta 200 caracteres")
		String lugar,

		@Min(value = 1, message = "debe estar entre 1 y 99")
		@Max(value = 99, message = "debe estar entre 1 y 99")
		Integer restriccionEdad,

		@Min(value = 1, message = "debe ser de al menos 1")
		Integer limiteCompra) {
}
