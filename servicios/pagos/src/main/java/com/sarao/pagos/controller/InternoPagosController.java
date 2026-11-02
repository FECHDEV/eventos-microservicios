package com.sarao.pagos.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sarao.pagos.dto.PagoCobradoResponse;
import com.sarao.pagos.repository.PagoRepository;
import lombok.RequiredArgsConstructor;

// Solo para otros servicios: SecurityConfig exige la clave de servicio sin usuario, y el gateway no publica /interno
@RestController
@RequestMapping("/interno/pagos")
@RequiredArgsConstructor
public class InternoPagosController {

	private final PagoRepository pagoRepository;

	// CU-14: los pagos confirmados por fecha de pago; con conDevolucion, también los tardíos sin cupo. Un null no
	// filtra; hasta es exclusivo
	@GetMapping("/cobrados")
	public List<PagoCobradoResponse> cobrados(@RequestParam boolean conDevolucion,
			@RequestParam(required = false) LocalDateTime desde, @RequestParam(required = false) LocalDateTime hasta) {
		return pagoRepository.cobrados(conDevolucion, desde, hasta).stream().map(PagoCobradoResponse::from).toList();
	}
}
