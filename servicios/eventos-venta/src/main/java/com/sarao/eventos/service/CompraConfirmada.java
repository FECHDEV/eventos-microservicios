package com.sarao.eventos.service;

// Se publica dentro de la transacción que confirma la compra; los correos salen después del commit (CorreosDeCompra)
public record CompraConfirmada(Long idReserva) {
}
