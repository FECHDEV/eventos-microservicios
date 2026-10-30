// Espejo de /api/accesos (CU-15). Solo el Personal autorizado. La respuesta de
// una validación no trae datos del Cliente: el resultado y, si es válida, el tipo

// Un evento en el que se puede validar ahora: activo y hoy, o todavía en curso
export interface EventoParaValidar {
	idEvento: number;
	nombre: string;
	fecha: string;
	horaInicio: string;
	horaFin: string;
	lugar: string;
}

// Los rechazos de FA-01 a FA-03 son resultados, no errores
export type Resultado = 'VALIDA' | 'NO_CORRESPONDE' | 'NO_VIGENTE' | 'YA_UTILIZADA';

export interface ResultadoValidacion {
	resultado: Resultado;
	tipoEntrada: string | null;
}

const TEXTOS: Record<Resultado, string> = {
	VALIDA: 'Entrada válida',
	NO_CORRESPONDE: 'No es válida para este evento',
	NO_VIGENTE: 'No es válida',
	YA_UTILIZADA: 'Ya fue utilizada',
};

// CU-15 RF-14: lo que la puerta lee en grande
export function textoDelResultado(resultado: Resultado): string {
	return TEXTOS[resultado];
}

export function esValida(resultado: Resultado): boolean {
	return resultado === 'VALIDA';
}
