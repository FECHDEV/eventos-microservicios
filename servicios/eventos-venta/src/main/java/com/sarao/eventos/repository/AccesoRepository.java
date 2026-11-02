package com.sarao.eventos.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sarao.eventos.entity.Acceso;

public interface AccesoRepository extends JpaRepository<Acceso, Long> {

	// CU-14 RF-11: con la entrada, su tipo y su evento cargados. Un null no filtra; hasta es exclusivo
	@Query("""
			select a from Acceso a join fetch a.entrada en join fetch en.tipoEntrada t join fetch t.evento ev
			where (:idEvento is null or ev.idEvento = :idEvento)
			  and (:desde is null or a.fechaHora >= :desde)
			  and (:hasta is null or a.fechaHora < :hasta)
			  and (:idPersonal is null or a.idPersonal = :idPersonal)
			order by a.fechaHora, a.idAcceso
			""")
	List<Acceso> paraReporte(@Param("idEvento") Long idEvento, @Param("desde") LocalDateTime desde,
			@Param("hasta") LocalDateTime hasta, @Param("idPersonal") Long idPersonal);

	// CU-14 RF-2: el filtro de accesos ofrece a quienes validaron alguna vez, aunque hoy tengan otro rol
	@Query("select distinct a.idPersonal from Acceso a")
	List<Long> idsDelPersonal();
}
