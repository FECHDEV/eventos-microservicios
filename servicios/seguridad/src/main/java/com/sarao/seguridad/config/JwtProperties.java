package com.sarao.seguridad.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("eventos.jwt")
public record JwtProperties(String secreto, Duration duracion) {
}
