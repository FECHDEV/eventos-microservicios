package com.sarao.seguridad.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.sarao.seguridad.entity.EstadoCuenta;
import com.sarao.seguridad.entity.Rol;
import com.sarao.seguridad.entity.Usuario;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UsuarioRepositoryTest {

	private static final LocalDate NACIMIENTO = LocalDate.of(2000, 5, 20);
	private static final LocalDateTime FECHA_REGISTRO = LocalDateTime.of(2026, 9, 12, 11, 0);

	@Autowired
	private TestEntityManager em;

	@Autowired
	private UsuarioRepository usuarioRepository;

	private static Usuario cliente(String correo) {
		return Usuario.registrarCliente("Ana", "Rojas", correo, "$2a$10$hash", null, NACIMIENTO, FECHA_REGISTRO);
	}

	@Test
	void guardaYLeeTodasLasColumnas() {
		Long id = em.persistAndGetId(cliente("ana@mail.com"), Long.class);
		em.flush();
		em.clear();

		Usuario leido = usuarioRepository.findById(id).orElseThrow();

		assertThat(leido.getNombre()).isEqualTo("Ana");
		assertThat(leido.getApellido()).isEqualTo("Rojas");
		assertThat(leido.getCorreo()).isEqualTo("ana@mail.com");
		assertThat(leido.getContrasena()).isEqualTo("$2a$10$hash");
		assertThat(leido.getTelefono()).isNull();
		assertThat(leido.getRol()).isEqualTo(Rol.CLIENTE);
		assertThat(leido.getEstado()).isEqualTo(EstadoCuenta.ACTIVA);
		assertThat(leido.getFechaNacimiento()).isEqualTo(NACIMIENTO);
		assertThat(leido.getFechaRegistro()).isEqualTo(FECHA_REGISTRO);
	}

	@Test
	void existsByCorreoDistingueCorreosRegistrados() {
		em.persist(cliente("ana@mail.com"));
		em.flush();
		em.clear();

		assertThat(usuarioRepository.existsByCorreo("ana@mail.com")).isTrue();
		assertThat(usuarioRepository.existsByCorreo("otra@mail.com")).isFalse();
	}

	@Test
	void correoRepetidoSeReconoceComoDuplicado() {
		em.persistAndFlush(cliente("ana@mail.com"));

		assertThatThrownBy(() -> usuarioRepository.saveAndFlush(cliente("ana@mail.com")))
				.isInstanceOfSatisfying(DataIntegrityViolationException.class,
						ex -> assertThat(UsuarioRepository.esCorreoDuplicado(ex)).isTrue());
	}

	@Test
	void datoDemasiadoLargoNoSeConfundeConCorreoDuplicado() {
		Usuario nombreLargo = Usuario.registrarCliente("a".repeat(81), "Rojas", "ana@mail.com", "$2a$10$hash", null,
				NACIMIENTO, FECHA_REGISTRO);

		assertThatThrownBy(() -> usuarioRepository.saveAndFlush(nombreLargo))
				.isInstanceOfSatisfying(DataIntegrityViolationException.class,
						ex -> assertThat(UsuarioRepository.esCorreoDuplicado(ex)).isFalse());
	}

	@Test
	void findByCorreoDevuelveLaCuentaRegistrada() {
		em.persist(cliente("ana@mail.com"));
		em.flush();
		em.clear();

		assertThat(usuarioRepository.findByCorreo("ana@mail.com"))
				.hasValueSatisfying(usuario -> assertThat(usuario.getNombre()).isEqualTo("Ana"));
		assertThat(usuarioRepository.findByCorreo("otra@mail.com")).isEmpty();
	}
}
