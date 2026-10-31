package com.sarao.seguridad.service;

// El correo de recuperación que hay que mandar cuando la transacción se confirme
public record RecuperacionSolicitada(String correo, String nombre, String token) {
}
