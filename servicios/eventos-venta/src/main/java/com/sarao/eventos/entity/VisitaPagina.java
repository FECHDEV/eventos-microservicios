package com.sarao.eventos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// El contador de una página (decisiones.md → «Contador de visitas por página»). Amplía el modelo de datos: no está
// en la Fase 4. El total se suma en la base (VisitaPaginaRepository.sumarVisita), nunca leyendo y guardando
@Entity
@Table(name = "visita_pagina")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisitaPagina {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_visita_pagina")
	private Long idVisitaPagina;

	// Texto y no enum: Hibernate pondría un CHECK con los valores, y con ddl-auto=update una página nueva no cabría
	@Column(nullable = false, unique = true, length = 40)
	private String pagina;

	@Column(nullable = false)
	private long total;
}
