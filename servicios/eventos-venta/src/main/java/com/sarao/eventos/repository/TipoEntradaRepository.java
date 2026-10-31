package com.sarao.eventos.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

	// CU-06: el evento de un tipo, sin cargar el tipo, para bloquear primero el evento (el orden es evento y después tipo)
	@Query("select t.evento.idEvento from TipoEntrada t where t.idTipoEntrada = :idTipoEntrada")
	Optional<Long> idEventoDe(@Param("idTipoEntrada") Long idTipoEntrada);

	// CU-06 FA-04: suma en la base, sobre el valor que tenga la fila y no sobre el que la transacción leyó antes. Vacía
	// el contexto: un tipo ya cargado queda viejo
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update TipoEntrada t set t.cantidadDisponible = t.cantidadDisponible + :cantidad
			where t.idTipoEntrada = :idTipoEntrada
			""")
	int devolverDisponibilidad(@Param("idTipoEntrada") Long idTipoEntrada, @Param("cantidad") int cantidad);
}
