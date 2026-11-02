package com.sarao.eventos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

// Los pedidos de correo van en segundo plano (CorreosDeCompra)
@Configuration
@EnableAsync
public class AsincroniaConfig {
}
