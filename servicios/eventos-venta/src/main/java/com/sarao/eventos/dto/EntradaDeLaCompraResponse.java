package com.sarao.eventos.dto;

import com.sarao.eventos.entity.EstadoEntrada;

// Una entrada dentro de una compra del listado: sin su código (CU-08 RF-10)
public record EntradaDeLaCompraResponse(Long idEntrada, int numero, EstadoEntrada estado) {
}
