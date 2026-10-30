package com.sarao.seguridad.config;

import java.time.Instant;

public record TokenEmitido(String token, Instant expiracion) {
}
