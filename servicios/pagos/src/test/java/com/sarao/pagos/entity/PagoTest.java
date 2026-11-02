package com.sarao.pagos.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

// CU-07: estados del pago y lo que ve el Cliente (RF-3, RF-8 a RF-11, RF-13, RF-16)
class PagoTest {

	private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 2, 20, 0);
	private static final LocalDateTime VENCE = AHORA.plusMinutes(10);

	private static Pago pendiente() {
		return Pago.generar(40L, 7L, new BigDecimal("100.00"), "SIM-1", "QR", null, VENCE);
	}

	@Test
	void naceVigenteYPendienteSinDevolucion() {
		Pago pago = pendiente();

		assertThat(pago.getEstado()).isEqualTo(EstadoPago.PENDIENTE);
		assertThat(pago.isRequiereDevolucion()).isFalse();
		assertThat(pago.isConfirmacionPendiente()).isFalse();
		assertThat(pago.estadoReservaAl(AHORA)).isEqualTo(EstadoReserva.VIGENTE);
		assertThat(pago.segundosRestantes(AHORA)).isEqualTo(600);
		assertThat(pago.esDe(7L)).isTrue();
		assertThat(pago.esDe(8L)).isFalse();
	}

	// RF-10: vencido se ve EXPIRADO aunque todavía no se haya marcado
	@Test
	void unPendienteVencidoSeVeExpirado() {
		Pago pago = pendiente();

		assertThat(pago.estadoAl(VENCE)).isEqualTo(EstadoPago.EXPIRADO);
		assertThat(pago.estadoReservaAl(VENCE)).isEqualTo(EstadoReserva.EXPIRADA);
		assertThat(pago.segundosRestantes(VENCE)).isZero();
	}

	// «Compra confirmada» solo con las entradas emitidas: mientras tanto se sigue esperando
	@Test
	void unPagadoSinConfirmarSigueEsperando() {
		Pago pago = pendiente();
		pago.registrarPago(AHORA);

		assertThat(pago.isConfirmacionPendiente()).isTrue();
		assertThat(pago.porConciliar()).isTrue();
		assertThat(pago.estadoAl(AHORA)).isEqualTo(EstadoPago.PENDIENTE);
		assertThat(pago.estadoAl(VENCE)).isEqualTo(EstadoPago.EXPIRADO);

		pago.resolverConfirmacion(true);

		assertThat(pago.porConciliar()).isFalse();
		assertThat(pago.estadoAl(VENCE)).isEqualTo(EstadoPago.PAGADO);
		assertThat(pago.estadoReservaAl(VENCE)).isEqualTo(EstadoReserva.CONFIRMADA);
		assertThat(pago.getFechaPago()).isEqualTo(AHORA);
	}

	// RF-16: pago tardío sin cupo
	@Test
	void sinCupoQuedaParaDevolucion() {
		Pago pago = pendiente();
		pago.registrarPago(VENCE.plusMinutes(1));
		pago.resolverConfirmacion(false);

		assertThat(pago.isRequiereDevolucion()).isTrue();
		assertThat(pago.estadoAl(AHORA)).isEqualTo(EstadoPago.PAGADO);
		assertThat(pago.estadoReservaAl(AHORA)).isEqualTo(EstadoReserva.EXPIRADA);
		assertThatThrownBy(() -> pago.resolverConfirmacion(true)).isInstanceOf(IllegalStateException.class);
	}

	// RF-9 y RF-13: un resultado se aplica una sola vez
	@Test
	void unPagoProcesadoNoCambia() {
		Pago conError = pendiente();
		conError.registrarError();

		assertThat(conError.estadoAl(AHORA)).isEqualTo(EstadoPago.ERROR);
		assertThat(conError.estadoReservaAl(AHORA)).isEqualTo(EstadoReserva.EXPIRADA);
		assertThat(conError.porConciliar()).isFalse();
		assertThatThrownBy(() -> conError.registrarPago(AHORA)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> conError.resolverConfirmacion(true)).isInstanceOf(IllegalStateException.class);

		Pago pagado = pendiente();
		pagado.registrarPago(AHORA);
		assertThatThrownBy(pagado::registrarError).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> pagado.registrarPago(AHORA)).isInstanceOf(IllegalStateException.class);
	}
}
