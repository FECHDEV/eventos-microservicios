package com.sarao.eventos.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import com.sarao.eventos.entity.Entrada;
import com.sarao.eventos.entity.EstadoEntrada;
import com.sarao.eventos.entity.Reserva;

// CU-13 RF-4: una compra del evento con las entradas de un estado. La cantidad es la de toda la compra. El cliente
// viene de Seguridad: puede faltar si no responde con esa cuenta
public record CompraParaDevolucion(Reserva reserva, Cuenta cliente, int cantidad, List<Numerada> entradas) {

	// Una entrada con su número dentro de la compra, como la ve el Cliente en «Mis entradas»
	public record Numerada(Entrada entrada, int numero) {
	}

	// Las entradas de una misma compra, en orden; vacío si ninguna tiene ese estado
	static Optional<CompraParaDevolucion> de(List<Entrada> deLaCompra, Cuenta cliente, EstadoEntrada estado) {
		List<Numerada> elegidas = IntStream.range(0, deLaCompra.size())
				.filter(i -> deLaCompra.get(i).getEstado() == estado)
				.mapToObj(i -> new Numerada(deLaCompra.get(i), i + 1)).toList();
		if (elegidas.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new CompraParaDevolucion(deLaCompra.get(0).getReserva(), cliente, deLaCompra.size(),
				elegidas));
	}

	public Entrada primera() {
		return entradas.get(0).entrada();
	}
}
