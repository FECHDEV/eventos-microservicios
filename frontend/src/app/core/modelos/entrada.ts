// Espejo de /api/entradas (CU-08). El listado no trae los códigos; el detalle
// trae el de la entrada solo si puede servir para entrar

import { EstadoEvento } from './evento';

export type EstadoEntrada = 'ACTIVA' | 'UTILIZADA' | 'DEVUELTA';

// Una entrada dentro de una compra del listado, numerada desde 1
export interface EntradaDeLaCompra {
	idEntrada: number;
	numero: number;
	estado: EstadoEntrada;
}

// Una compra confirmada (CompraDelClienteResponse). La fecha es la vigente: si
// el evento se postergó, la nueva
export interface CompraDelCliente {
	idReserva: number;
	idEvento: number;
	nombreEvento: string;
	fecha: string;
	horaInicio: string;
	horaFin: string;
	lugar: string;
	estadoEvento: EstadoEvento;
	tipoEntrada: string;
	cantidad: number;
	entradas: EntradaDeLaCompra[];
}

// Ya ordenadas por el backend: las próximas del más cercano al más lejano, las
// pasadas del más reciente al más antiguo. Las de eventos cancelados van
// aparte, hayan terminado o no, del más reciente al más antiguo
export interface EntradasDelCliente {
	proximas: CompraDelCliente[];
	pasadas: CompraDelCliente[];
	canceladas: CompraDelCliente[];
}

// El detalle (EntradaResponse). codigoQr es null si la entrada ya no sirve para
// entrar: utilizada, devuelta o de un evento cancelado
export interface Entrada {
	idEntrada: number;
	numero: number;
	cantidad: number;
	estado: EstadoEntrada;
	fechaEmision: string;
	codigoQr: string | null;
	idEvento: number;
	nombreEvento: string;
	fecha: string;
	horaInicio: string;
	horaFin: string;
	lugar: string;
	estadoEvento: EstadoEvento;
	tipoEntrada: string;
}

const TEXTOS_DE_ESTADO: Record<EstadoEntrada, string> = {
	ACTIVA: 'Activa',
	UTILIZADA: 'Utilizada',
	DEVUELTA: 'Devuelta',
};

export function textoDelEstado(estado: EstadoEntrada): string {
	return TEXTOS_DE_ESTADO[estado];
}

// CU-08 RF-6: un dato de la consulta, no un aviso. Un evento activo no lleva
// etiqueta; uno en borrador no puede tener entradas
export function etiquetaDelEvento(estado: EstadoEvento): string | null {
	switch (estado) {
		case 'POSTERGADO':
			return 'Postergado';
		case 'CANCELADO':
			return 'Cancelado';
		default:
			return null;
	}
}

// CU-08 RF-9: por qué no se muestra el QR. El estado de la entrada va antes que
// el del evento: una devuelta de un evento cancelado dice que fue devuelta
export function motivoSinQr(entrada: Entrada): string | null {
	if (entrada.codigoQr !== null) {
		return null;
	}
	if (entrada.estado === 'DEVUELTA') {
		return 'Esta entrada fue devuelta.';
	}
	if (entrada.estado === 'UTILIZADA') {
		return 'Esta entrada ya se usó.';
	}
	if (entrada.estadoEvento === 'CANCELADO') {
		return 'El evento se canceló.';
	}
	// No inventa un motivo que los datos no muestran
	return 'Esta entrada no tiene un código para mostrar.';
}
