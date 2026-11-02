package com.sarao.eventos.repository;

// CU-13 RF-2: cuántas entradas activas tiene un evento cancelado, sin cargarlas
public record PendientesDelEvento(Long idEvento, Long pendientes) {
}
