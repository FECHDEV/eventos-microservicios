package com.sarao.eventos.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sarao.eventos.entity.Entrada;
import com.sarao.eventos.entity.EstadoEntrada;
import jakarta.persistence.LockModeType;

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

	// CU-07 RF-17: los códigos de una compra, para su correo
	@Query("select e.codigoQrEntrada from Entrada e where e.reserva.idReserva = :idReserva order by e.idEntrada")
	List<String> codigosDeLaCompra(@Param("idReserva") Long idReserva);

	// CU-15 RF-5: sin cargar la entrada, que se relee con bloqueo (ValidacionDeEntradas)
	@Query("""
			select new com.sarao.eventos.repository.EntradaPorCodigo(e.idEntrada, e.tipoEntrada.evento.idEvento)
			from Entrada e where e.codigoQrEntrada = :codigo
			""")
	Optional<EntradaPorCodigo> buscarPorCodigo(@Param("codigo") String codigo);

	// CU-15 RF-11: dos validaciones de la misma entrada se serializan
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Entrada> findConBloqueoByIdEntrada(Long idEntrada);

	// CU-14 RF-8: cuántas entradas devueltas tiene cada pago. No se llama con la lista vacía
	@Query("""
			select new com.sarao.eventos.repository.CantidadPorId(e.idPago, count(e)) from Entrada e
			where e.estado = com.sarao.eventos.entity.EstadoEntrada.DEVUELTA and e.idPago in :idsPago
			group by e.idPago
			""")
	List<CantidadPorId> devueltasPorPago(@Param("idsPago") Collection<Long> idsPago);

	// CU-14 RF-9: sin el código de la entrada en ninguna parte del reporte (RNF-03)
	@Query("""
			select e from Entrada e join fetch e.tipoEntrada t join fetch t.evento ev
			where (:idEvento is null or ev.idEvento = :idEvento)
			  and (:idTipo is null or t.idTipoEntrada = :idTipo)
			  and (:estado is null or e.estado = :estado)
			order by ev.fecha, ev.idEvento, t.idTipoEntrada, e.idEntrada
			""")
	List<Entrada> paraReporte(@Param("idEvento") Long idEvento, @Param("idTipo") Long idTipo,
			@Param("estado") EstadoEntrada estado);

	// CU-14 RF-10: una compra es un pago con entradas; con evento, solo las de ese evento
	@Query("""
			select new com.sarao.eventos.repository.ComprasDelCliente(e.idCliente, count(distinct e.idPago), count(e))
			from Entrada e
			where (:idEvento is null or e.tipoEntrada.evento.idEvento = :idEvento)
			group by e.idCliente
			""")
	List<ComprasDelCliente> comprasPorCliente(@Param("idEvento") Long idEvento);

	// CU-13 RF-2: las activas de cada evento cancelado, que son las pendientes de devolución
	@Query("""
			select new com.sarao.eventos.repository.PendientesDelEvento(t.evento.idEvento, count(e))
			from Entrada e join e.tipoEntrada t
			where t.evento.estado = com.sarao.eventos.entity.EstadoEvento.CANCELADO
			  and e.estado = com.sarao.eventos.entity.EstadoEntrada.ACTIVA
			group by t.evento.idEvento
			""")
	List<PendientesDelEvento> pendientesDeLosCancelados();

	// CU-13 RF-4 a RF-6: todas las entradas del evento, por compra, con su reserva. Se traen también las usadas para
	// numerar cada entrada dentro de su compra
	@Query("""
			select e from Entrada e join fetch e.reserva r join fetch e.tipoEntrada t
			where t.evento.idEvento = :idEvento
			order by r.idReserva, e.idEntrada
			""")
	List<Entrada> delEventoConSuCompra(@Param("idEvento") Long idEvento);

	// CU-13: el evento de una compra con entradas; una reserva sin entradas no es una compra
	@Query("select distinct e.tipoEntrada.evento.idEvento from Entrada e where e.reserva.idReserva = :idReserva")
	Optional<Long> idEventoDeLaCompra(@Param("idReserva") Long idReserva);

	@Query("select e.tipoEntrada.evento.idEvento from Entrada e where e.idEntrada = :idEntrada")
	Optional<Long> idEventoDeLaEntrada(@Param("idEntrada") Long idEntrada);

	// CU-13 RF-11 y RF-12: una compra sin activas, ¿ya se devolvió o solo tiene usadas?
	@Query("""
			select count(e) > 0 from Entrada e
			where e.reserva.idReserva = :idReserva
			  and e.estado = com.sarao.eventos.entity.EstadoEntrada.DEVUELTA
			""")
	boolean hayDevueltasEnLaCompra(@Param("idReserva") Long idReserva);

	@Query("select e.estado from Entrada e where e.idEntrada = :idEntrada")
	Optional<EstadoEntrada> estadoDe(@Param("idEntrada") Long idEntrada);

	// CU-13 RF-9: cumple el papel de Entrada.marcarDevuelta() para toda la compra. Condicionada a ACTIVA: devuelve 0 si
	// otra petición ya la registró (FA-02, RF-13)
	@Modifying
	@Query("""
			update Entrada e set e.estado = com.sarao.eventos.entity.EstadoEntrada.DEVUELTA
			where e.estado = com.sarao.eventos.entity.EstadoEntrada.ACTIVA and e.reserva.idReserva = :idReserva
			""")
	int devolverLasActivasDeLaCompra(@Param("idReserva") Long idReserva);

	// CU-13 RF-10: una sola entrada, con la misma condición
	@Modifying
	@Query("""
			update Entrada e set e.estado = com.sarao.eventos.entity.EstadoEntrada.DEVUELTA
			where e.idEntrada = :idEntrada and e.estado = com.sarao.eventos.entity.EstadoEntrada.ACTIVA
			""")
	int devolver(@Param("idEntrada") Long idEntrada);

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
