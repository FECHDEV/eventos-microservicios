package com.sarao.eventos.service.reporte;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Los valores como texto, para el PDF, como se leen en Bolivia: miles con punto y decimales con coma. La pantalla
// formatea igual (frontend: features/reportes/celda.ts)
final class FormatoDeCeldas {

	private FormatoDeCeldas() {
	}

	static String texto(Object valor, TipoDato tipo) {
		if (valor == null) {
			return "";
		}
		return switch (tipo) {
			case MONTO -> "Bs " + numero("#,##0.00").format(valor);
			case ENTERO -> numero("#,##0").format(valor);
			case FECHA -> ((LocalDate) valor).format(Textos.DIA);
			case FECHA_HORA -> ((LocalDateTime) valor).format(Textos.DIA_Y_HORA);
			case TEXTO -> valor.toString();
		};
	}

	// DecimalFormat no es seguro entre hilos: uno por llamada
	private static DecimalFormat numero(String patron) {
		DecimalFormatSymbols simbolos = new DecimalFormatSymbols();
		simbolos.setGroupingSeparator('.');
		simbolos.setDecimalSeparator(',');
		return new DecimalFormat(patron, simbolos);
	}
}
