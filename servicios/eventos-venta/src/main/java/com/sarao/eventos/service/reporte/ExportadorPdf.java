package com.sarao.eventos.service.reporte;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

// CU-14 RF-15: A4 apaisado, con el título, la fecha, los criterios, el resumen y el detalle
@Component
public class ExportadorPdf {

	private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
	private static final Font SECCION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
	private static final Font TEXTO = FontFactory.getFont(FontFactory.HELVETICA, 9);
	private static final Font NEGRITA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);

	public byte[] exportar(ReporteGenerado generado) {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		Document documento = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
		PdfWriter.getInstance(documento, salida);
		documento.open();
		documento.add(new Paragraph(Textos.titulo(generado.reporte().getTipo()), TITULO));
		documento.add(new Paragraph(Textos.generadoYExportado(generado), TEXTO));
		documento.add(new Paragraph(Textos.criterios(generado.criterios()), TEXTO));
		seccion(documento, "Resumen", generado.contenido().resumen());
		seccion(documento, "Detalle", generado.contenido().detalle());
		documento.close();
		return salida.toByteArray();
	}

	private static void seccion(Document documento, String titulo, Tabla tabla) {
		Paragraph encabezado = new Paragraph(titulo, SECCION);
		encabezado.setSpacingBefore(12);
		encabezado.setSpacingAfter(6);
		documento.add(encabezado);
		PdfPTable pdf = new PdfPTable(tabla.columnas().size());
		pdf.setWidthPercentage(100);
		pdf.setHeaderRows(1);
		for (Columna columna : tabla.columnas()) {
			pdf.addCell(celda(columna.titulo(), NEGRITA, columna.tipo()));
		}
		for (List<Object> fila : tabla.filas()) {
			fila(pdf, tabla.columnas(), fila, TEXTO);
		}
		if (tabla.totales() != null) {
			fila(pdf, tabla.columnas(), tabla.totales(), NEGRITA);
		}
		documento.add(pdf);
	}

	private static void fila(PdfPTable pdf, List<Columna> columnas, List<Object> valores, Font fuente) {
		for (int i = 0; i < columnas.size(); i++) {
			TipoDato tipo = columnas.get(i).tipo();
			Object valor = valores.get(i);
			// «Total» va en la primera columna, aunque su tipo sea otro
			String texto = valor instanceof String cadena ? cadena : FormatoDeCeldas.texto(valor, tipo);
			pdf.addCell(celda(texto, fuente, tipo));
		}
	}

	private static PdfPCell celda(String texto, Font fuente, TipoDato tipo) {
		PdfPCell celda = new PdfPCell(new Phrase(texto, fuente));
		celda.setHorizontalAlignment(
				tipo == TipoDato.ENTERO || tipo == TipoDato.MONTO ? Element.ALIGN_CENTER : Element.ALIGN_LEFT);
		celda.setPadding(4);
		return celda;
	}
}
