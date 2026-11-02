package com.sarao.pagos.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.sarao.pagos.entity.Pago;
import jakarta.persistence.LockModeType;

public interface PagoRepository extends JpaRepository<Pago, Long> {

	Optional<Pago> findByIdReserva(Long idReserva);

	// CU-07 RF-13: con el pago bloqueado se vuelve a mirar si ya estaba procesado
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Pago> findConBloqueoByIdPago(Long idPago);

	// El aviso del sistema de pago trae nuestra referencia (PagoService.procesarAviso)
	@Query("select p.idPago from Pago p where p.referenciaExterna = :referencia")
	Optional<Long> idPorReferencia(@Param("referencia") String referencia);

	// La lectura previa a la consulta al sistema de pago, sin cargar la entidad ni bloquear. Vacío si ya está procesado
	@Query("""
			select p.referenciaExterna from Pago p
			where p.idPago = :idPago
			  and p.estado in (com.sarao.pagos.entity.EstadoPago.PENDIENTE, com.sarao.pagos.entity.EstadoPago.EXPIRADO)
			""")
	Optional<String> referenciaSinProcesar(@Param("idPago") Long idPago);

	// CU-07 RF-10: condicionada, así no pisa un resultado que otra verificación acaba de registrar
	@Transactional
	@Modifying
	@Query("""
			update Pago p set p.estado = com.sarao.pagos.entity.EstadoPago.EXPIRADO
			where p.idPago = :idPago and p.estado = com.sarao.pagos.entity.EstadoPago.PENDIENTE
			  and p.fechaExpiracion <= :ahora
			""")
	int expirarSiVencio(@Param("idPago") Long idPago, @Param("ahora") LocalDateTime ahora);

	// CU-07 RF-20: los pendientes y los expirados que todavía pueden cobrarse (la deuda del sistema de pago vence por
	// día), y los pagados que esperan la confirmación, sin ventana: no se pierde una compra cobrada
	@Query("""
			select p.idPago from Pago p
			where (p.estado in (com.sarao.pagos.entity.EstadoPago.PENDIENTE, com.sarao.pagos.entity.EstadoPago.EXPIRADO)
			       and p.fechaExpiracion >= :desde)
			   or p.confirmacionPendiente = true
			order by p.idPago
			""")
	List<Long> idsPorConciliar(@Param("desde") LocalDateTime desde);

	// CU-14 RF-7 y RF-8: PAGADO y ya confirmado; uno que espera la confirmación se resuelve en el minuto (plan de HU-20)
	@Query("""
			select p from Pago p
			where p.estado = com.sarao.pagos.entity.EstadoPago.PAGADO and p.confirmacionPendiente = false
			  and (:conDevolucion = true or p.requiereDevolucion = false)
			  and (:desde is null or p.fechaPago >= :desde)
			  and (:hasta is null or p.fechaPago < :hasta)
			order by p.fechaPago, p.idPago
			""")
	List<Pago> cobrados(@Param("conDevolucion") boolean conDevolucion, @Param("desde") LocalDateTime desde,
			@Param("hasta") LocalDateTime hasta);
}
