package com.sarao.eventos.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.sarao.eventos.entity.TipoEntrada;
import jakarta.persistence.LockModeType;

public interface TipoEntradaRepository extends JpaRepository<TipoEntrada, Long> {

	List<TipoEntrada> findByEvento_IdEventoOrderByIdTipoEntrada(Long idEvento);

	boolean existsByEvento_IdEventoAndNombreIgnoreCase(Long idEvento, String nombre);

	boolean existsByEvento_IdEventoAndNombreIgnoreCaseAndIdTipoEntradaNot(Long idEvento, String nombre,
			Long idTipoEntrada);

	// Un evento se activa solo si tiene al menos un tipo de entrada (CU-12 RF-6)
	boolean existsByEvento_IdEvento(Long idEvento);

	// Un cambio de cantidad y una reserva (CU-06) sobre el mismo tipo se esperan en vez de pisar la disponible
	// (decisiones.md → Concurrencia sobre el último cupo)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<TipoEntrada> findConBloqueoByIdTipoEntradaAndEvento_IdEvento(Long idTipoEntrada, Long idEvento);
}
