package com.sarao.eventos.repository;

// CU-14 RF-10: cuántas compras (pagos con entradas) y cuántas entradas tiene un Cliente
public record ComprasDelCliente(Long idCliente, Long compras, Long entradas) {
}
