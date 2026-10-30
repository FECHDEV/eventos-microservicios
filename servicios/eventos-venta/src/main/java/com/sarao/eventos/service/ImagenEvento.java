package com.sarao.eventos.service;

// Una imagen leída del almacén, lista para servirse con su tipo de contenido
public record ImagenEvento(byte[] contenido, FormatoImagen formato) {
}
