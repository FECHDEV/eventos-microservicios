package com.sarao.eventos.service.reporte;

import java.util.function.Function;

// CU-14 paso 2: cada criterio de filtro, con el nombre de su campo en la API y en criterios_filtro
public enum Criterio {
	DESDE("desde", CriteriosDeReporte::desde),
	HASTA("hasta", CriteriosDeReporte::hasta),
	EVENTO("idEvento", CriteriosDeReporte::idEvento),
	TIPO_ENTRADA("idTipoEntrada", CriteriosDeReporte::idTipoEntrada),
	ESTADO("estado", CriteriosDeReporte::estado),
	PERSONAL("idPersonal", CriteriosDeReporte::idPersonal);

	private final String campo;
	private final Function<CriteriosDeReporte, Object> valor;

	Criterio(String campo, Function<CriteriosDeReporte, Object> valor) {
		this.campo = campo;
		this.valor = valor;
	}

	public String campo() {
		return campo;
	}

	Object valorEn(CriteriosDeReporte criterios) {
		return valor.apply(criterios);
	}
}
