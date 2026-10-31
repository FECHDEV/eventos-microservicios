package com.sarao.notificaciones.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RecuperacionRequest(@NotBlank @Email String correo, @NotBlank String nombre, @NotBlank String token) {
}
