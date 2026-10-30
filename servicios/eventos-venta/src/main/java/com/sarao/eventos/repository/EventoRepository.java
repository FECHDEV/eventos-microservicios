package com.sarao.eventos.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sarao.eventos.entity.EstadoEvento;
import com.sarao.eventos.entity.Evento;
import jakarta.persistence.LockModeType;

public interface EventoRepository extends JpaRepository<Evento, Long> {

	// Un estado null no filtra; el orden llega en la paginación
	@Query("select e from Evento e where (:estado is null or e.estado = :estado)")
	Page<Evento> buscar(@Param("estado") EstadoEvento estado, Pageable paginacion);

	// Candidatos del cierre automático (CU-12 RF-20): solo los ids, porque cada evento se vuelve a leer con bloqueo
	// antes de cerrarle la venta. El fin exacto lo decide Evento.termino, que depende de si el evento cruza la
	// medianoche; acotar por fecha evita traer los eventos futuros
	@Query("select e.idEvento from Evento e where e.ventaAbierta = true and e.fecha <= :limite")
	List<Long> idsConLaVentaAbiertaHasta(@Param("limite") LocalDate limite);

	// Las operaciones que escriben un evento se serializan: sin esto, la que termina última reescribe todas las
	// columnas, incluida una imagen que la otra acaba de reemplazar y cuyo archivo ya se borró
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Evento> findConBloqueoByIdEvento(Long idEvento);
}
