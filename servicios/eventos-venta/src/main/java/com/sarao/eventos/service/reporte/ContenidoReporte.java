package com.sarao.eventos.service.reporte;

// CU-14 RF-6: el resumen con los totales agrupados y el detalle fila por fila
public record ContenidoReporte(Tabla resumen, Tabla detalle) {

	// FA-02: sin filas en el detalle no hay reporte
	public boolean sinResultados() {
		return detalle.filas().isEmpty();
	}
}
