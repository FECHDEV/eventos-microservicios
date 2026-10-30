package com.sarao.eventos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Habilita las tareas programadas: el cierre automático de la venta (CU-12 FA-01)
@Configuration
@EnableScheduling
public class ProgramacionConfig {
}
