// Las dos reglas que el backend aplica y que @angular/forms/signals no trae de
// fábrica. Son funciones puras para poder probarlas sin formulario y sin reloj.

// La contraseña se limita por bytes porque así la limita BCrypt, y una ñ o un
// emoji ocupan más de uno (CU-01 RF-12).
export function bytesUtf8(valor: string): number {
	return new TextEncoder().encode(valor).length;
}

// La misma regla de ReglasContrasena en Seguridad, también con DOTALL (flag s).
// Con la flag u cuenta caracteres como Java, no unidades UTF-16: un emoji es uno.
// al registrarse, al cambiar y al restablecer la contraseña.
export const CONTRASENA_SEGURA = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/su;
export const AYUDA_CONTRASENA = 'al menos 8 caracteres, con una mayúscula, una minúscula y un número.';

// Fecha del calendario de quien usa la pantalla, como AAAA-MM-DD. Se arma con
// los métodos locales: con toISOString(), una noche en Bolivia ya sería el día
// siguiente en UTC.
export function fechaLocalISO(momento: Date): string {
	const mes = `${momento.getMonth() + 1}`.padStart(2, '0');
	const dia = `${momento.getDate()}`.padStart(2, '0');
	return `${momento.getFullYear()}-${mes}-${dia}`;
}

// RF-13: la fecha de nacimiento de hoy se acepta; solo se rechaza la posterior.
// En formato AAAA-MM-DD comparar como texto equivale a comparar fechas.
export function esFechaFutura(iso: string, hoy: Date): boolean {
	return iso !== '' && iso > fechaLocalISO(hoy);
}
