package com.sarao.eventos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.sarao.eventos.entity.EstadoEvento;
import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.exception.EventoCanceladoException;
import com.sarao.eventos.exception.ImagenDemasiadoGrandeException;
import com.sarao.eventos.exception.ImagenInvalidaException;
import com.sarao.eventos.exception.ImagenNoEncontradaException;
import com.sarao.eventos.repository.EventoRepository;

// El servicio deja el borrado de archivos para cuando termina la transacción: acá se simula ese final a mano
@ExtendWith(MockitoExtension.class)
class GestionEventosServiceImagenTest {

	@Mock
	private EventoRepository eventoRepository;

	@Mock
	private AlmacenImagenes almacenImagenes;

	private GestionEventosService servicio;
	private Evento evento;

	@BeforeEach
	void preparar() {
		TransactionSynchronizationManager.initSynchronization();
		servicio = new GestionEventosService(eventoRepository, almacenImagenes,
				GestionEventosServiceTest.RELOJ);
			evento = Evento.registrar(1L, "Noche de jazz", null, LocalDate.of(2026, 10, 4), LocalTime.of(22, 0),
				LocalTime.of(3, 0), "Teatro Municipal", null, null);
		// Subir y quitar escriben: toman el evento con bloqueo. Leer la imagen no
		lenient().when(eventoRepository.findConBloqueoByIdEvento(5L)).thenReturn(Optional.of(evento));
		lenient().when(eventoRepository.findById(5L)).thenReturn(Optional.of(evento));
	}

	@AfterEach
	void limpiar() {
		TransactionSynchronizationManager.clearSynchronization();
	}

	private static void terminarTransaccion(int estado) {
		TransactionSynchronizationManager.getSynchronizations()
				.forEach(sincronizacion -> sincronizacion.afterCompletion(estado));
	}

	// RF-22
	@Test
	void guardaLaImagenYLaAsocia() {
		given(almacenImagenes.guardar(FormatoImagenTest.PNG, FormatoImagen.PNG)).willReturn("nueva.png");

		servicio.cambiarImagen(5L, FormatoImagenTest.PNG);
		terminarTransaccion(TransactionSynchronization.STATUS_COMMITTED);

		assertThat(evento.getImagen()).isEqualTo("nueva.png");
		then(almacenImagenes).should(never()).borrar(any());
	}

	// RF-25: la anterior se borra solo cuando el cambio quedó guardado
	@Test
	void reemplazarBorraLaAnteriorAlConfirmar() {
		evento.cambiarImagen("vieja.jpg");
		given(almacenImagenes.guardar(FormatoImagenTest.PNG, FormatoImagen.PNG)).willReturn("nueva.png");

		servicio.cambiarImagen(5L, FormatoImagenTest.PNG);
		then(almacenImagenes).should(never()).borrar(any());
		terminarTransaccion(TransactionSynchronization.STATUS_COMMITTED);

		then(almacenImagenes).should().borrar("vieja.jpg");
	}

	// Si la transacción se revierte, el evento sigue con la anterior: la que sobra es la nueva
	@Test
	void siSeRevierteBorraLaNuevaYConservaLaAnterior() {
		evento.cambiarImagen("vieja.jpg");
		given(almacenImagenes.guardar(FormatoImagenTest.PNG, FormatoImagen.PNG)).willReturn("nueva.png");

		servicio.cambiarImagen(5L, FormatoImagenTest.PNG);
		terminarTransaccion(TransactionSynchronization.STATUS_ROLLED_BACK);

		then(almacenImagenes).should().borrar("nueva.png");
		then(almacenImagenes).should(never()).borrar("vieja.jpg");
	}

	// Con un final desconocido el commit pudo llegar a la base: borrar la nueva dejaría al evento sin su archivo
	@Test
	void conUnFinalDesconocidoNoBorraNada() {
		evento.cambiarImagen("vieja.jpg");
		given(almacenImagenes.guardar(FormatoImagenTest.PNG, FormatoImagen.PNG)).willReturn("nueva.png");

		servicio.cambiarImagen(5L, FormatoImagenTest.PNG);
		terminarTransaccion(TransactionSynchronization.STATUS_UNKNOWN);

		then(almacenImagenes).should(never()).borrar("nueva.png");
		then(almacenImagenes).should(never()).borrar("vieja.jpg");
	}

	@Test
	void cambiarYQuitarLaImagenBloqueanLaFilaDelEvento() {
		given(almacenImagenes.guardar(FormatoImagenTest.PNG, FormatoImagen.PNG)).willReturn("nueva.png");

		servicio.cambiarImagen(5L, FormatoImagenTest.PNG);
		servicio.quitarImagen(5L);

		then(eventoRepository).should(org.mockito.Mockito.times(2)).findConBloqueoByIdEvento(5L);
		then(eventoRepository).should(never()).findById(any());
	}

	// RF-23
	@Test
	void rechazaLoQueNoEsJpgPngNiWebp() {
		assertThatThrownBy(() -> servicio.cambiarImagen(5L, "<svg/>".getBytes()))
				.isInstanceOf(ImagenInvalidaException.class);
		then(almacenImagenes).should(never()).guardar(any(), any());
	}

	// RF-24: 2 MB justos entran, un byte más no
	@Test
	void rechazaMasDeDosMegabytes() {
		byte[] justo = Arrays.copyOf(FormatoImagenTest.PNG, GestionEventosService.BYTES_MAXIMOS_IMAGEN);
		byte[] pasado = Arrays.copyOf(FormatoImagenTest.PNG, GestionEventosService.BYTES_MAXIMOS_IMAGEN + 1);
		given(almacenImagenes.guardar(justo, FormatoImagen.PNG)).willReturn("justa.png");

		servicio.cambiarImagen(5L, justo);

		assertThatThrownBy(() -> servicio.cambiarImagen(5L, pasado)).isInstanceOf(ImagenDemasiadoGrandeException.class);
	}

	// RF-26
	@Test
	void quitarLaDesasociaYBorraElArchivoAlConfirmar() {
		evento.cambiarImagen("vieja.jpg");

		servicio.quitarImagen(5L);
		terminarTransaccion(TransactionSynchronization.STATUS_COMMITTED);

		assertThat(evento.tieneImagen()).isFalse();
		then(almacenImagenes).should().borrar("vieja.jpg");
	}

	@Test
	void quitarCuandoNoHayImagenNoBorraNada() {
		servicio.quitarImagen(5L);
		terminarTransaccion(TransactionSynchronization.STATUS_COMMITTED);

		then(almacenImagenes).should(never()).borrar(any());
	}

	// RF-27
	@Test
	void unCanceladoNoCambiaNiQuitaSuImagen() {
		ReflectionTestUtils.setField(evento, "estado", EstadoEvento.CANCELADO);

		assertThatThrownBy(() -> servicio.cambiarImagen(5L, FormatoImagenTest.PNG))
				.isInstanceOf(EventoCanceladoException.class);
		assertThatThrownBy(() -> servicio.quitarImagen(5L)).isInstanceOf(EventoCanceladoException.class);
		then(almacenImagenes).should(never()).guardar(any(), any());
	}

	// RF-28
	@Test
	void leeLaImagenConSuFormato() {
		evento.cambiarImagen("5f2c1d3e-0000-4000-8000-000000000000.webp");
		given(almacenImagenes.leer("5f2c1d3e-0000-4000-8000-000000000000.webp"))
				.willReturn(Optional.of(FormatoImagenTest.WEBP));

		ImagenEvento imagen = servicio.leerImagen(5L);

		assertThat(imagen.contenido()).isEqualTo(FormatoImagenTest.WEBP);
		assertThat(imagen.formato()).isEqualTo(FormatoImagen.WEBP);
	}

	@Test
	void sinImagenOSinArchivoEsNoEncontrada() {
		assertThatThrownBy(() -> servicio.leerImagen(5L)).isInstanceOf(ImagenNoEncontradaException.class);

		evento.cambiarImagen("5f2c1d3e-0000-4000-8000-000000000000.png");
		given(almacenImagenes.leer(any())).willReturn(Optional.empty());
		assertThatThrownBy(() -> servicio.leerImagen(5L)).isInstanceOf(ImagenNoEncontradaException.class);
	}
}
