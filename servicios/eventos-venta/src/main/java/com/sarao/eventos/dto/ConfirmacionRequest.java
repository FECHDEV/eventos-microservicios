package com.sarao.eventos.dto;

import jakarta.validation.constraints.NotNull;

public record ConfirmacionRequest(@NotNull Long idPago) {
}
