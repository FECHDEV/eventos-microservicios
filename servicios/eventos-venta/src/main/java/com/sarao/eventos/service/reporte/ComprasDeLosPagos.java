package com.sarao.eventos.service.reporte;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.sarao.eventos.entity.Reserva;
import com.sarao.eventos.repository.ReservaRepository;
import com.sarao.eventos.service.PagoCobrado;
import lombok.RequiredArgsConstructor;

// Cruza los pagos de Pagos con sus reservas, que son las que saben el evento y el tipo. Pagos filtra por fecha; el
// evento y el tipo se filtran acá (plan de HU-20 → Decisiones)
@Component
@RequiredArgsConstructor
class ComprasDeLosPagos {

	record Compra(PagoCobrado pago, Reserva reserva) {
	}

	private final ReservaRepository reservaRepository;

	// En el orden en que llegan los pagos: por fecha de pago. Un pago sin reserva conocida no se muestra
	List<Compra> filtrar(List<PagoCobrado> pagos, Long idEvento, Long idTipoEntrada) {
		if (pagos.isEmpty()) {
			return List.of();
		}
		Map<Long, Reserva> reservas = reservaRepository
				.conTipoYEvento(pagos.stream().map(PagoCobrado::idReserva).collect(Collectors.toSet())).stream()
				.collect(Collectors.toMap(Reserva::getIdReserva, Function.identity()));
		return pagos.stream().filter(pago -> reservas.containsKey(pago.idReserva()))
				.map(pago -> new Compra(pago, reservas.get(pago.idReserva())))
				.filter(compra -> idEvento == null
						|| compra.reserva().getTipoEntrada().getEvento().getIdEvento().equals(idEvento))
				.filter(compra -> idTipoEntrada == null
						|| compra.reserva().getTipoEntrada().getIdTipoEntrada().equals(idTipoEntrada))
				.toList();
	}

	static Set<Long> clientes(List<Compra> compras) {
		return compras.stream().map(compra -> compra.reserva().getIdCliente()).collect(Collectors.toSet());
	}
}
