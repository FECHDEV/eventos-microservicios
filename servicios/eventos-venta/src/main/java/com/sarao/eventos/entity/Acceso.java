package com.sarao.eventos.entity;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Tabla acceso (modelo-diseno-y-datos.md 2.7). Una entrada puede tener varios (decisiones.md → Varios accesos por
// entrada)
@Entity
@Table(name = "acceso")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Acceso {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_acceso")
	private Long idAcceso;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_entrada", nullable = false)
	private Entrada entrada;

	// El Personal autorizado que validó. Es un id: la cuenta vive en Seguridad
	@Column(name = "id_usuario", nullable = false)
	private Long idPersonal;

	@Column(name = "fecha_hora", nullable = false)
	private LocalDateTime fechaHora;

	// CU-15 paso 5
	public static Acceso registrar(Entrada entrada, Long idPersonal, LocalDateTime ahora) {
		Acceso acceso = new Acceso();
		acceso.entrada = Objects.requireNonNull(entrada);
		acceso.idPersonal = Objects.requireNonNull(idPersonal);
		acceso.fechaHora = Objects.requireNonNull(ahora);
		return acceso;
	}
}
