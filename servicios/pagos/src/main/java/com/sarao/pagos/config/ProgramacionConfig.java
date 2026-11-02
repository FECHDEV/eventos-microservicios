package com.sarao.pagos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Habilita la conciliación de cada minuto (CU-07 RF-20)
@Configuration
@EnableScheduling
public class ProgramacionConfig {
}
