package com.sarao.eventos.entity;

import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

	// VARCHAR y no ENUM de MySQL: con ddl-auto=update, una página nueva no cabría en la columna ya creada
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, unique = true, length = 40)
	private Pagina pagina;

	@Column(nullable = false)
	private long total;

	public static VisitaPagina nueva(Pagina pagina) {
		VisitaPagina visita = new VisitaPagina();
		visita.pagina = Objects.requireNonNull(pagina);
		visita.total = 0;
		return visita;
	}
}
