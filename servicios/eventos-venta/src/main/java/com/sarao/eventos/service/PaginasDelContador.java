package com.sarao.eventos.service;

import java.util.Arrays;
import java.util.Set;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sarao.eventos.entity.Pagina;
import com.sarao.eventos.entity.VisitaPagina;
import com.sarao.eventos.repository.VisitaPaginaRepository;
import lombok.RequiredArgsConstructor;

// Crea al arrancar la fila de cada página que todavía no la tiene (una pantalla nueva), así una visita siempre es un
// update: dos primeras visitas a la vez no compiten por insertar la fila (decisiones.md → «Contador de visitas por
// página»). Supone una sola instancia del servicio
@Service
@RequiredArgsConstructor
public class PaginasDelContador implements ApplicationRunner {

	private final VisitaPaginaRepository repositorio;

	@Override
	@Transactional
	public void run(ApplicationArguments argumentos) {
		Set<Pagina> registradas = repositorio.paginasRegistradas();
		Arrays.stream(Pagina.values()).filter(pagina -> !registradas.contains(pagina))
				.forEach(pagina -> repositorio.save(VisitaPagina.nueva(pagina)));
	}
}
