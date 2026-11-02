package com.sarao.eventos.service.reporte;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.sarao.eventos.entity.EstadoEntrada;
import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.entity.TipoEntrada;
import com.sarao.eventos.entity.TipoReporte;
import com.sarao.eventos.service.Cuenta;

// Cómo se nombran las cosas en los reportes, igual en la pantalla, el PDF y el Excel
public final class Textos {

	// Los resúmenes van en el orden de los eventos: por fecha, y a igual fecha por id
	static final Comparator<Evento> POR_EVENTO = Comparator.comparing(Evento::getFecha)
			.thenComparing(Evento::getIdEvento);
	static final Comparator<TipoEntrada> POR_EVENTO_Y_TIPO = Comparator
			.comparing(TipoEntrada::getEvento, POR_EVENTO).thenComparing(TipoEntrada::getIdTipoEntrada);

	public static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	public static final DateTimeFormatter DIA_Y_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	private Textos() {
	}

	public static String generado(LocalDateTime fecha) {
		return "Generado el " + fecha.format(DIA_Y_HORA);
	}

	// El encabezado de los archivos: los datos son de cuando se exportó
	public static String generadoYExportado(ReporteGenerado generado) {
		String texto = generado(generado.reporte().getFechaGeneracion());
		return generado.exportado() == null ? texto
				: texto + " · Exportado el " + generado.exportado().format(DIA_Y_HORA);
	}

	// FA-01: sin criterios, el reporte trae toda la información disponible
	public static String criterios(List<CriterioAplicado> criterios) {
		return criterios.isEmpty() ? "Sin filtros"
				: criterios.stream().map(criterio -> criterio.nombre() + ": " + criterio.valor())
						.collect(Collectors.joining(" · "));
	}

	public static String titulo(TipoReporte tipo) {
		return switch (tipo) {
			case VENTAS -> "Reporte de ventas";
			case INGRESOS -> "Reporte de ingresos";
			case ENTRADAS -> "Reporte de entradas";
			case CLIENTES -> "Reporte de clientes";
			case ACCESOS -> "Reporte de accesos";
		};
	}

	// Una cuenta que Seguridad no devolvió no deja el reporte sin armar
	public static String nombre(Cuenta cuenta) {
		return cuenta == null ? "—" : cuenta.nombre() + " " + cuenta.apellido();
	}

	public static String estado(EstadoEntrada estado) {
		return switch (estado) {
			case ACTIVA -> "Activa";
			case UTILIZADA -> "Utilizada";
			case DEVUELTA -> "Devuelta";
		};
	}

	// El estado de una cuenta de Seguridad: ACTIVA o DESACTIVADA
	public static String estadoDeCuenta(String estado) {
		return "DESACTIVADA".equals(estado) ? "Desactivada" : "Activa";
	}
}
