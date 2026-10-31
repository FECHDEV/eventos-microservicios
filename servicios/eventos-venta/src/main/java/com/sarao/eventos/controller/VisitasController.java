package com.sarao.eventos.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sarao.eventos.dto.VisitasResponse;
import com.sarao.eventos.entity.Pagina;
import com.sarao.eventos.service.ContadorDeVisitas;
import lombok.RequiredArgsConstructor;

// El pie de cada pantalla, con o sin sesión (SecurityConfig). Una página fuera del enum sale como 400
@RestController
@RequestMapping("/api/visitas")
@RequiredArgsConstructor
public class VisitasController {

	private final ContadorDeVisitas contador;

	@PostMapping("/{pagina}")
	public VisitasResponse registrar(@PathVariable Pagina pagina) {
		return new VisitasResponse(contador.registrarVisita(pagina));
	}
}
