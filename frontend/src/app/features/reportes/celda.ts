import { TipoDato, Valor } from '../../core/modelos/reporte';
import { fechaCorta, horaCorta } from '../../shared/formato/fechas';
import { cantidad, precio } from '../../shared/formato/precios';

// Un valor del reporte como se lee en Bolivia, igual que en el PDF
// (backend: FormatoDeCeldas). Un texto en una columna de otro tipo («Total» al
// pie) va tal cual
export function formatearCelda(valor: Valor, tipo: TipoDato): string {
	if (valor === null) {
		return '—';
	}
	if (typeof valor === 'number') {
		switch (tipo) {
			case 'MONTO':
				return precio(valor);
			case 'ENTERO':
				return cantidad(valor);
			default:
				return String(valor);
		}
	}
	switch (tipo) {
		case 'FECHA':
			return esFecha(valor) ? fechaCorta(valor) : valor;
		case 'FECHA_HORA':
			return esFecha(valor) ? `${fechaCorta(valor)} ${horaCorta(valor.slice(11))}` : valor;
		default:
			return valor;
	}
}

function esFecha(valor: string): boolean {
	return /^\d{4}-\d{2}-\d{2}/.test(valor);
}

// Las cifras se centran bajo su título
export function esNumerica(tipo: TipoDato): boolean {
	return tipo === 'ENTERO' || tipo === 'MONTO';
}
