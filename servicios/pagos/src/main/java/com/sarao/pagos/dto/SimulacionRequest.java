package com.sarao.pagos.dto;

import com.sarao.pagos.service.pago.EstadoDelCobro;
import jakarta.validation.constraints.NotNull;

// El resultado que se fuerza en el simulador (CU-07 RF-22). PENDIENTE no se fuerza: se rechaza en el controlador
public record SimulacionRequest(@NotNull(message = "es obligatorio") EstadoDelCobro resultado) {
}
