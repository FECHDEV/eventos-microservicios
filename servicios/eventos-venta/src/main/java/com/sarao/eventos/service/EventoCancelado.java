package com.sarao.eventos.service;

import com.sarao.eventos.entity.Evento;

// CU-13 RF-2: un evento cancelado y cuántas entradas activas tiene
public record EventoCancelado(Evento evento, int pendientes) {
}
