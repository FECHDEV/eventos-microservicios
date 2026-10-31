package com.sarao.eventos.entity;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

// Tabla reserva (modelo-diseno-y-datos.md 2.4). Reglas en decisiones.md → Venta y → Adquirir entradas (CU-06). La
// liberación por vencimiento no pasa por acá: es una actualización en bloque (LiberacionDeReservas)
@Entity
@Table(name = "reserva")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reserva {

	// CLAUDE.md → Compra: la reserva temporal dura 10 minutos
	public static final Duration DURACION = Duration.ofMinutes(10);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_reserva")
	private Long idReserva;

	// Es un id: la cuenta vive en Seguridad
	@Column(name = "id_usuario", nullable = false)
	private Long idCliente;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_tipo_entrada", nullable = false)
	private TipoEntrada tipoEntrada;

	@Column(nullable = false)
	private int cantidad;

	// Copiado del tipo al reservar (decisiones.md → Precio congelado en reserva.precio_unitario)
	@Column(name = "precio_unitario", nullable = false, precision = 10, scale = 2)
	private BigDecimal precioUnitario;

	@Column(name = "fecha_creacion", nullable = false)
	private LocalDateTime fechaCreacion;

	@Column(name = "fecha_expiracion", nullable = false)
	private LocalDateTime fechaExpiracion;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EstadoReserva estado;

	public static Reserva crear(Long idCliente, TipoEntrada tipo, int cantidad, LocalDateTime ahora) {
		if (cantidad < 1) {
			throw new IllegalArgumentException("La cantidad debe ser de al menos 1");
		}
		Reserva reserva = new Reserva();
		reserva.idCliente = Objects.requireNonNull(idCliente);
		reserva.tipoEntrada = Objects.requireNonNull(tipo);
		reserva.cantidad = cantidad;
		reserva.precioUnitario = tipo.getPrecio();
		reserva.fechaCreacion = Objects.requireNonNull(ahora);
		reserva.fechaExpiracion = ahora.plus(DURACION);
		reserva.estado = EstadoReserva.VIGENTE;
		return reserva;
	}

	// Vencida desde el instante exacto de su expiración (CU-06 RF-18), aunque el barrido todavía no la haya marcado
	public boolean estaVigente(LocalDateTime ahora) {
		return estado == EstadoReserva.VIGENTE && ahora.isBefore(fechaExpiracion);
	}

	// Lo que ve el Cliente: una VIGENTE cuyo plazo ya pasó está vencida
	public EstadoReserva estadoAl(LocalDateTime ahora) {
		return estado == EstadoReserva.VIGENTE && !estaVigente(ahora) ? EstadoReserva.EXPIRADA : estado;
	}

	public long segundosRestantes(LocalDateTime ahora) {
		return estaVigente(ahora) ? Duration.between(ahora, fechaExpiracion).toSeconds() : 0;
	}

	public BigDecimal total() {
		return precioUnitario.multiply(BigDecimal.valueOf(cantidad));
	}
}
