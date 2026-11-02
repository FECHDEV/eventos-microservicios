package com.sarao.notificaciones.dto;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// CU-14 RF-19 a RF-23: un reporte ya exportado, para un solo correo a buzones del dominio propio. criterios ya viene
// como «Desde: 01/09/2026»; el contenido de cada adjunto, en base64 (Jackson lo lee como byte[])
public record ReporteRequest(@NotBlank String titulo, @NotBlank String enviadoPor, @NotNull List<String> criterios,
		@NotNull LocalDateTime datosAl, @NotEmpty @Size(max = 10) List<@NotBlank @Email String> destinatarios,
		@NotEmpty @Size(max = 2) List<@Valid Adjunto> adjuntos) {

	public record Adjunto(@NotBlank String nombre, @NotEmpty byte[] contenido) {
	}
}
