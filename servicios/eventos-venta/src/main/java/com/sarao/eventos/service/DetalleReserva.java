package com.sarao.eventos.service;

import java.time.LocalDateTime;

import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.entity.Reserva;
import com.sarao.eventos.entity.TipoEntrada;

// Una reserva con lo que la respuesta muestra de su tipo y su evento, ya cargado, y el instante de la consulta, del que
// salen su estado visible y los segundos que le quedan
public record DetalleReserva(Reserva reserva, TipoEntrada tipo, Evento evento, LocalDateTime consultadaEn) {
}
