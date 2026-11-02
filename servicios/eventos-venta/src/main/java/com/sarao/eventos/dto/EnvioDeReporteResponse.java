package com.sarao.eventos.dto;

// CU-14 RF-24: a cuántos destinatarios salió el correo. Los archivos van adjuntos, no en la respuesta
public record EnvioDeReporteResponse(int destinatarios) {
}
