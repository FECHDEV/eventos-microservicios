package com.sarao.eventos.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sarao.eventos.dto.ConfirmacionRequest;
import com.sarao.eventos.dto.ConfirmacionResponse;
import com.sarao.eventos.dto.ReservaParaCobrarResponse;
import com.sarao.eventos.service.ConfirmacionDeReservas;
import com.sarao.eventos.service.ReservaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// Solo para Pagos: SecurityConfig exige la clave de servicio sin usuario, y el gateway no publica /interno
@RestController
@RequestMapping("/interno/reservas/{idReserva}")
@RequiredArgsConstructor
public class InternoReservasController {

	private final ReservaService reservaService;
	private final ConfirmacionDeReservas confirmacionDeReservas;

	// La de otro Cliente responde 404, igual que una inexistente (CU-07 RF-2)
	@GetMapping
	public ReservaParaCobrarResponse paraCobrar(@PathVariable Long idReserva, @RequestParam Long cliente) {
		return ReservaParaCobrarResponse.from(reservaService.consultar(cliente, idReserva));
	}

	@PostMapping("/liberacion")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void liberar(@PathVariable Long idReserva) {
		confirmacionDeReservas.liberar(idReserva);
	}

	@PostMapping("/confirmacion")
	public ConfirmacionResponse confirmar(@PathVariable Long idReserva,
			@Valid @RequestBody ConfirmacionRequest solicitud) {
		return new ConfirmacionResponse(confirmacionDeReservas.confirmar(idReserva, solicitud.idPago()));
	}
}
