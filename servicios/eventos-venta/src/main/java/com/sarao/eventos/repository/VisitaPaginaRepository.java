package com.sarao.eventos.repository;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sarao.eventos.entity.Pagina;
import com.sarao.eventos.entity.VisitaPagina;

public interface VisitaPaginaRepository extends JpaRepository<VisitaPagina, Long> {

	@Query("select v.pagina from VisitaPagina v")
	Set<Pagina> paginasRegistradas();

	// Suma en la base, con la fila bloqueada hasta el final de la transacción: dos visitas a la vez no se pisan
	@Modifying
	@Query("update VisitaPagina v set v.total = v.total + 1 where v.pagina = :pagina")
	int sumarVisita(@Param("pagina") Pagina pagina);

	@Query("select v.total from VisitaPagina v where v.pagina = :pagina")
	Optional<Long> totalDe(@Param("pagina") Pagina pagina);
}
