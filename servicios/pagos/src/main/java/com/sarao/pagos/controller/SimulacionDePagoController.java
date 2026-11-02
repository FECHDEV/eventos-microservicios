package com.sarao.pagos.controller;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sarao.pagos.dto.SimulacionRequest;
import com.sarao.pagos.exception.CamposInvalidosException;
import com.sarao.pagos.exception.CamposInvalidosException.CampoInvalido;
import com.sarao.pagos.service.PagoService;
import com.sarao.pagos.service.pago.EstadoDelCobro;
import com.sarao.pagos.service.pago.SimuladorDePago;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// CU-07 RF-22: herramienta de pruebas. Solo existe con el simulador; con otro proveedor esta ruta no está
@RestController
@RequestMapping("/api/reservas/{idReserva}/pago/simulacion")
@ConditionalOnProperty(name = "eventos.pago.proveedor", havingValue = "simulador", matchIfMissing = true)
@RequiredArgsConstructor
public class SimulacionDePagoController {

	private final PagoService pagoService;
	private final SimuladorDePago simuladorDePago;

	@PostMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void simular(@AuthenticationPrincipal Long idUsuario, @PathVariable Long idReserva,
			@Valid @RequestBody SimulacionRequest solicitud) {
		if (solicitud.resultado() == EstadoDelCobro.PENDIENTE) {
			throw new CamposInvalidosException(List.of(new CampoInvalido("resultado", "tiene que ser PAGADO o ERROR")));
		}
		simuladorDePago.forzar(pagoService.referenciaParaSimular(idUsuario, idReserva), solicitud.resultado());
	}
}
