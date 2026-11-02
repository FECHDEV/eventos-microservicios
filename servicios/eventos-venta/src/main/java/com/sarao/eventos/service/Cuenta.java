package com.sarao.eventos.service;

import java.time.LocalDateTime;

// Una cuenta de Seguridad, como la necesitan los reportes: Eventos y Venta solo guarda su id
public record Cuenta(Long idUsuario, String nombre, String apellido, String correo, String telefono,
		LocalDateTime fechaRegistro, String estado) {
}
