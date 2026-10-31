package com.sarao.eventos.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sarao.eventos.entity.VisitaPagina;

public interface VisitaPaginaRepository extends JpaRepository<VisitaPagina, Long> {

	// Sin conflicto si otra visita la creó a la vez, y sin leer filas de páginas que ya no están en el enum
	@Modifying
	@Query(value = "insert ignore into visita_pagina (pagina, total) values (:pagina, 0)", nativeQuery = true)
	int crearSiFalta(@Param("pagina") String pagina);

	// Suma en la base, con la fila bloqueada hasta el final de la transacción: dos visitas a la vez no se pisan
	@Modifying
	@Query("update VisitaPagina v set v.total = v.total + 1 where v.pagina = :pagina")
	int sumarVisita(@Param("pagina") String pagina);

	@Query("select v.total from VisitaPagina v where v.pagina = :pagina")
	Optional<Long> totalDe(@Param("pagina") String pagina);
}
