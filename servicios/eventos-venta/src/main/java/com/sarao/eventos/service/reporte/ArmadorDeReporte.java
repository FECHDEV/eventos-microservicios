package com.sarao.eventos.service.reporte;

import java.util.Set;

import com.sarao.eventos.entity.TipoReporte;

// CU-14 pasos 2 a 5 para un tipo de reporte: qué criterios admite y cómo se arma con ellos
public interface ArmadorDeReporte {

	TipoReporte tipo();

	Set<Criterio> criteriosPermitidos();

	ContenidoReporte armar(CriteriosDeReporte criterios);
}
