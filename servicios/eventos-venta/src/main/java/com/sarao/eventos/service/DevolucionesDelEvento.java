package com.sarao.eventos.service;

import java.util.List;

import com.sarao.eventos.entity.Evento;

// CU-13 RF-4 y RF-5: las compras con entradas pendientes y, aparte, las que tienen devueltas
public record DevolucionesDelEvento(Evento evento, List<CompraParaDevolucion> pendientes,
		List<CompraParaDevolucion> devueltas) {
}
