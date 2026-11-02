package com.sarao.pagos.entity;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Tabla pago (modelo-diseno-y-datos.md 2.5). codigo_qr es el QR de cobro, no el de la entrada (decisiones.md →
// codigo_qr significa dos cosas). La reserva vive en otra base: id_reserva es un id, y el pago copia lo que necesita
// de ella (plan de HU-18 → Decisiones)
@Entity
// El aviso del sistema de pago busca por la referencia (PagoRepository.idPorReferencia)
@Table(name = "pago", indexes = @Index(columnList = "referencia_externa"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pago {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_pago")
	private Long idPago;

	// Relación 1 a 0..1: un solo cobro por reserva (CU-07 RF-4)
	@Column(name = "id_reserva", nullable = false, unique = true)
	private Long idReserva;

	// El dueño de la reserva: solo él paga y consulta (RF-2)
	@Column(name = "id_usuario", nullable = false)
	private Long idCliente;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal monto;

	// El texto de un QR interoperable puede pasar de 255 caracteres
	@Column(name = "codigo_qr", nullable = false, length = 1000)
	private String codigoQrPago;

	@Column(name = "url_pasarela", length = 255)
	private String urlPasarela;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EstadoPago estado;

	@Column(name = "fecha_pago")
	private LocalDateTime fechaPago;

	// El identificador de la transacción en el sistema de pago
	@Column(name = "referencia_externa", length = 120)
	private String referenciaExterna;

	// Pago tardío sin cupo: se devuelve fuera del sistema (decisiones.md → Pago tardío)
	@Column(name = "requiere_devolucion", nullable = false)
	private boolean requiereDevolucion;

	// El vencimiento de la reserva, que no cambia: con él se marca EXPIRADO (RF-10)
	@Column(name = "fecha_expiracion", nullable = false)
	private LocalDateTime fechaExpiracion;

	// PAGADO, pero Eventos y Venta todavía no respondió si emitió las entradas: se repite hasta saberlo
	@Column(name = "confirmacion_pendiente", nullable = false)
	private boolean confirmacionPendiente;

	public static Pago generar(Long idReserva, Long idCliente, BigDecimal monto, String referencia, String codigoQr,
			String urlPasarela, LocalDateTime fechaExpiracion) {
		Pago pago = new Pago();
		pago.idReserva = Objects.requireNonNull(idReserva);
		pago.idCliente = Objects.requireNonNull(idCliente);
		pago.monto = Objects.requireNonNull(monto);
		pago.referenciaExterna = Objects.requireNonNull(referencia);
		pago.codigoQrPago = Objects.requireNonNull(codigoQr);
		pago.urlPasarela = urlPasarela;
		pago.fechaExpiracion = Objects.requireNonNull(fechaExpiracion);
		pago.estado = EstadoPago.PENDIENTE;
		return pago;
	}

	// PAGADO o ERROR: el sistema de pago ya dio su resultado y no se vuelve a aplicar (CU-07 RF-13)
	public boolean estaProcesado() {
		return estado == EstadoPago.PAGADO || estado == EstadoPago.ERROR;
	}

	// Falta preguntar al sistema de pago, o falta saber si se emitieron las entradas
	public boolean porConciliar() {
		return !estaProcesado() || confirmacionPendiente;
	}

	public boolean esDe(Long idCliente) {
		return this.idCliente.equals(idCliente);
	}

	public void registrarPago(LocalDateTime fecha) {
		exigirSinProcesar();
		estado = EstadoPago.PAGADO;
		fechaPago = Objects.requireNonNull(fecha);
		confirmacionPendiente = true;
	}

	public void registrarError() {
		exigirSinProcesar();
		estado = EstadoPago.ERROR;
	}

	// La respuesta de Eventos y Venta: entradas emitidas, o un pago tardío sin cupo que se devuelve (RF-16)
	public void resolverConfirmacion(boolean confirmada) {
		if (estado != EstadoPago.PAGADO || !confirmacionPendiente) {
			throw new IllegalStateException("El pago no espera confirmación");
		}
		confirmacionPendiente = false;
		requiereDevolucion = !confirmada;
	}

	private boolean confirmado() {
		return estado == EstadoPago.PAGADO && !confirmacionPendiente && !requiereDevolucion;
	}

	private boolean vigenteAl(LocalDateTime ahora) {
		return ahora.isBefore(fechaExpiracion);
	}

	// Lo que ve el Cliente. Un PENDIENTE vencido está EXPIRADO aunque todavía no se haya marcado, y un PAGADO sin
	// confirmar sigue esperando: «Compra confirmada» se muestra solo con las entradas emitidas
	public EstadoPago estadoAl(LocalDateTime ahora) {
		boolean esperando = estado == EstadoPago.PENDIENTE || estado == EstadoPago.PAGADO && confirmacionPendiente;
		if (esperando) {
			return vigenteAl(ahora) ? EstadoPago.PENDIENTE : EstadoPago.EXPIRADO;
		}
		return estado;
	}

	public EstadoReserva estadoReservaAl(LocalDateTime ahora) {
		if (confirmado()) {
			return EstadoReserva.CONFIRMADA;
		}
		return estado != EstadoPago.ERROR && !requiereDevolucion && vigenteAl(ahora) ? EstadoReserva.VIGENTE
				: EstadoReserva.EXPIRADA;
	}

	public long segundosRestantes(LocalDateTime ahora) {
		return estadoReservaAl(ahora) == EstadoReserva.VIGENTE ? Duration.between(ahora, fechaExpiracion).toSeconds()
				: 0;
	}

	private void exigirSinProcesar() {
		if (estaProcesado()) {
			throw new IllegalStateException("El pago ya fue procesado");
		}
	}
}
