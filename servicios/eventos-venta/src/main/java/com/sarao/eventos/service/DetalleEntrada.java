package com.sarao.eventos.service;

import com.sarao.eventos.entity.Entrada;

// CU-08 RF-8: una entrada con su número dentro de la compra («Entrada 2 de 3»)
public record DetalleEntrada(Entrada entrada, int numero, int cantidad) {
}
