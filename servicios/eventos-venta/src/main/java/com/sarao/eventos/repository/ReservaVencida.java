package com.sarao.eventos.repository;

// Lo necesario para liberar una reserva vencida sin cargarla (LiberacionDeReservas)
public record ReservaVencida(Long idReserva, Long idTipoEntrada, int cantidad) {
}
