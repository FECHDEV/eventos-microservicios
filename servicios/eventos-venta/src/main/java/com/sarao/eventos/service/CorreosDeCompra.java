package com.sarao.eventos.service;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.entity.Reserva;
import com.sarao.eventos.service.ConsultaDeClientes.Contacto;
import lombok.extern.slf4j.Slf4j;

// CU-07 RF-17 y RF-18: los dos correos de una compra, después del commit y en segundo plano. Una compra revertida no
// manda correos, y un correo que falla no revierte la compra: el fallo queda en el log
@Slf4j
@Component
public class CorreosDeCompra {

	private static final Duration PLAZO_RESPUESTA = Duration.ofSeconds(2);

	private final ConsultaDeCompras consultaDeCompras;
	private final ConsultaDeClientes consultaDeClientes;
	private final RestClient notificaciones;

	public CorreosDeCompra(ConsultaDeCompras consultaDeCompras, ConsultaDeClientes consultaDeClientes,
			@Value("${eventos.notificaciones-url}") String urlNotificaciones,
			@Value("${eventos.clave-servicio}") String claveServicio) {
		this.consultaDeCompras = consultaDeCompras;
		this.consultaDeClientes = consultaDeClientes;
		this.notificaciones = ClienteInterno.crear(urlNotificaciones, claveServicio, PLAZO_RESPUESTA);
	}

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void alConfirmarse(CompraConfirmada confirmada) {
		try {
			ConsultaDeCompras.Compra compra = consultaDeCompras.compra(confirmada.idReserva());
			Contacto contacto = consultaDeClientes.contacto(compra.reserva().getIdCliente());
			Map<String, Object> detalle = detalle(compra.reserva(), contacto);
			pedir("/interno/correos/detalle-de-compra", detalle);
			pedir("/interno/correos/entradas", Map.of("compra", detalle, "codigos", compra.codigos()));
		} catch (RuntimeException e) {
			// Sin los códigos: con ellos se entra al evento
			log.error("No se pudieron pedir los correos de la reserva {}: {}", confirmada.idReserva(),
					e.getClass().getSimpleName());
		}
	}

	private void pedir(String ruta, Object cuerpo) {
		try {
			notificaciones.post().uri(ruta)
					.contentType(MediaType.APPLICATION_JSON).body(cuerpo).retrieve().toBodilessEntity();
		} catch (RestClientException e) {
			log.error("No se pudo pedir el correo {}: {}", ruta, e.getClass().getSimpleName());
		}
	}

	private static Map<String, Object> detalle(Reserva reserva, Contacto contacto) {
		Evento evento = reserva.getTipoEntrada().getEvento();
		return Map.of("correo", contacto.correo(), "nombre", contacto.nombre(), "evento", evento.getNombre(),
				"fecha", evento.getFecha(), "horaInicio", evento.getHoraInicio(), "lugar", evento.getLugar(),
				"tipo", reserva.getTipoEntrada().getNombre(), "cantidad", reserva.getCantidad(),
				"precioUnitario", reserva.getPrecioUnitario(), "total", reserva.total());
	}
}
