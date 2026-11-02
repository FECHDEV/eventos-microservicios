package com.sarao.eventos.service.reporte;

import java.util.List;

import com.sarao.eventos.service.Cuenta;

// CU-14 RF-2: las opciones de los filtros
public record OpcionesDeReporte(List<EventoConTipos> eventos, List<Cuenta> personal) {
}
