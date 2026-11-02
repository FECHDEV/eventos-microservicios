package com.sarao.eventos.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sarao.eventos.entity.Reporte;

public interface ReporteRepository extends JpaRepository<Reporte, Long> {
}
