package com.sarao.seguridad.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.sarao.seguridad.dto.validacion.MaximoBytesUtf8;
import com.sarao.seguridad.dto.validacion.ReglasContrasena;
import com.sarao.seguridad.entity.Usuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Las reglas se validan en AdministradorInicial y solo cuando no hay administrador: por eso no lleva @Validated
@ConfigurationProperties("eventos.admin")
public record AdministradorInicialProperties(

		@NotBlank(message = "EVENTOS_ADMIN_CORREO es obligatorio")
		@Email(message = "EVENTOS_ADMIN_CORREO no tiene formato de correo")
		@Size(max = 120, message = "EVENTOS_ADMIN_CORREO admite hasta 120 caracteres")
		String correo,

		@NotBlank(message = "EVENTOS_ADMIN_PASSWORD es obligatoria")
		@Pattern(regexp = ReglasContrasena.PATRON, flags = Pattern.Flag.DOTALL,
				message = "EVENTOS_ADMIN_PASSWORD " + ReglasContrasena.MENSAJE)
		@MaximoBytesUtf8(value = 72, message = "EVENTOS_ADMIN_PASSWORD admite hasta 72 bytes")
		String contrasena) {

	public AdministradorInicialProperties {
		correo = correo == null ? null : Usuario.normalizarCorreo(correo);
	}

	@Override
	public String toString() {
		return "AdministradorInicialProperties[correo=" + correo + "]";
	}
}
