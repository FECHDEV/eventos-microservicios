package com.sarao.eventos.service;

import com.sarao.eventos.entity.Evento;

// CU-12: el evento recién activado y cuántas entradas usadas se reactivaron (FA-02, RF-31); 0 si venía de BORRADOR
public record EventoActivado(Evento evento, int entradasReactivadas) {
}
