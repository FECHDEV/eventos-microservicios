package com.sarao.pagos.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.sarao.pagos.entity.EstadoReserva;
import com.sarao.pagos.entity.Pago;
import com.sarao.pagos.exception.ErrorSistemaPagoException;
import com.sarao.pagos.exception.PagoNoEncontradoException;
import com.sarao.pagos.exception.ReservaNoEncontradaException;
import com.sarao.pagos.exception.ReservaNoVigenteException;
import com.sarao.pagos.repository.PagoRepository;
import com.sarao.pagos.service.ConsultaDeReservas.ReservaParaCobrar;
import com.sarao.pagos.service.pago.CobroGenerado;
import com.sarao.pagos.service.pago.IPago;
import com.sarao.pagos.service.pago.SistemaDePagoNoDisponibleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// CU-07 para el Cliente: el cobro de su reserva y la consulta de su pago. Aplicar el resultado es de ConciliacionDePagos
@Slf4j
@Service
@RequiredArgsConstructor
public class PagoService {

	// El largo de pago.referencia_externa
	private static final int LARGO_DE_LA_REFERENCIA = 120;

	private final PagoRepository pagoRepository;
	private final ConsultaDeReservas consultaDeReservas;
	private final ConciliacionDePagos conciliacionDePagos;
	private final IPago iPago;
	private final Clock clock;

	// RF-3 a RF-6. Sin transacción ni bloqueos: ni Eventos y Venta ni el sistema de pago se llaman con filas tomadas.
	// Un cobro por reserva lo garantiza el UNIQUE de id_reserva: si otra pestaña guardó el suyo durante la llamada, gana
	// ese, y el que se generó acá nunca se muestra, así que nadie puede pagarlo
	public Pago pagar(Long idCliente, Long idReserva) {
		Optional<Pago> existente = pagoRepository.findByIdReserva(idReserva);
		if (existente.isPresent()) {
			return delDueno(existente.get(), idCliente);
		}
		ReservaParaCobrar reserva = consultaDeReservas.paraCobrar(idReserva, idCliente);
		if (reserva.estado() != EstadoReserva.VIGENTE || reserva.segundosRestantes() <= 0) {
			throw new ReservaNoVigenteException();
		}
		LocalDateTime vence = LocalDateTime.now(clock).plusSeconds(reserva.segundosRestantes());
		CobroGenerado cobro;
		try {
			cobro = iPago.generarCobro(idReserva, reserva.total(), reserva.concepto());
		} catch (SistemaDePagoNoDisponibleException error) {
			log.warn("No se pudo generar el cobro de la reserva {}", idReserva, error);
			// Otra pestaña pudo tener más suerte: entonces la reserva no se libera
			Optional<Pago> otro = pagoRepository.findByIdReserva(idReserva);
			if (otro.isPresent()) {
				return otro.get();
			}
			consultaDeReservas.liberar(idReserva);
			throw new ErrorSistemaPagoException();
		}
		// Venció durante la llamada: su cupo ya volvió, y un cobro nuevo no se muestra (RF-5)
		if (!LocalDateTime.now(clock).isBefore(vence)) {
			throw new ReservaNoVigenteException();
		}
		try {
			return pagoRepository.saveAndFlush(Pago.generar(idReserva, idCliente, reserva.total(), cobro.referencia(),
					cobro.codigoQr(), cobro.urlPasarela(), vence));
		} catch (DataIntegrityViolationException carrera) {
			return pagoRepository.findByIdReserva(idReserva).orElseThrow(() -> carrera);
		}
	}

	// RF-7: pregunta al sistema de pago, si todavía hace falta, y devuelve cómo quedó
	public Pago verificar(Long idCliente, Long idReserva) {
		Pago pago = propio(idCliente, idReserva);
		if (pago.porConciliar()) {
			conciliacionDePagos.conciliar(pago.getIdPago());
			return pagoRepository.findById(pago.getIdPago()).orElseThrow();
		}
		return pago;
	}

	// El aviso del sistema de pago no confirma nada: adelanta la misma consulta que hacen la pantalla y la tarea de cada
	// minuto. Una referencia que no es nuestra no llega a consultar
	public void procesarAviso(String referencia) {
		if (referencia == null || referencia.isBlank() || referencia.length() > LARGO_DE_LA_REFERENCIA) {
			return;
		}
		try {
			pagoRepository.idPorReferencia(referencia).ifPresent(conciliacionDePagos::conciliar);
		} catch (RuntimeException error) {
			// La pantalla y la tarea de cada minuto vuelven a consultar: el proveedor no tiene que reintentar
			log.warn("No se pudo procesar el aviso de pago {}: {}", referencia, error.getClass().getSimpleName());
		}
	}

	// Solo el dueño simula el pago de su reserva (RF-22)
	public String referenciaParaSimular(Long idCliente, Long idReserva) {
		return propio(idCliente, idReserva).getReferenciaExterna();
	}

	public LocalDateTime ahora() {
		return LocalDateTime.now(clock);
	}

	// Uno ajeno responde igual que una reserva sin cobro: no revela nada (plan de HU-18 → Decisiones)
	private Pago propio(Long idCliente, Long idReserva) {
		return pagoRepository.findByIdReserva(idReserva).filter(pago -> pago.esDe(idCliente))
				.orElseThrow(PagoNoEncontradoException::new);
	}

	private static Pago delDueno(Pago pago, Long idCliente) {
		if (!pago.esDe(idCliente)) {
			throw new ReservaNoEncontradaException();
		}
		return pago;
	}
}
