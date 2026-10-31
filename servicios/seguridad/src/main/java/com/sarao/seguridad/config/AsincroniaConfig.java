package com.sarao.seguridad.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

// Los pedidos de correo van en segundo plano (NotificadorCorreoRemoto)
@Configuration
@EnableAsync
public class AsincroniaConfig {
}
