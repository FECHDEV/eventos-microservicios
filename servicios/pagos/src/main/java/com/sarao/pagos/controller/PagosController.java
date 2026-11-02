package com.sarao.pagos.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sarao.pagos.dto.PagoResponse;
import com.sarao.pagos.entity.Pago;
import com.sarao.pagos.service.PagoService;

// CU-07. Solo el Cliente (SecurityConfig), y solo el dueño de la reserva (PagoService). El Cliente sale de la sesión
// (IdentidadDelGatewayFiltro), nunca de la petición
@RestController
@RequestMapping("/api/reservas/{idReserva}/pago")
public class PagosController {

	private final PagoService pagoService;
	private final boolean simulador;

	public PagosController(PagoService pagoService, @Value("${eventos.pago.proveedor}") String proveedor) {
		this.pagoService = pagoService;
		this.simulador = "simulador".equals(proveedor);
	}

	// Genera el cobro, o devuelve el que ya tiene (RF-3, RF-4)
	@PostMapping
	public PagoResponse pagar(@AuthenticationPrincipal Long idUsuario, @PathVariable Long idReserva) {
		return respuesta(pagoService.pagar(idUsuario, idReserva));
	}

	// Pregunta al sistema de pago y devuelve cómo quedó (RF-7)
	@GetMapping
	public PagoResponse consultar(@AuthenticationPrincipal Long idUsuario, @PathVariable Long idReserva) {
		return respuesta(pagoService.verificar(idUsuario, idReserva));
	}

	private PagoResponse respuesta(Pago pago) {
		return PagoResponse.from(pago, pagoService.ahora(), simulador);
	}
}
