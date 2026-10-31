package com.sarao.notificaciones.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record EntradasRequest(@NotNull @Valid CorreoDeCompra compra, @NotEmpty List<@NotBlank String> codigos) {
}
