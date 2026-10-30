package com.sarao.eventos.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class EventoRequestTest {

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

	private static EventoRequest valido() {
		return new EventoRequest("Noche de jazz", "Cuarteto en vivo", LocalDate.of(2026, 10, 4), LocalTime.of(22, 0),
				LocalTime.of(3, 0), "Teatro Municipal", 18, 4);
	}

	private static Set<String> camposInvalidos(EventoRequest solicitud) {
		return validator.validate(solicitud).stream().map(violacion -> violacion.getPropertyPath().toString())
				.collect(Collectors.toSet());
	}

	@Test
	void unEventoCompletoEsValido() {
		assertThat(camposInvalidos(valido())).isEmpty();
	}

	// RF-8: obligatorios, y un texto de solo espacios cuenta como vacío
	@Test
	void faltanLosObligatorios() {
		EventoRequest vacio = new EventoRequest("  ", null, null, null, null, " ", null, null);

		assertThat(camposInvalidos(vacio)).containsExactlyInAnyOrder("nombre", "fecha", "horaInicio", "horaFin", "lugar");
	}

	// RF-12: descripción, edad y límite son opcionales
	@Test
	void losOpcionalesPuedenFaltar() {
		EventoRequest sinOpcionales = new EventoRequest("Feria", null, LocalDate.of(2026, 10, 12), LocalTime.of(10, 0),
				LocalTime.of(12, 0), "Fexpocruz", null, null);

		assertThat(camposInvalidos(sinOpcionales)).isEmpty();
	}

	// RF-9: largos de la tabla evento
	@Test
	void respetaLosLargosDeLaTabla() {
		EventoRequest largo = new EventoRequest("n".repeat(151), null, LocalDate.of(2026, 10, 12), LocalTime.of(10, 0),
				LocalTime.of(12, 0), "l".repeat(201), null, null);
		EventoRequest justo = new EventoRequest("n".repeat(150), null, LocalDate.of(2026, 10, 12), LocalTime.of(10, 0),
				LocalTime.of(12, 0), "l".repeat(200), null, null);

		assertThat(camposInvalidos(largo)).containsExactlyInAnyOrder("nombre", "lugar");
		assertThat(camposInvalidos(justo)).isEmpty();
	}

	// La descripción es TEXT: hasta 65 535 bytes
	@Test
	void laDescripcionAdmiteLoQueEntraEnUnTexto() {
		EventoRequest limite = new EventoRequest("Feria", "a".repeat(65_535), LocalDate.of(2026, 10, 12),
				LocalTime.of(10, 0), LocalTime.of(12, 0), "Fexpocruz", null, null);
		EventoRequest pasado = new EventoRequest("Feria", "ñ".repeat(32_768), LocalDate.of(2026, 10, 12),
				LocalTime.of(10, 0), LocalTime.of(12, 0), "Fexpocruz", null, null);

		assertThat(camposInvalidos(limite)).isEmpty();
		assertThat(camposInvalidos(pasado)).containsExactly("descripcion");
	}

	// RF-13: edad de 1 a 99, límite de 1 o más
	@Test
	void edadYLimiteFueraDeRango() {
		assertThat(camposInvalidos(conEdadYLimite(0, 0))).containsExactlyInAnyOrder("restriccionEdad", "limiteCompra");
		assertThat(camposInvalidos(conEdadYLimite(100, -1))).containsExactlyInAnyOrder("restriccionEdad", "limiteCompra");
		assertThat(camposInvalidos(conEdadYLimite(1, 1))).isEmpty();
		assertThat(camposInvalidos(conEdadYLimite(99, 10_000))).isEmpty();
	}

	private static EventoRequest conEdadYLimite(Integer edad, Integer limite) {
		return new EventoRequest("Feria", null, LocalDate.of(2026, 10, 12), LocalTime.of(10, 0), LocalTime.of(12, 0),
				"Fexpocruz", edad, limite);
	}
}
