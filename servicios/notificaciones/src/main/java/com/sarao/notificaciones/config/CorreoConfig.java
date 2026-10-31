package com.sarao.notificaciones.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

// El envío va en segundo plano: quien llama recibe el 202 sin esperar al servidor de correo (NotificadorCorreoSmtp)
@Configuration
@EnableAsync
@EnableConfigurationProperties(CorreoProperties.class)
public class CorreoConfig {
}
