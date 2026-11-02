package com.sarao.pagos.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.sarao.pagos.entity.Pago;
import com.sarao.pagos.exception.ServicioNoDisponibleException;
import com.sarao.pagos.repository.PagoRepository;
import com.sarao.pagos.service.ConsultaDeReservas.ResultadoConfirmacion;
import com.sarao.pagos.service.pago.IPago;
import com.sarao.pagos.service.pago.ResultadoDelCobro;
import com.sarao.pagos.service.pago.SistemaDePagoNoDisponibleException;
import lombok.extern.slf4j.Slf4j;

// La única pieza que aplica un resultado del sistema de pago (CU-07 pasos 3 a 6, FA-01 a FA-03). La usan la consulta
// de la pantalla y la tarea de cada minuto. Dos pasos (decisiones.md → Microservicios: confirmación en dos pasos):
// registrar lo que dijo el sistema de pago y después pedir a Eventos y Venta que emita las entradas
@Slf4j
@Component
public class ConciliacionDePagos {

	private final PagoRepository pagoRepository;
	private final ConsultaDeReservas consultaDeReservas;
	private final IPago iPago;
	private final Clock clock;
	private final TransactionTemplate transaccion;

	public ConciliacionDePagos(PagoRepository pagoRepository, ConsultaDeReservas consultaDeReservas, IPago iPago,
			Clock clock, PlatformTransactionManager transactionManager) {
		this.pagoRepository = pagoRepository;
		this.consultaDeReservas = consultaDeReservas;
		this.iPago = iPago;
		this.clock = clock;
		this.transaccion = new TransactionTemplate(transactionManager);
		this.transaccion.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
	}

	public void conciliar(Long idPago) {
		Optional<String> referencia = pagoRepository.referenciaSinProcesar(idPago);
		if (referencia.isPresent() && !aplicarResultado(idPago, referencia.get())) {
			return;
		}
		confirmarSiFalta(idPago);
	}

	// Sin nada bloqueado durante la consulta: una llamada lenta no frena otra verificación. Falso si no hay nada más
	// que hacer (pendiente, error o el sistema de pago no respondió)
	private boolean aplicarResultado(Long idPago, String referencia) {
		ResultadoDelCobro resultado;
		try {
			resultado = iPago.consultar(referencia);
		} catch (SistemaDePagoNoDisponibleException error) {
			log.warn("No se pudo consultar el pago {}; se reintenta en la próxima verificación", idPago, error);
			return false;
		}
		switch (resultado.estado()) {
			case PENDIENTE -> {
				// RF-8 y RF-10
				pagoRepository.expirarSiVencio(idPago, LocalDateTime.now(clock));
				return false;
			}
			case ERROR -> {
				// RF-9: primero queda el error; la reserva se libera después, fuera de la transacción
				Long idReserva = registrar(idPago, Pago::registrarError);
				if (idReserva != null) {
					consultaDeReservas.liberar(idReserva);
				}
				return false;
			}
			case PAGADO -> {
				LocalDateTime fecha = resultado.fechaPago() == null ? LocalDateTime.now(clock) : resultado.fechaPago();
				registrar(idPago, pago -> pago.registrarPago(fecha));
				return true;
			}
			default -> throw new IllegalStateException("Resultado desconocido: " + resultado.estado());
		}
	}

	// Con el pago bloqueado: si otra verificación ya lo procesó, no hace nada (RF-13). Devuelve la reserva del pago, o
	// null si ya estaba procesado
	private Long registrar(Long idPago, Consumer<Pago> cambio) {
		return transaccion.execute(estado -> {
			Pago pago = pagoRepository.findConBloqueoByIdPago(idPago).orElseThrow();
			if (pago.estaProcesado()) {
				return null;
			}
			cambio.accept(pago);
			return pago.getIdReserva();
		});
	}

	// RF-11, RF-15 y RF-16. El pago queda bloqueado mientras Eventos y Venta responde: dos verificaciones no reciben
	// respuestas distintas (SIN_CUPO y después CONFIRMADA). Si falla, sigue pendiente y se repite en la próxima
	private void confirmarSiFalta(Long idPago) {
		try {
			transaccion.executeWithoutResult(estado -> {
				Pago pago = pagoRepository.findConBloqueoByIdPago(idPago).orElseThrow();
				if (!pago.isConfirmacionPendiente()) {
					return;
				}
				ResultadoConfirmacion resultado = consultaDeReservas.confirmar(pago.getIdReserva(), idPago);
				pago.resolverConfirmacion(resultado == ResultadoConfirmacion.CONFIRMADA);
				if (resultado == ResultadoConfirmacion.SIN_CUPO) {
					log.info("El pago {} llegó tarde y sin cupo: queda para devolución", idPago);
				}
			});
		} catch (ServicioNoDisponibleException error) {
			log.warn("No se pudo confirmar la compra del pago {}; se reintenta en la próxima verificación", idPago);
		}
	}
}
