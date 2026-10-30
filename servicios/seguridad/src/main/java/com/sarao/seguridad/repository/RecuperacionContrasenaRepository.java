package com.sarao.seguridad.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.sarao.seguridad.entity.RecuperacionContrasena;
import com.sarao.seguridad.entity.Usuario;
import jakarta.persistence.LockModeType;

public interface RecuperacionContrasenaRepository extends JpaRepository<RecuperacionContrasena, Long> {

	// Dos restablecimientos simultáneos con el mismo token no pueden usarlo dos veces
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<RecuperacionContrasena> findByHashToken(String hashToken);

	List<RecuperacionContrasena> findByUsuarioAndUsadoFalse(Usuario usuario);
}
