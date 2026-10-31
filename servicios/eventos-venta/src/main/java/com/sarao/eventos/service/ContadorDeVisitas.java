package com.sarao.eventos.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sarao.eventos.entity.Pagina;
import com.sarao.eventos.repository.VisitaPaginaRepository;
import lombok.RequiredArgsConstructor;

// El contador del pie de cada página (decisiones.md → «Contador de visitas por página»). Las filas las crea
// PaginasDelContador al arrancar
@Service
@RequiredArgsConstructor
public class ContadorDeVisitas {

	private final VisitaPaginaRepository repositorio;

	// Lee el total dentro de la misma transacción que lo sumó: es el de esta visita, aunque haya otras a la vez. Si la
	// fila falta (la tabla se vació con la aplicación andando), la crea en vez de fallar
	@Transactional
	public long registrarVisita(Pagina pagina) {
		if (repositorio.sumarVisita(pagina.name()) == 0) {
			repositorio.crearSiFalta(pagina.name());
			repositorio.sumarVisita(pagina.name());
		}
		return repositorio.totalDe(pagina.name()).orElseThrow();
	}
}
