package com.sarao.eventos.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.sarao.eventos.entity.TipoReporte;
import com.sarao.eventos.service.reporte.CriterioAplicado;
import com.sarao.eventos.service.reporte.ReporteGenerado;
import com.sarao.eventos.service.reporte.Tabla;
import com.sarao.eventos.service.reporte.Textos;

// CU-14 RF-6. Las tablas y los criterios salen tal cual: son datos planos, sin entidades
public record ReporteResponse(Long idReporte, TipoReporte tipo, String titulo, LocalDateTime fechaGeneracion,
		List<CriterioAplicado> criterios, Tabla resumen, Tabla detalle) {

	public static ReporteResponse from(ReporteGenerado generado) {
		return new ReporteResponse(generado.reporte().getIdReporte(), generado.reporte().getTipo(),
				Textos.titulo(generado.reporte().getTipo()), generado.reporte().getFechaGeneracion(),
				generado.criterios(), generado.contenido().resumen(), generado.contenido().detalle());
	}
}
