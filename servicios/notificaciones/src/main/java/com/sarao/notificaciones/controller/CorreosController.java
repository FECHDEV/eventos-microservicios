package com.sarao.notificaciones.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sarao.notificaciones.dto.CorreoDeCompra;
import com.sarao.notificaciones.dto.EntradasRequest;
import com.sarao.notificaciones.dto.RecuperacionRequest;
import com.sarao.notificaciones.service.NotificadorCorreo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// Solo para otros servicios: SecurityConfig exige la clave de servicio y el gateway no publica /interno. Responde 202: el
// correo sale en segundo plano y un fallo de entrega se registra acá, sin afectar a quien lo pidió
@RestController
@RequestMapping("/interno/correos")
@ResponseStatus(HttpStatus.ACCEPTED)
@RequiredArgsConstructor
public class CorreosController {

	private final NotificadorCorreo notificador;

	@PostMapping("/recuperacion")
	public void recuperacion(@Valid @RequestBody RecuperacionRequest solicitud) {
		notificador.enviarRecuperacion(solicitud.correo(), solicitud.nombre(), solicitud.token());
	}

	@PostMapping("/detalle-de-compra")
	public void detalleDeCompra(@Valid @RequestBody CorreoDeCompra compra) {
		notificador.enviarDetalleDeCompra(compra);
	}

	@PostMapping("/entradas")
	public void entradas(@Valid @RequestBody EntradasRequest solicitud) {
		notificador.enviarEntradas(solicitud.compra(), solicitud.codigos());
	}
}
