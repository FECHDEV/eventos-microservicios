package com.sarao.eventos.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sarao.eventos.entity.EstadoReserva;
import com.sarao.eventos.entity.Reserva;

// La reserva llega al evento por su tipo: no tiene FK a evento (decisiones.md → reserva y entrada no tienen FK a evento)
public interface ReservaRepository extends JpaRepository<Reserva, Long> {

	boolean existsByTipoEntrada_Evento_IdEventoAndEstadoAndFechaExpiracionLessThanEqual(Long idEvento,
			EstadoReserva estado, LocalDateTime ahora);

	// Sin bloquear: la liberación toma el bloqueo del evento solo si hay algo que liberar
	default boolean hayVencidas(Long idEvento, LocalDateTime ahora) {
		return existsByTipoEntrada_Evento_IdEventoAndEstadoAndFechaExpiracionLessThanEqual(idEvento,
				EstadoReserva.VIGENTE, ahora);
	}

	// En orden de id: dos barridos del mismo evento toman las filas en el mismo orden
	@Query("""
			select new com.sarao.eventos.repository.ReservaVencida(r.idReserva, r.tipoEntrada.idTipoEntrada, r.cantidad)
			from Reserva r
			where r.tipoEntrada.evento.idEvento = :idEvento
			  and r.estado = com.sarao.eventos.entity.EstadoReserva.VIGENTE
			  and r.fechaExpiracion <= :ahora
			order by r.idReserva
			""")
	List<ReservaVencida> vencidasDelEvento(@Param("idEvento") Long idEvento, @Param("ahora") LocalDateTime ahora);

	// Condicionada al estado: de dos transacciones que expiran la misma reserva, solo una recibe 1 y devuelve su
	// cantidad (CU-06 RF-19). Vacía el contexto: lo que ya se había leído de la reserva queda viejo
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Reserva r set r.estado = com.sarao.eventos.entity.EstadoReserva.EXPIRADA
			where r.idReserva = :idReserva and r.estado = com.sarao.eventos.entity.EstadoReserva.VIGENTE
			""")
	int expirar(@Param("idReserva") Long idReserva);

	// Vigentes por plazo, no solo por estado: una vencida sin marcar no cuenta (CU-06 RF-18). Las entradas emitidas se
	// suman aparte (EntradaRepository.cantidadEmitidaAlCliente, CU-07 RF-21)
	@Query("""
			select coalesce(sum(r.cantidad), 0) from Reserva r
			where r.idCliente = :idCliente
			  and r.tipoEntrada.evento.idEvento = :idEvento
			  and r.estado = com.sarao.eventos.entity.EstadoReserva.VIGENTE
			  and r.fechaExpiracion > :ahora
			""")
	long cantidadVigenteDelCliente(@Param("idCliente") Long idCliente, @Param("idEvento") Long idEvento,
			@Param("ahora") LocalDateTime ahora);

	// Otro cliente recibe lo mismo que una reserva inexistente (CU-06 RF-20)
	@Query("""
			select r.tipoEntrada.evento.idEvento from Reserva r
			where r.idReserva = :idReserva and r.idCliente = :idCliente
			""")
	Optional<Long> idEventoDeLaReserva(@Param("idReserva") Long idReserva, @Param("idCliente") Long idCliente);

	// Con el tipo y el evento, que la respuesta muestra fuera de la transacción
	@Query("""
			select r from Reserva r join fetch r.tipoEntrada t join fetch t.evento
			where r.idReserva = :idReserva and r.idCliente = :idCliente
			""")
	Optional<Reserva> buscarDelCliente(@Param("idReserva") Long idReserva, @Param("idCliente") Long idCliente);
}
