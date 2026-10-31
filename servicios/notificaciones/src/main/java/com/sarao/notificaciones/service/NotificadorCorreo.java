package com.sarao.notificaciones.service;

import java.util.List;

import com.sarao.notificaciones.dto.CorreoDeCompra;

// Los tres correos a clientes (CLAUDE.md → Correos). El detalle de compra y las entradas van separados (decisiones.md →
// Dos correos al confirmar la compra)
public interface NotificadorCorreo {

	void enviarRecuperacion(String correo, String nombre, String token);

	void enviarDetalleDeCompra(CorreoDeCompra compra);

	void enviarEntradas(CorreoDeCompra compra, List<String> codigos);
}
