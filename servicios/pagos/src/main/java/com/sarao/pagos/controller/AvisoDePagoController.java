package com.sarao.pagos.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sarao.pagos.service.PagoService;
import lombok.RequiredArgsConstructor;

// El sistema de pago avisa que una deuda se pagó (su callback_url). Público (SecurityConfig): el
// aviso no confirma nada, solo adelanta la consulta al sistema de pago. Siempre 200, exista o no la referencia
@RestController
@RequestMapping("/api/pagos/aviso")
@RequiredArgsConstructor
public class AvisoDePagoController {

	private final PagoService pagoService;

	@RequestMapping(method = { RequestMethod.GET, RequestMethod.POST })
	public ResponseEntity<Void> avisar(@RequestParam(required = false) String referencia) {
		pagoService.procesarAviso(referencia);
		return ResponseEntity.ok().build();
	}
}
