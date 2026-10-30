package com.sarao.seguridad.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class RegistroClienteRequestTest {

	private static final LocalDate NACIMIENTO = LocalDate.of(2000, 5, 20);

	private static ValidatorFactory factory;
	private static Validator validator;

	@BeforeAll
	static void crearValidador() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void cerrarValidador() {
		factory.close();
	}

	private static Set<String> camposInvalidos(RegistroClienteRequest solicitud) {
		return validator.validate(solicitud).stream()
				.map(violacion -> violacion.getPropertyPath().toString())
				.collect(Collectors.toSet());
	}

	private static RegistroClienteRequest conContrasena(String contrasena) {
		return new RegistroClienteRequest("Ana", "Rojas", "ana@mail.com", contrasena, null, NACIMIENTO);
	}

	private static RegistroClienteRequest conFechaNacimiento(LocalDate fechaNacimiento) {
		return new RegistroClienteRequest("Ana", "Rojas", "ana@mail.com", "Secreta12", null, fechaNacimiento);
	}

	// 4 + 60 + 1 + 4 = 69 caracteres fijos; ninguna etiqueta supera los 63 que admite @Email
	private static String correoDeLongitud(int longitud) {
		return "ana@" + "a".repeat(60) + "." + "b".repeat(longitud - 69) + ".com";
	}

	@Test
	void solicitudCompletaEsValida() {
		var solicitud = new RegistroClienteRequest("Ana", "Rojas", "ana@mail.com", "Secreta12", "70000000", NACIMIENTO);
		assertThat(camposInvalidos(solicitud)).isEmpty();
	}

	@Test
	void faltanTodosLosObligatoriosYSeIndicanTodos() {
		var solicitud = new RegistroClienteRequest(null, null, null, null, null, null);
		assertThat(camposInvalidos(solicitud))
				.containsExactlyInAnyOrder("nombre", "apellido", "correo", "contrasena", "fechaNacimiento");
	}

	@Test
	void camposConSoloEspaciosSonIncompletos() {
		var solicitud = new RegistroClienteRequest("   ", "   ", "   ", "Secreta12", null, NACIMIENTO);
		assertThat(camposInvalidos(solicitud)).containsExactlyInAnyOrder("nombre", "apellido", "correo");
	}

	@Test
	void correoSinFormatoEsInvalido() {
		var solicitud = new RegistroClienteRequest("Ana", "Rojas", "ana-sin-arroba", "Secreta12", null, NACIMIENTO);
		assertThat(camposInvalidos(solicitud)).containsExactly("correo");
	}

	@Test
	void correoSeNormalizaAntesDeValidar() {
		var solicitud = new RegistroClienteRequest("Ana", "Rojas", "  Ana@Mail.com ", "Secreta12", null, NACIMIENTO);
		assertThat(solicitud.correo()).isEqualTo("ana@mail.com");
		assertThat(camposInvalidos(solicitud)).isEmpty();
	}

	@Test
	void limiteDelCorreoSeAplicaAlValorNormalizado() {
		// "İ" (U+0130) ocupa 2 caracteres en minúsculas: 100 caracteres de entrada quedan en 140
		var correo = "İ".repeat(40) + "@" + "a".repeat(55) + ".com";
		var solicitud = new RegistroClienteRequest("Ana", "Rojas", correo, "Secreta12", null, NACIMIENTO);
		assertThat(camposInvalidos(solicitud)).containsExactly("correo");
	}

	@Test
	void contrasenaDe7CaracteresEsInvalidaYDe8Valida() {
		assertThat(camposInvalidos(conContrasena("Abcdef1"))).containsExactly("contrasena");
		assertThat(camposInvalidos(conContrasena("Abcdef12"))).isEmpty();
	}

	@Test
	void contrasenaDe72BytesEsValidaYDe73Invalida() {
		assertThat(camposInvalidos(conContrasena("Aa1" + "a".repeat(69)))).isEmpty();
		assertThat(camposInvalidos(conContrasena("Aa1" + "a".repeat(70)))).containsExactly("contrasena");
	}

	@Test
	void caracteresMultibyteCuentanPorBytesParaElMaximo() {
		// "ñ" ocupa 2 bytes en UTF-8: 3 + 68 = 71 bytes, 3 + 70 = 73 bytes
		assertThat(camposInvalidos(conContrasena("Aa1" + "ñ".repeat(34)))).isEmpty();
		assertThat(camposInvalidos(conContrasena("Aa1" + "ñ".repeat(35)))).containsExactly("contrasena");
	}

	// CU-01 RF-16
	@Test
	void contrasenaSinMayusculaSinMinusculaOSinNumeroEsInvalida() {
		assertThat(camposInvalidos(conContrasena("secreta12"))).containsExactly("contrasena");
		assertThat(camposInvalidos(conContrasena("SECRETA12"))).containsExactly("contrasena");
		assertThat(camposInvalidos(conContrasena("Secretaaa"))).containsExactly("contrasena");
	}

	@Test
	void fechaDeNacimientoFuturaEsInvalidaYLaDeHoyValida() {
		assertThat(camposInvalidos(conFechaNacimiento(LocalDate.now().plusDays(1)))).containsExactly("fechaNacimiento");
		assertThat(camposInvalidos(conFechaNacimiento(LocalDate.now()))).isEmpty();
	}

	@Test
	void longitudesMaximasDeLaTablaUsuario() {
		var enElLimite = new RegistroClienteRequest("a".repeat(80), "a".repeat(80), correoDeLongitud(120),
				"Secreta12", "1".repeat(20), NACIMIENTO);
		var excedida = new RegistroClienteRequest("a".repeat(81), "a".repeat(81), correoDeLongitud(121),
				"Secreta12", "1".repeat(21), NACIMIENTO);

		assertThat(camposInvalidos(enElLimite)).isEmpty();
		assertThat(camposInvalidos(excedida)).containsExactlyInAnyOrder("nombre", "apellido", "correo", "telefono");
	}

	@Test
	void telefonoEsOpcional() {
		var solicitud = new RegistroClienteRequest("Ana", "Rojas", "ana@mail.com", "Secreta12", null, NACIMIENTO);
		assertThat(camposInvalidos(solicitud)).isEmpty();
	}
}
