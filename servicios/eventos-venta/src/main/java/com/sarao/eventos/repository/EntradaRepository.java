package com.sarao.eventos.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sarao.eventos.entity.Entrada;

public interface EntradaRepository extends JpaRepository<Entrada, Long> {

	// CU-06 RF-10 con CU-07 RF-21: el límite cuenta las entradas emitidas del Cliente en el evento, salvo las devueltas
	@Query("""
			select count(e) from Entrada e
			where e.idCliente = :idCliente
			  and e.tipoEntrada.evento.idEvento = :idEvento
			  and e.estado <> com.sarao.eventos.entity.EstadoEntrada.DEVUELTA
			""")
	long cantidadEmitidaAlCliente(@Param("idCliente") Long idCliente, @Param("idEvento") Long idEvento);

	// CU-08: las entradas del Cliente con su compra, su tipo y su evento, en una sola consulta
	@Query("""
			select e from Entrada e join fetch e.reserva join fetch e.tipoEntrada t join fetch t.evento
			where e.idCliente = :idCliente
			order by e.idEntrada
			""")
	List<Entrada> delCliente(@Param("idCliente") Long idCliente);

	// CU-08 RF-2: la de otro Cliente no se encuentra, igual que una inexistente
	@Query("""
			select e from Entrada e join fetch e.reserva join fetch e.tipoEntrada t join fetch t.evento
			where e.idEntrada = :idEntrada and e.idCliente = :idCliente
			""")
	Optional<Entrada> buscarDelCliente(@Param("idEntrada") Long idEntrada, @Param("idCliente") Long idCliente);

	// CU-08 RF-8: para numerar una entrada dentro de su compra
	@Query("select e.idEntrada from Entrada e where e.reserva.idReserva = :idReserva order by e.idEntrada")
	List<Long> idsDeLaCompra(@Param("idReserva") Long idReserva);

	// CU-12 FA-02: al volver a activar un evento postergado, sus entradas usadas vuelven a ACTIVA. Cumple el papel de
	// Entrada.reactivar() del modelo, en bloque. Llega al evento por el tipo: la entrada no tiene FK a evento. Solo
	// escribe antes lo pendiente (la activación); no vacía el contexto, que en activar no tiene entradas cargadas y sí
	// el evento bloqueado
	@Modifying(flushAutomatically = true)
	@Query("""
			update Entrada e set e.estado = com.sarao.eventos.entity.EstadoEntrada.ACTIVA
			where e.estado = com.sarao.eventos.entity.EstadoEntrada.UTILIZADA
			  and e.tipoEntrada.idTipoEntrada in
			    (select t.idTipoEntrada from TipoEntrada t where t.evento.idEvento = :idEvento)
			""")
	int reactivarUtilizadasDelEvento(@Param("idEvento") Long idEvento);
}
