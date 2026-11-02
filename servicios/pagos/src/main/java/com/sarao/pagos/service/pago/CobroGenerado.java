package com.sarao.pagos.service.pago;

// Lo que devuelve el sistema de pago al crear el cobro: el identificador de la transacción, el QR que el cliente
// escanea y la página de pago, si la hay
public record CobroGenerado(String referencia, String codigoQr, String urlPasarela) {
}
