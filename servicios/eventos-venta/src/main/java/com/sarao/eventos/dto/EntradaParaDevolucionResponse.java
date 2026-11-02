package com.sarao.eventos.dto;

// CU-13: una entrada con su número dentro de la compra; sin su código
public record EntradaParaDevolucionResponse(Long idEntrada, int numero) {
}
