import { Estilo, Modo } from '../../shared/apariencia/apariencia';

// El estilo y el modo que eligió la persona, guardados en el navegador
// (docs/decisiones.md → «Tres estilos y modo día/noche»). Sin nada guardado, o
// si el almacenamiento no está disponible: adultos y noche.
//
// index.html repite esta lectura antes de que arranque Angular, para no mostrar
// un destello de otro tema: si cambia acá, cambia allá

export type Tema = 'oscuro' | 'claro';

export const CLAVE_ESTILO = 'sarao.estilo';
export const CLAVE_MODO = 'sarao.modo';
// La del botón sol/luna (hasta el 12/10/2026): 'claro' u 'oscuro'
export const CLAVE_TEMA_ANTERIOR = 'sarao.tema';

// En automático es de día de 6:00 a 18:59, con la hora del dispositivo
const HORA_DE_DIA = 6;
const HORA_DE_NOCHE = 19;

export function leerEstilo(): Estilo {
	const guardado = leer(CLAVE_ESTILO);
	return guardado === 'ninos' || guardado === 'jovenes' ? guardado : 'adultos';
}

export function leerModo(): Modo {
	const guardado = leer(CLAVE_MODO);
	if (guardado === 'automatico' || guardado === 'dia' || guardado === 'noche') {
		return guardado;
	}
	return leer(CLAVE_TEMA_ANTERIOR) === 'claro' ? 'dia' : 'noche';
}

export function guardarEstilo(estilo: Estilo): void {
	escribir(() => localStorage.setItem(CLAVE_ESTILO, estilo));
}

export function guardarModo(modo: Modo): void {
	escribir(() => {
		localStorage.setItem(CLAVE_MODO, modo);
		localStorage.removeItem(CLAVE_TEMA_ANTERIOR);
	});
}

export function temaDe(modo: Modo, ahora: Date): Tema {
	if (modo === 'automatico') {
		const hora = ahora.getHours();
		return hora >= HORA_DE_DIA && hora < HORA_DE_NOCHE ? 'claro' : 'oscuro';
	}
	return modo === 'dia' ? 'claro' : 'oscuro';
}

// Hasta las próximas 6:00 o 19:00, lo que llegue antes
export function milisegundosHastaElCambio(ahora: Date): number {
	const cambio = new Date(ahora);
	const hora = ahora.getHours();
	if (hora < HORA_DE_DIA) {
		cambio.setHours(HORA_DE_DIA, 0, 0, 0);
	} else if (hora < HORA_DE_NOCHE) {
		cambio.setHours(HORA_DE_NOCHE, 0, 0, 0);
	} else {
		cambio.setDate(cambio.getDate() + 1);
		cambio.setHours(HORA_DE_DIA, 0, 0, 0);
	}
	return cambio.getTime() - ahora.getTime();
}

function leer(clave: string): string | null {
	try {
		return localStorage.getItem(clave);
	} catch {
		return null;
	}
}

function escribir(guardar: () => void): void {
	try {
		guardar();
	} catch {
		// Modo privado o almacenamiento lleno: la elección vale hasta recargar
	}
}
