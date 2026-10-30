package com.sarao.seguridad.dto.validacion;

// La misma regla al registrarse, al cambiar y al restablecer la contraseña (docs/decisiones.md → Complejidad)
public final class ReglasContrasena {

	public static final String PATRON = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$";
	public static final String MENSAJE = "debe tener al menos 8 caracteres, con una mayúscula, una minúscula y un número";

	private ReglasContrasena() {
	}
}
