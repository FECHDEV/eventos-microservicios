package com.sarao.eventos.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class TipoEntradaRequestTest {

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

	private static Set<String> camposInvalidos(TipoEntradaRequest solicitud) {
		return validator.validate(solicitud).stream().map(violacion -> violacion.getPropertyPath().toString())
				.collect(Collectors.toSet());
	}

	private static TipoEntradaRequest conPrecio(String precio) {
		return new TipoEntradaRequest("General", new BigDecimal(precio), 100);
	}

	@Test
	void unTipoCompletoEsValido() {
		assertThat(camposInvalidos(new TipoEntradaRequest("General", new BigDecimal("50"), 100))).isEmpty();
	}

	// RF-8: obligatorios, y un nombre de solo espacios cuenta como vacío
	@Test
	void faltanLosObligatorios() {
		assertThat(camposInvalidos(new TipoEntradaRequest("   ", null, null)))
				.containsExactlyInAnyOrder("nombre", "precio", "cantidadTotal");
	}

	// RF-9: largo de la tabla tipo_entrada
	@Test
	void elNombreAdmiteHasta80Caracteres() {
		assertThat(camposInvalidos(new TipoEntradaRequest("n".repeat(81), new BigDecimal("50"), 100)))
				.containsExactly("nombre");
		assertThat(camposInvalidos(new TipoEntradaRequest("n".repeat(80), new BigDecimal("50"), 100))).isEmpty();
	}

	// RF-10: mayor que 0, hasta 2 decimales y hasta 99 999 999,99 (DECIMAL(10,2))
	@Test
	void precioFueraDeLoPermitido() {
		assertThat(camposInvalidos(conPrecio("0"))).containsExactly("precio");
		assertThat(camposInvalidos(conPrecio("-1"))).containsExactly("precio");
		assertThat(camposInvalidos(conPrecio("10.999"))).containsExactly("precio");
		// Cuenta los decimales como llegan: 10.500 son tres, aunque valga lo mismo que 10.50
		assertThat(camposInvalidos(conPrecio("10.500"))).containsExactly("precio");
		assertThat(camposInvalidos(conPrecio("100000000.00"))).containsExactly("precio");
		assertThat(camposInvalidos(conPrecio("0.01"))).isEmpty();
		assertThat(camposInvalidos(conPrecio("10.5"))).isEmpty();
		assertThat(camposInvalidos(conPrecio("99999999.99"))).isEmpty();
	}

	// RF-11: al menos 1
	@Test
	void laCantidadTotalEsDeAlMenosUno() {
		assertThat(camposInvalidos(new TipoEntradaRequest("General", new BigDecimal("50"), 0)))
				.containsExactly("cantidadTotal");
		assertThat(camposInvalidos(new TipoEntradaRequest("General", new BigDecimal("50"), 1))).isEmpty();
	}
}
