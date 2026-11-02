package com.sarao.eventos.repository;

// Una cantidad agrupada por id, sin cargar las filas (CU-14 RF-8: devueltas por pago)
public record CantidadPorId(Long id, Long cantidad) {
}
