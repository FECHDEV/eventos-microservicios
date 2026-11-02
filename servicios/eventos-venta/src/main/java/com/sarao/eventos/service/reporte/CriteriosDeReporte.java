package com.sarao.eventos.service.reporte;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import com.sarao.eventos.entity.EstadoEntrada;

// CU-14 paso 3: los criterios que estableció el Administrador. Todos son opcionales y un null no filtra (FA-01). Se
// guardan en reporte.criterios_filtro como clave=valor separados por ';': el modelo deja el formato abierto
// (modelo-diseno-y-datos.md 3) y así se leen a simple vista en la base
public record CriteriosDeReporte(LocalDate desde, LocalDate hasta, Long idEvento, Long idTipoEntrada,
		EstadoEntrada estado, Long idPersonal) {

	public static CriteriosDeReporte ninguno() {
		return new CriteriosDeReporte(null, null, null, null, null, null);
	}

	// Los que llegaron, en el orden de Criterio, para compararlos con los que admite el tipo de reporte (RF-4)
	public Set<Criterio> presentes() {
		Set<Criterio> presentes = EnumSet.noneOf(Criterio.class);
		for (Criterio criterio : Criterio.values()) {
			if (criterio.valorEn(this) != null) {
				presentes.add(criterio);
			}
		}
		return presentes;
	}

	// RF-5: el rango incluye los dos días, desde las 00:00 del primero hasta antes de las 00:00 del siguiente al último
	public LocalDateTime inicio() {
		return desde == null ? null : desde.atStartOfDay();
	}

	public LocalDateTime fin() {
		return hasta == null ? null : hasta.plusDays(1).atStartOfDay();
	}

	public String serializar() {
		StringJoiner texto = new StringJoiner(";");
		for (Criterio criterio : presentes()) {
			texto.add(criterio.campo() + "=" + criterio.valorEn(this));
		}
		return texto.toString();
	}

	public static CriteriosDeReporte leer(String texto) {
		Map<String, String> valores = new HashMap<>();
		if (texto != null && !texto.isEmpty()) {
			for (String par : texto.split(";")) {
				String[] partes = par.split("=", 2);
				valores.put(partes[0], partes[1]);
			}
		}
		return new CriteriosDeReporte(fecha(valores.get("desde")), fecha(valores.get("hasta")),
				numero(valores.get("idEvento")), numero(valores.get("idTipoEntrada")),
				valores.containsKey("estado") ? EstadoEntrada.valueOf(valores.get("estado")) : null,
				numero(valores.get("idPersonal")));
	}

	private static LocalDate fecha(String valor) {
		return valor == null ? null : LocalDate.parse(valor);
	}

	private static Long numero(String valor) {
		return valor == null ? null : Long.valueOf(valor);
	}
}
