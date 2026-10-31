package com.sarao.eventos.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sarao.eventos.entity.Entrada;
import com.sarao.eventos.exception.EntradaNoEncontradaException;
import com.sarao.eventos.repository.EntradaRepository;
import lombok.RequiredArgsConstructor;

// CU-08: las entradas del Cliente agrupadas por compra, y el detalle de una. Solo el Cliente llega (SecurityConfig), y
// solo a las suyas: el Cliente sale de la sesión. Una reserva sin entradas no es una compra confirmada y no aparece
@Service
@RequiredArgsConstructor
public class ConsultaDeEntradas {

	private static final Comparator<CompraDelCliente> POR_FECHA = Comparator
			.comparing((CompraDelCliente compra) -> compra.evento().getFecha())
			.thenComparing(compra -> compra.evento().getHoraInicio());
	// En el mismo evento, siempre por orden de compra
	private static final Comparator<CompraDelCliente> POR_COMPRA = Comparator
			.comparing(compra -> compra.reserva().getIdReserva());

	private final EntradaRepository entradaRepository;
	private final Clock clock;

	@Transactional(readOnly = true)
	public EntradasDelCliente delCliente(Long idCliente) {
		LocalDateTime ahora = LocalDateTime.now(clock);
		Map<Long, List<Entrada>> porCompra = new LinkedHashMap<>();
		for (Entrada entrada : entradaRepository.delCliente(idCliente)) {
			porCompra.computeIfAbsent(entrada.getReserva().getIdReserva(), id -> new ArrayList<>()).add(entrada);
		}
		Map<Boolean, List<CompraDelCliente>> porCancelacion = porCompra.values().stream().map(CompraDelCliente::de)
				.collect(Collectors.partitioningBy(compra -> compra.evento().estaCancelado()));
		Map<Boolean, List<CompraDelCliente>> porTermino = porCancelacion.get(false).stream()
				.collect(Collectors.partitioningBy(compra -> compra.evento().termino(ahora)));
		return new EntradasDelCliente(porTermino.get(false).stream().sorted(POR_FECHA.thenComparing(POR_COMPRA)).toList(),
				porTermino.get(true).stream().sorted(POR_FECHA.reversed().thenComparing(POR_COMPRA)).toList(),
				porCancelacion.get(true).stream().sorted(POR_FECHA.reversed().thenComparing(POR_COMPRA)).toList());
	}

	@Transactional(readOnly = true)
	public DetalleEntrada detalle(Long idCliente, Long idEntrada) {
		Entrada entrada = entradaRepository.buscarDelCliente(idEntrada, idCliente)
				.orElseThrow(EntradaNoEncontradaException::new);
		List<Long> deLaCompra = entradaRepository.idsDeLaCompra(entrada.getReserva().getIdReserva());
		return new DetalleEntrada(entrada, deLaCompra.indexOf(entrada.getIdEntrada()) + 1, deLaCompra.size());
	}
}
