package com.sarao.eventos.service.reporte;

import java.util.List;

import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.entity.TipoEntrada;

// CU-14 RF-2: un evento del filtro con sus tipos de entrada
public record EventoConTipos(Evento evento, List<TipoEntrada> tipos) {
}
