package com.sarao.eventos.entity;

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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Tabla entrada (modelo-diseno-y-datos.md 2.6). codigo_qr es el que se escanea en la puerta (decisiones.md →
// codigo_qr significa dos cosas)
@Entity
// id_usuario no es llave foránea, así que MySQL no le crea el índice que necesita «Mis entradas»
@Table(name = "entrada", indexes = @Index(columnList = "id_usuario"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Entrada {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_entrada")
	private Long idEntrada;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_tipo_entrada", nullable = false)
	private TipoEntrada tipoEntrada;

	// La compra (decisiones.md → La entrada conoce su reserva)
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_reserva", nullable = false)
	private Reserva reserva;

	// Es un id: la cuenta vive en Seguridad
	@Column(name = "id_usuario", nullable = false)
	private Long idCliente;

	// Es un id: el pago vive en Pagos
	@Column(name = "id_pago", nullable = false)
	private Long idPago;

	@Column(name = "codigo_qr", nullable = false, unique = true, length = 255)
	private String codigoQrEntrada;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EstadoEntrada estado;

	@Column(name = "fecha_emision", nullable = false)
	private LocalDateTime fechaEmision;

	// CU-07 paso 6: una por cada unidad de la reserva, del tipo y el Cliente de la reserva
	public static Entrada emitir(Reserva reserva, Long idPago, String codigo, LocalDateTime ahora) {
		Entrada entrada = new Entrada();
		entrada.reserva = Objects.requireNonNull(reserva);
		entrada.idPago = Objects.requireNonNull(idPago);
		entrada.tipoEntrada = reserva.getTipoEntrada();
		entrada.idCliente = reserva.getIdCliente();
		entrada.codigoQrEntrada = Objects.requireNonNull(codigo);
		entrada.estado = EstadoEntrada.ACTIVA;
		entrada.fechaEmision = Objects.requireNonNull(ahora);
		return entrada;
	}

	// CU-08 RF-9: el QR se muestra solo si puede servir para entrar. La vigencia de la puerta (CU-15) mira además que
	// el evento esté activo y sea hoy
	public boolean muestraSuCodigo() {
		return estado == EstadoEntrada.ACTIVA && !tipoEntrada.getEvento().estaCancelado();
	}
}
