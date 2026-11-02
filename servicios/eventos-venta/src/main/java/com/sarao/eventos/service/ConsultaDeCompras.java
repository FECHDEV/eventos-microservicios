package com.sarao.eventos.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sarao.eventos.entity.Reserva;
import com.sarao.eventos.repository.EntradaRepository;
import com.sarao.eventos.repository.ReservaRepository;
import lombok.RequiredArgsConstructor;

// Lo que llevan los correos de una compra ya confirmada (CorreosDeCompra)
@Service
@RequiredArgsConstructor
public class ConsultaDeCompras {

	private final ReservaRepository reservaRepository;
	private final EntradaRepository entradaRepository;

	public record Compra(Reserva reserva, List<String> codigos) {
	}

	@Transactional(readOnly = true)
	public Compra compra(Long idReserva) {
		Reserva reserva = reservaRepository.buscarConSuEvento(idReserva).orElseThrow();
		return new Compra(reserva, entradaRepository.codigosDeLaCompra(idReserva));
	}
}
