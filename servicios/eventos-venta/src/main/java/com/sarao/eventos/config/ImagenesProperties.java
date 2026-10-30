package com.sarao.eventos.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Carpeta donde se guardan las imágenes de los eventos (decisiones.md → Imagen del evento)
@ConfigurationProperties("eventos.imagenes")
public record ImagenesProperties(Path directorio) {
}
