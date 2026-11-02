package com.sarao.notificaciones.controller;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.sarao.notificaciones.dto.CorreoDeCompra;
import com.sarao.notificaciones.dto.EntradasRequest;
import com.sarao.notificaciones.dto.RecuperacionRequest;
import com.sarao.notificaciones.dto.ReporteRequest;
import com.sarao.notificaciones.service.NotificadorCorreo;
import jakarta.validation.Valid;

// Solo para otros servicios: SecurityConfig exige la clave de servicio y el gateway no publica /interno. Responde 202: el
// correo sale en segundo plano y un fallo de entrega se registra acá, sin afectar a quien lo pidió
@RestController
@RequestMapping("/interno/correos")
@ResponseStatus(HttpStatus.ACCEPTED)
public class CorreosController {

	private final NotificadorCorreo notificador;
	private final String dominio;

	public CorreosController(NotificadorCorreo notificador, @Value("${eventos.correo.dominio}") String dominio) {
		this.notificador = notificador;
		this.dominio = dominio;
	}

	@PostMapping("/recuperacion")
	public void recuperacion(@Valid @RequestBody RecuperacionRequest solicitud) {
		notificador.enviarRecuperacion(solicitud.correo(), solicitud.nombre(), solicitud.token());
	}

	@PostMapping("/detalle-de-compra")
	public void detalleDeCompra(@Valid @RequestBody CorreoDeCompra compra) {
		notificador.enviarDetalleDeCompra(compra);
	}

	// CU-14: el servidor de correo solo entrega al dominio propio; otra dirección daría un «enviado» que no llega
	@PostMapping("/reporte")
	public void reporte(@Valid @RequestBody ReporteRequest reporte) {
		String sufijo = "@" + dominio.toLowerCase(Locale.ROOT);
		if (!reporte.destinatarios().stream().allMatch(para -> para.toLowerCase(Locale.ROOT).endsWith(sufijo))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se envía a buzones del dominio propio");
		}
		notificador.enviarReporte(reporte);
	}

	@PostMapping("/entradas")
	public void entradas(@Valid @RequestBody EntradasRequest solicitud) {
		notificador.enviarEntradas(solicitud.compra(), solicitud.codigos());
	}
}
