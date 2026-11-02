package com.sarao.eventos.service.reporte;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.dhatim.fastexcel.reader.CellType;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

import com.sarao.eventos.entity.Reporte;
import com.sarao.eventos.entity.TipoReporte;

// CU-14 RF-15 y RF-16: el mismo contenido que la pantalla
class ExportadoresTest {

	private static final List<Columna> RESUMEN = List.of(Columna.texto("Evento"), Columna.entero("Compras"),
			Columna.monto("Monto"));
	private static final List<Columna> DETALLE = List.of(Columna.fechaHora("Fecha de pago"), Columna.texto("Cliente"),
			Columna.texto("Teléfono"), Columna.monto("Monto"));

	// Un Cliente que se llama como una fórmula, y un teléfono vacío
	private final ReporteGenerado generado = new ReporteGenerado(
			Reporte.registrar(1L, TipoReporte.VENTAS, "desde=2026-09-01",
					LocalDateTime.of(2026, 9, 23, 14, 5)),
			List.of(new CriterioAplicado("Desde", "01/09/2026")),
			new ContenidoReporte(
					new Tabla(RESUMEN, List.of(Arrays.asList("Noche de jazz", 2L, new BigDecimal("1240.00"))),
							Arrays.asList("Total", 2L, new BigDecimal("1240.00"))),
					new Tabla(DETALLE, List.of(
							Arrays.asList(LocalDateTime.of(2026, 9, 2, 10, 14), "=1+1", null, new BigDecimal("1240.00"))),
							null)),
			LocalDateTime.of(2026, 9, 23, 15, 30));

	@Test
	void elPdfLlevaTituloCriteriosResumenYDetalle() throws IOException {
		byte[] pdf = new ExportadorPdf().exportar(generado);

		assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
		String texto = new PdfTextExtractor(new PdfReader(pdf)).getTextFromPage(1);
		// Los datos son de cuando se exportó: se dicen las dos fechas (decisiones.md → Gestionar reportes (CU-14))
		assertThat(texto).contains("Reporte de ventas", "Generado el 23/09/2026 14:05",
				"Exportado el 23/09/2026 15:30", "Desde: 01/09/2026", "Resumen",
				"Detalle", "Noche de jazz", "Bs 1.240,00", "02/09/2026 10:14", "Total");
	}

	// El texto va como texto aunque empiece con '=' (no se ejecuta como fórmula); los montos, como números
	@Test
	void elExcelLlevaNumerosComoNumerosYTextoComoTexto() throws IOException {
		byte[] excel = new ExportadorExcel().exportar(generado);

		try (ReadableWorkbook libro = new ReadableWorkbook(new ByteArrayInputStream(excel))) {
			List<Row> filas = libro.getFirstSheet().read();
			assertThat(filas.get(0).getCellText(0)).isEqualTo("Reporte de ventas");
			assertThat(filas.get(1).getCellText(0))
					.isEqualTo("Generado el 23/09/2026 14:05 · Exportado el 23/09/2026 15:30");
			assertThat(filas).anySatisfy(fila -> assertThat(fila.getCellText(0)).isEqualTo("Desde: 01/09/2026"));
			Row total = filas.stream().filter(fila -> "Total".equals(fila.getCellText(0))).findFirst().orElseThrow();
			assertThat(total.getCell(2).getType()).isEqualTo(CellType.NUMBER);
			assertThat(new BigDecimal(total.getCellText(2))).isEqualByComparingTo("1240");
			Row cliente = filas.stream().filter(fila -> fila.getCellCount() > 1 && "=1+1".equals(fila.getCellText(1)))
					.findFirst().orElseThrow();
			assertThat(cliente.getCell(1).getType()).isEqualTo(CellType.STRING);
			assertThat(cliente.getCell(3).getType()).isEqualTo(CellType.NUMBER);
		}
	}
}
