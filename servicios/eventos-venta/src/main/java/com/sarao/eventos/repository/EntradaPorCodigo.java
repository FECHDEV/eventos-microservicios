package com.sarao.eventos.repository;

// CU-15: la entrada de un código y su evento, sin cargarla: se relee después con bloqueo
public record EntradaPorCodigo(Long idEntrada, Long idEvento) {
}
