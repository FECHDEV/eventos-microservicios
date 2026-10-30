package com.sarao.seguridad.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.sarao.seguridad.entity.Rol;
import jakarta.validation.constraints.NotNull;

// El administrador que opera sale de la sesión: si la petición trae otros campos, se ignoran
@JsonIgnoreProperties(ignoreUnknown = true)
public record CambioRolRequest(@NotNull(message = "es obligatorio") Rol rol) {
}
