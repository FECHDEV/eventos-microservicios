package com.sarao.eventos.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.sarao.eventos.entity.Pagina;
import com.sarao.eventos.entity.VisitaPagina;
import com.sarao.eventos.repository.VisitaPaginaRepository;

// decisiones.md → «Contador de visitas por página»: una fila por página, que suma una visita cada vez que se abre
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ ContadorDeVisitas.class, PaginasDelContador.class })
class ContadorDeVisitasTest {

	@Autowired
	private TestEntityManager em;

	@Autowired
	private VisitaPaginaRepository repositorio;

	@Autowired
	private ContadorDeVisitas contador;

	@Autowired
	private PaginasDelContador paginas;

	@BeforeEach
	void prepararFilas() {
		paginas.run(new DefaultApplicationArguments());
		em.flush();
		em.clear();
	}

	@Test
	void alArrancarCreaUnaFilaPorPaginaEnCero() {
		Map<Pagina, Long> totales = totales();

		assertThat(totales).containsOnlyKeys(Pagina.values());
		assertThat(totales.values()).containsOnly(0L);
	}

	@Test
	void volverAArrancarNoDuplicaFilasNiBorraLasVisitas() {
		contador.registrarVisita(Pagina.PORTADA);
		em.flush();
		em.clear();

		paginas.run(new DefaultApplicationArguments());
		em.flush();
		em.clear();

		assertThat(repositorio.count()).isEqualTo(Pagina.values().length);
		assertThat(totales()).containsEntry(Pagina.PORTADA, 1L);
	}

	@Test
	void cadaVisitaSumaUnaYDevuelveElTotalDeEsaPagina() {
		assertThat(contador.registrarVisita(Pagina.PORTADA)).isEqualTo(1);
		assertThat(contador.registrarVisita(Pagina.PORTADA)).isEqualTo(2);
		assertThat(contador.registrarVisita(Pagina.DETALLE_DE_EVENTO)).isEqualTo(1);
		em.flush();
		em.clear();

		assertThat(totales()).containsEntry(Pagina.PORTADA, 2L).containsEntry(Pagina.DETALLE_DE_EVENTO, 1L)
				.containsEntry(Pagina.REPORTES, 0L);
	}

	// Una página nueva del enum (se agregó una pantalla) recibe su fila en el siguiente arranque
	@Test
	void unaPaginaSinFilaLaRecibeAlArrancar() {
		repositorio.deleteAll(repositorio.findAll().stream().filter(v -> v.getPagina() == Pagina.VALIDACION).toList());
		em.flush();
		em.clear();

		paginas.run(new DefaultApplicationArguments());
		em.flush();
		em.clear();

		assertThat(totales()).containsEntry(Pagina.VALIDACION, 0L);
	}

	private Map<Pagina, Long> totales() {
		return repositorio.findAll().stream().collect(Collectors.toMap(VisitaPagina::getPagina, VisitaPagina::getTotal));
	}

	// Sin su fila (la tabla se vació con la aplicación andando) la visita la crea, en vez de fallar
	@Test
	void unaPaginaSinFilaLaCreaConLaPrimeraVisita() {
		repositorio.deleteAll(repositorio.findAll().stream().filter(v -> v.getPagina() == Pagina.REPORTES).toList());
		em.flush();
		em.clear();

		assertThat(contador.registrarVisita(Pagina.REPORTES)).isEqualTo(1);
		assertThat(contador.registrarVisita(Pagina.REPORTES)).isEqualTo(2);
		em.flush();
		em.clear();

		assertThat(repositorio.count()).isEqualTo(Pagina.values().length);
		assertThat(totales()).containsEntry(Pagina.REPORTES, 2L);
	}
}
