package com.sarao.eventos.service.reporte;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import com.sarao.eventos.entity.Reporte;

// CU-14: el reporte registrado, sus criterios descritos y su contenido. Lo que muestran la API y los archivos no tiene
// nada perezoso (del Reporte solo se leen su id, su tipo y su fecha): se usa fuera de la transacción. exportado es null
// al generar; al exportar, el momento en que se volvió a armar con los datos de entonces (decisiones.md → 'Gestionar
// reportes (CU-14)')
public record ReporteGenerado(Reporte reporte, List<CriterioAplicado> criterios, ContenidoReporte contenido,
		LocalDateTime exportado) {

	// reporte-ventas-12.pdf: el mismo nombre de la descarga, para que el archivo se reconozca (RF-19)
	public String nombreArchivo(String extension) {
		return "reporte-" + reporte.getTipo().name().toLowerCase(Locale.ROOT) + "-" + reporte.getIdReporte() + "."
				+ extension;
	}
}
