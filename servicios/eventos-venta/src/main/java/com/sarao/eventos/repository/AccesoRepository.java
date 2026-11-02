package com.sarao.eventos.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sarao.eventos.entity.Acceso;

public interface AccesoRepository extends JpaRepository<Acceso, Long> {
}
