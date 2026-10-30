package com.sarao.seguridad.service;

import com.sarao.seguridad.config.TokenEmitido;
import com.sarao.seguridad.entity.Usuario;

public record SesionIniciada(Usuario usuario, TokenEmitido token) {
}
