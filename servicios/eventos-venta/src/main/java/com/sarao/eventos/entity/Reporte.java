package com.sarao.eventos.entity;

import java.time.LocalDateTime;
import java.util.Objects;

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

// Tabla reporte (modelo-diseno-y-datos.md 2.8): un reporte generado, con su tipo, sus criterios y la fecha
// (CLAUDE.md → Reportes). El contenido no se guarda: exportar lo vuelve a armar con estos criterios
@Entity
@Table(name = "reporte")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reporte {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_reporte")
	private Long idReporte;

	// El Administrador que lo generó. Es un id: la cuenta vive en Seguridad
	@Column(name = "id_usuario", nullable = false)
	private Long idAdministrador;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TipoReporte tipo;

	// Formato de CriteriosDeReporte.serializar
	@Column(name = "criterios_filtro", columnDefinition = "TEXT")
	private String criteriosFiltro;

	@Column(name = "fecha_generacion", nullable = false)
	private LocalDateTime fechaGeneracion;

	public static Reporte registrar(Long idAdministrador, TipoReporte tipo, String criteriosFiltro,
			LocalDateTime fechaGeneracion) {
		Reporte reporte = new Reporte();
		reporte.idAdministrador = Objects.requireNonNull(idAdministrador);
		reporte.tipo = Objects.requireNonNull(tipo);
		reporte.criteriosFiltro = criteriosFiltro;
		reporte.fechaGeneracion = Objects.requireNonNull(fechaGeneracion);
		return reporte;
	}
}
