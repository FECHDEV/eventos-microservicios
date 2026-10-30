package com.sarao.seguridad.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "usuario")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Usuario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_usuario")
	private Long idUsuario;

	@Column(nullable = false, length = 80)
	private String nombre;

	@Column(nullable = false, length = 80)
	private String apellido;

	// Binaria: con la colación de la base, josé@mail.com y jose@mail.com serían la misma cuenta. El valor ya llega normalizado
	@Column(nullable = false, unique = true, columnDefinition = "varchar(120) character set utf8mb4 collate utf8mb4_bin")
	private String correo;

	@Column(nullable = false, length = 255)
	private String contrasena;

	@Column(length = 20)
	private String telefono;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Rol rol;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EstadoCuenta estado;

	@Column(name = "fecha_nacimiento")
	private LocalDate fechaNacimiento;

	@Column(name = "fecha_registro", nullable = false)
	private LocalDateTime fechaRegistro;

	public static Usuario registrarCliente(String nombre, String apellido, String correo, String contrasenaHash,
			String telefono, LocalDate fechaNacimiento, LocalDateTime fechaRegistro) {
		Usuario usuario = new Usuario();
		usuario.nombre = Objects.requireNonNull(nombre);
		usuario.apellido = Objects.requireNonNull(apellido);
		usuario.correo = normalizarCorreo(Objects.requireNonNull(correo));
		usuario.contrasena = Objects.requireNonNull(contrasenaHash);
		usuario.telefono = telefonoONulo(telefono);
		usuario.fechaNacimiento = Objects.requireNonNull(fechaNacimiento, "fecha_nacimiento es obligatoria para un CLIENTE");
		usuario.fechaRegistro = Objects.requireNonNull(fechaRegistro);
		usuario.rol = Rol.CLIENTE;
		usuario.estado = EstadoCuenta.ACTIVA;
		return usuario;
	}

	public static Usuario crearAdministrador(String nombre, String apellido, String correo, String contrasenaHash,
			LocalDateTime fechaRegistro) {
		Usuario usuario = new Usuario();
		usuario.nombre = Objects.requireNonNull(nombre);
		usuario.apellido = Objects.requireNonNull(apellido);
		usuario.correo = normalizarCorreo(Objects.requireNonNull(correo));
		usuario.contrasena = Objects.requireNonNull(contrasenaHash);
		usuario.fechaRegistro = Objects.requireNonNull(fechaRegistro);
		usuario.rol = Rol.ADMINISTRADOR;
		usuario.estado = EstadoCuenta.ACTIVA;
		return usuario;
	}

	public static String normalizarCorreo(String correo) {
		return correo.strip().toLowerCase(Locale.ROOT);
	}

	public boolean estaActiva() {
		return estado == EstadoCuenta.ACTIVA;
	}

	public void cambiarContrasena(String contrasenaHash) {
		contrasena = Objects.requireNonNull(contrasenaHash);
	}

	public void asignarRol(Rol rol) {
		this.rol = Objects.requireNonNull(rol);
	}

	public void desactivar() {
		estado = EstadoCuenta.DESACTIVADA;
	}

	private static String telefonoONulo(String telefono) {
		return telefono == null || telefono.isBlank() ? null : telefono;
	}
}
