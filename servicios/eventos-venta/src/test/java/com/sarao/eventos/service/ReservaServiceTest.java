package com.sarao.eventos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.sarao.eventos.dto.ReservaRequest;
import com.sarao.eventos.entity.EstadoEvento;
import com.sarao.eventos.entity.EstadoReserva;
import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.entity.Reserva;
import com.sarao.eventos.entity.TipoEntrada;
import com.sarao.eventos.exception.EdadNoPermitidaException;
import com.sarao.eventos.exception.EntradasNoDisponiblesException;
import com.sarao.eventos.exception.LimiteCompraExcedidoException;
import com.sarao.eventos.exception.ReservaNoEncontradaException;
import com.sarao.eventos.exception.TipoEntradaNoEncontradoException;
import com.sarao.eventos.exception.VentaCerradaException;
import com.sarao.eventos.repository.EventoRepository;
import com.sarao.eventos.repository.ReservaRepository;
import com.sarao.eventos.repository.TipoEntradaRepository;

// El evento y el tipo solo se stubbean por los métodos con bloqueo: si el servicio los buscara sin bloqueo, estos tests
// darían 404 (RF-14)
@ExtendWith(MockitoExtension.class)
class ReservaServiceTest {

	// 21/09/2026 a las 20:00 en La Paz
	private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-09-22T00:00:00Z"), ZoneId.of("America/La_Paz"));
	private static final LocalDateTime AHORA = LocalDateTime.of(2026, 9, 21, 20, 0);
	private static final LocalDate MAYOR = LocalDate.of(2000, 5, 20);
	private static final LocalDate DIA_DEL_EVENTO = LocalDate.of(2026, 10, 4);

	@Mock
	private EventoRepository eventoRepository;

	@Mock
	private TipoEntradaRepository tipoEntradaRepository;

	@Mock
	private ReservaRepository reservaRepository;

	@Mock
	private LiberacionDeReservas liberacionDeReservas;

	private ReservaService servicio;

	@BeforeEach
	void crearServicio() {
		servicio = new ReservaService(eventoRepository, tipoEntradaRepository, reservaRepository, liberacionDeReservas,
				RELOJ);
	}

	// Activo el 04/10 de 22:00 a 03:00, id 5
	private Evento evento(Integer restriccionEdad, Integer limiteCompra) {
		Evento evento = Evento.registrar(1L, "Noche de jazz", null, DIA_DEL_EVENTO, LocalTime.of(22, 0),
				LocalTime.of(3, 0), "Teatro Municipal", restriccionEdad, limiteCompra);
		ReflectionTestUtils.setField(evento, "idEvento", 5L);
		evento.activar();
		given(tipoEntradaRepository.idEventoDe(3L)).willReturn(Optional.of(5L));
		given(eventoRepository.findConBloqueoByIdEvento(5L)).willReturn(Optional.of(evento));
		return evento;
	}

	// General, id 3, a 50 Bs con la disponible indicada
	private TipoEntrada general(Evento evento, int disponible) {
		TipoEntrada tipo = TipoEntrada.registrar(evento, "General", new BigDecimal("50"), 100);
		ReflectionTestUtils.setField(tipo, "idTipoEntrada", 3L);
		ReflectionTestUtils.setField(tipo, "cantidadDisponible", disponible);
		given(tipoEntradaRepository.findConBloqueoByIdTipoEntradaAndEvento_IdEvento(3L, 5L))
				.willReturn(Optional.of(tipo));
		return tipo;
	}

	private void guardarDevuelveLoMismo() {
		given(reservaRepository.save(any())).willAnswer(invocacion -> invocacion.getArgument(0));
	}

	private DetalleReserva reservar(int cantidad) {
		return reservar(MAYOR, cantidad);
	}

	private DetalleReserva reservar(LocalDate fechaNacimiento, int cantidad) {
		return servicio.reservar(7L, fechaNacimiento, new ReservaRequest(3L, cantidad));
	}

	// --- Reserva (RF-12, RF-13, RF-15) ---

	@Test
	void reservaDescuentaYDevuelveElDetalle() {
		Evento jazz = evento(null, null);
		TipoEntrada general = general(jazz, 10);
		guardarDevuelveLoMismo();

		DetalleReserva detalle = reservar(3);

		ArgumentCaptor<Reserva> guardada = ArgumentCaptor.forClass(Reserva.class);
		then(reservaRepository).should().save(guardada.capture());
		Reserva reserva = guardada.getValue();
		assertThat(reserva.getIdCliente()).isEqualTo(7L);
		assertThat(reserva.getTipoEntrada()).isSameAs(general);
		assertThat(reserva.getCantidad()).isEqualTo(3);
		assertThat(reserva.getPrecioUnitario()).isEqualTo(new BigDecimal("50.00"));
		assertThat(reserva.getFechaCreacion()).isEqualTo(AHORA);
		assertThat(reserva.getFechaExpiracion()).isEqualTo(AHORA.plusMinutes(10));
		assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.VIGENTE);
		assertThat(general.getCantidadDisponible()).isEqualTo(7);
		assertThat(detalle.reserva()).isSameAs(reserva);
		assertThat(detalle.tipo()).isSameAs(general);
		assertThat(detalle.evento()).isSameAs(jazz);
		assertThat(detalle.consultadaEn()).isEqualTo(AHORA);
	}

	// RF-14 y RF-17: evento bloqueado, vencidas liberadas y recién entonces el tipo
	@Test
	void bloqueaElEventoLiberaYDespuesTomaElTipo() {
		Evento jazz = evento(null, null);
		general(jazz, 10);
		guardarDevuelveLoMismo();

		reservar(1);

		InOrder orden = inOrder(eventoRepository, liberacionDeReservas, tipoEntradaRepository);
		orden.verify(eventoRepository).findConBloqueoByIdEvento(5L);
		orden.verify(liberacionDeReservas).liberarVencidasConElEventoBloqueado(5L, AHORA);
		orden.verify(tipoEntradaRepository).findConBloqueoByIdTipoEntradaAndEvento_IdEvento(3L, 5L);
	}

	// RF-7
	@Test
	void unPostergadoTambienVende() {
		Evento jazz = evento(null, null);
		jazz.postergar(LocalDate.of(2026, 11, 1));
		general(jazz, 10);
		guardarDevuelveLoMismo();

		assertThat(reservar(1).reserva().getCantidad()).isEqualTo(1);
	}

	// --- Tipo o evento que no vende (RF-5, RF-6) ---

	@Test
	void unTipoInexistenteEs404() {
		given(tipoEntradaRepository.idEventoDe(3L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> reservar(1)).isInstanceOf(TipoEntradaNoEncontradoException.class);
		then(eventoRepository).shouldHaveNoInteractions();
	}

	// Borrador, cancelado o terminado: responden como si el tipo no existiera
	@Test
	void unEventoQueNoSeMuestraEs404() {
		Evento jazz = evento(null, null);
		ReflectionTestUtils.setField(jazz, "estado", EstadoEvento.BORRADOR);
		assertThatThrownBy(() -> reservar(1)).isInstanceOf(TipoEntradaNoEncontradoException.class);

		ReflectionTestUtils.setField(jazz, "estado", EstadoEvento.CANCELADO);
		assertThatThrownBy(() -> reservar(1)).isInstanceOf(TipoEntradaNoEncontradoException.class);

		// Terminó hace dos semanas, aunque la venta siga abierta por el minuto de desfase
		ReflectionTestUtils.setField(jazz, "estado", EstadoEvento.ACTIVO);
		ReflectionTestUtils.setField(jazz, "fecha", LocalDate.of(2026, 9, 7));
		assertThat(jazz.isVentaAbierta()).isTrue();
		assertThatThrownBy(() -> reservar(1)).isInstanceOf(TipoEntradaNoEncontradoException.class);

		// Corta en el evento: sin esto el 404 podría salir recién al no encontrar el tipo
		then(liberacionDeReservas).shouldHaveNoInteractions();
		then(tipoEntradaRepository).should(never()).findConBloqueoByIdTipoEntradaAndEvento_IdEvento(any(), any());
		then(reservaRepository).should(never()).save(any());
	}

	@Test
	void conLaVentaCerradaNoReserva() {
		evento(null, null).cerrarVenta();

		assertThatThrownBy(() -> reservar(1)).isInstanceOf(VentaCerradaException.class);
		then(reservaRepository).should(never()).save(any());
	}

	// --- Validaciones en el orden de la ficha (RF-8 a RF-11) ---

	@Test
	void sinDisponibilidadSuficienteNoReserva() {
		TipoEntrada general = general(evento(null, null), 2);

		assertThatThrownBy(() -> reservar(3)).isInstanceOf(EntradasNoDisponiblesException.class);
		assertThat(general.getCantidadDisponible()).isEqualTo(2);
		then(reservaRepository).should(never()).save(any());
	}

	@Test
	void pedirExactamenteLaDisponibleLaAgota() {
		TipoEntrada general = general(evento(null, null), 2);
		guardarDevuelveLoMismo();

		reservar(2);

		assertThat(general.getCantidadDisponible()).isZero();
	}

	@Test
	void sinLaEdadMinimaNoReservaEInformaCual() {
		general(evento(18, null), 10);

		assertThatThrownBy(() -> reservar(DIA_DEL_EVENTO.minusYears(18).plusDays(1), 1))
				.isInstanceOfSatisfying(EdadNoPermitidaException.class,
				error -> assertThat(error.propiedades()).containsEntry("edadMinima", 18));
		then(reservaRepository).should(never()).save(any());
	}

	@Test
	void cumplirLaEdadElDiaDelEventoAlcanza() {
		general(evento(18, null), 10);
		guardarDevuelveLoMismo();

		assertThat(reservar(DIA_DEL_EVENTO.minusYears(18), 1).reserva().getCantidad()).isEqualTo(1);
	}

	@Test
	void superarElLimiteInformaCuantasPuedeAgregar() {
		general(evento(null, 4), 10);
		given(reservaRepository.cantidadVigenteDelCliente(7L, 5L, AHORA)).willReturn(3L);

		assertThatThrownBy(() -> reservar(2)).isInstanceOfSatisfying(LimiteCompraExcedidoException.class,
				error -> assertThat(error.propiedades()).containsEntry("limite", 4).containsEntry("puedeAgregar", 1));
		then(reservaRepository).should(never()).save(any());
	}

	@Test
	void llegarJustoAlLimiteSeAcepta() {
		general(evento(null, 4), 10);
		given(reservaRepository.cantidadVigenteDelCliente(7L, 5L, AHORA)).willReturn(3L);
		guardarDevuelveLoMismo();

		assertThat(reservar(1).reserva().getCantidad()).isEqualTo(1);
	}

	@Test
	void sinLimiteNoCuentaLasReservasDelCliente() {
		general(evento(null, null), 10);
		guardarDevuelveLoMismo();

		reservar(5);

		then(reservaRepository).should(never()).cantidadVigenteDelCliente(any(), any(), any());
	}

	// RF-11: disponibilidad antes que edad, y edad antes que límite
	@Test
	void informaLaPrimeraValidacionQueFalla() {
		general(evento(18, 1), 1);
		LocalDate menor = LocalDate.of(2015, 1, 1);

		assertThatThrownBy(() -> reservar(menor, 2)).isInstanceOf(EntradasNoDisponiblesException.class);
		assertThatThrownBy(() -> reservar(menor, 1)).isInstanceOf(EdadNoPermitidaException.class);
		then(reservaRepository).should(never()).cantidadVigenteDelCliente(any(), any(), any());
	}

	// --- Consulta (RF-20) ---

	// Sin los helpers de arriba: consultar no busca el evento ni el tipo con bloqueo, y Mockito rechaza stubs sin usar
	@Test
	void consultarLiberaAntesDeLeerYDevuelveElDetalle() {
		Evento jazz = Evento.registrar(1L, "Noche de jazz", null, DIA_DEL_EVENTO, LocalTime.of(22, 0),
				LocalTime.of(3, 0), "Teatro Municipal", null, null);
		TipoEntrada general = TipoEntrada.registrar(jazz, "General", new BigDecimal("50"), 100);
		Reserva reserva = Reserva.crear(7L, general, 2, AHORA.minusMinutes(3));
		given(reservaRepository.idEventoDeLaReserva(40L, 7L)).willReturn(Optional.of(5L));
		given(reservaRepository.buscarDelCliente(40L, 7L)).willReturn(Optional.of(reserva));

		DetalleReserva detalle = servicio.consultar(7L, 40L);

		InOrder orden = inOrder(liberacionDeReservas, reservaRepository);
		orden.verify(liberacionDeReservas).liberarVencidas(5L, AHORA);
		orden.verify(reservaRepository).buscarDelCliente(40L, 7L);
		assertThat(detalle.reserva()).isSameAs(reserva);
		assertThat(detalle.tipo()).isSameAs(general);
		assertThat(detalle.evento()).isSameAs(jazz);
		assertThat(detalle.consultadaEn()).isEqualTo(AHORA);
	}

	@Test
	void unaReservaAjenaOInexistenteEs404() {
		given(reservaRepository.idEventoDeLaReserva(40L, 7L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.consultar(7L, 40L)).isInstanceOf(ReservaNoEncontradaException.class);
		then(liberacionDeReservas).shouldHaveNoInteractions();
	}
}
