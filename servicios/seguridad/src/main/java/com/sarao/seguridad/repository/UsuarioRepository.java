package com.sarao.seguridad.repository;

import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.sarao.seguridad.entity.Rol;
import com.sarao.seguridad.entity.Usuario;
import jakarta.persistence.LockModeType;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	boolean existsByCorreo(String correo);

	Optional<Usuario> findByCorreo(String correo);

	boolean existsByRol(Rol rol);

	// Serializa las operaciones sobre la misma cuenta, como dos solicitudes de recuperación simultáneas
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Usuario> findConBloqueoByCorreo(String correo);

	// Dos cambios de contraseña simultáneos: el segundo espera y compara contra el hash que dejó el primero
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Usuario> findConBloqueoByIdUsuario(Long idUsuario);

	// correo es la única restricción UNIQUE de la tabla usuario
	static boolean esCorreoDuplicado(DataIntegrityViolationException ex) {
		for (Throwable causa = ex.getCause(); causa != null; causa = causa.getCause()) {
			if (causa instanceof ConstraintViolationException violacion) {
				return violacion.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE;
			}
		}
		return false;
	}
}
