package com.sarao.eventos.service.reporte;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.springframework.stereotype.Component;

// CU-14 RF-16: una hoja con el encabezado, el resumen y el detalle. Los montos y las cantidades van como números y las
// fechas como fechas, para poder sumarlos y ordenarlos. Todo texto va como celda de texto: un nombre que empieza con
// '=' no se ejecuta como fórmula (RNF-03)
@Component
public class ExportadorExcel {

	public byte[] exportar(ReporteGenerado generado) {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		try {
			Workbook libro = new Workbook(salida, "Reportes", "1.0");
			Worksheet hoja = libro.newWorksheet("Reporte");
			hoja.value(0, 0, Textos.titulo(generado.reporte().getTipo()));
			hoja.style(0, 0).bold().set();
			hoja.value(1, 0, Textos.generadoYExportado(generado));
			hoja.value(2, 0, Textos.criterios(generado.criterios()));
			int fila = tabla(hoja, 4, "Resumen", generado.contenido().resumen());
			tabla(hoja, fila + 1, "Detalle", generado.contenido().detalle());
			for (int columna = 0; columna < generado.contenido().detalle().columnas().size(); columna++) {
				hoja.width(columna, 20);
			}
			libro.finish();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return salida.toByteArray();
	}

	// Devuelve la fila siguiente a la tabla
	private static int tabla(Worksheet hoja, int fila, String titulo, Tabla tabla) {
		hoja.value(fila, 0, titulo);
		hoja.style(fila, 0).bold().set();
		fila++;
		List<Columna> columnas = tabla.columnas();
		for (int i = 0; i < columnas.size(); i++) {
			hoja.value(fila, i, columnas.get(i).titulo());
			hoja.style(fila, i).bold().set();
		}
		fila++;
		for (List<Object> valores : tabla.filas()) {
			celdas(hoja, fila++, columnas, valores, false);
		}
		if (tabla.totales() != null) {
			celdas(hoja, fila++, columnas, tabla.totales(), true);
		}
		return fila;
	}

	private static void celdas(Worksheet hoja, int fila, List<Columna> columnas, List<Object> valores,
			boolean negrita) {
		for (int i = 0; i < columnas.size(); i++) {
			Object valor = valores.get(i);
			if (valor == null) {
				continue;
			}
			// «Total» va en la primera columna, aunque su tipo sea otro
			TipoDato tipo = valor instanceof String ? TipoDato.TEXTO : columnas.get(i).tipo();
			String formato = switch (tipo) {
				case ENTERO -> {
					hoja.value(fila, i, (Number) valor);
					yield null;
				}
				case MONTO -> {
					hoja.value(fila, i, (Number) valor);
					yield "#,##0.00";
				}
				case FECHA -> {
					hoja.value(fila, i, (LocalDate) valor);
					yield "dd/mm/yyyy";
				}
				case FECHA_HORA -> {
					hoja.value(fila, i, (LocalDateTime) valor);
					yield "dd/mm/yyyy hh:mm";
				}
				case TEXTO -> {
					hoja.value(fila, i, valor.toString());
					yield null;
				}
			};
			// El formato y la negrita en un solo estilo: uno segundo reemplazaría al primero
			if (formato != null && negrita) {
				hoja.style(fila, i).format(formato).bold().set();
			} else if (formato != null) {
				hoja.style(fila, i).format(formato).set();
			} else if (negrita) {
				hoja.style(fila, i).bold().set();
			}
		}
	}
}
