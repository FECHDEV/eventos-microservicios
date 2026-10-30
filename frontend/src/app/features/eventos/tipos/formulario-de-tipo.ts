import { maxLength, required, schema, validate } from '@angular/forms/signals';

import { DatosDeTipoEntrada, TipoEntrada } from '../../../core/modelos/tipo-entrada';
import { sinEspaciosSolos } from '../../../shared/formularios/reglas-de-campo';

// El formulario de un tipo de entrada (CU-11). Los campos son texto: el precio
// se escribe con coma, como se lee en Bolivia, y un <input type="number"> la
// rechaza en algunos navegadores. Se convierten al enviar.
export interface FormularioDeTipo {
	nombre: string;
	precio: string;
	cantidadTotal: string;
}

// El orden en que aparecen en pantalla: el foco va al primero que falle
export const CAMPOS_DEL_TIPO: readonly (keyof FormularioDeTipo)[] = ['nombre', 'precio', 'cantidadTotal'];

export const TIPO_VACIO: FormularioDeTipo = { nombre: '', precio: '', cantidadTotal: '' };

// Hasta 8 cifras enteras y 2 decimales, con coma o con punto (DECIMAL(10,2))
const PRECIO = /^\d{1,8}([.,]\d{1,2})?$/;

export function esPrecioValido(precio: string): boolean {
	return PRECIO.test(precio) && Number(precio.replace(',', '.')) > 0;
}

export function aFormularioDeTipo(tipo: TipoEntrada | undefined): FormularioDeTipo {
	if (tipo === undefined) {
		return { ...TIPO_VACIO };
	}
	return {
		nombre: tipo.nombre,
		precio: tipo.precio.toFixed(2).replace('.', ','),
		cantidadTotal: String(tipo.cantidadTotal),
	};
}

export function aDatosDeTipo(formulario: FormularioDeTipo): DatosDeTipoEntrada {
	return {
		nombre: formulario.nombre,
		precio: Number(formulario.precio.replace(',', '.')),
		cantidadTotal: Number(formulario.cantidadTotal),
	};
}

// Lo que el esquema necesita saber del tipo que se edita
export interface ContextoDelTipo {
	// Lo ya vendido y reservado: la cantidad total no puede bajar de ahí (0 al agregar)
	minimo(): number;
}

// Las reglas de TipoEntradaRequest y la del mínimo del servicio (CU-11 RF-8 a
// RF-11 y RF-15), para avisar antes de enviar. Quien decide sigue siendo el backend.
export function esquemaDelTipo(contexto: ContextoDelTipo) {
	return schema<FormularioDeTipo>((ruta) => {
		required(ruta.nombre, { message: 'Escribe el nombre del tipo de entrada.' });
		sinEspaciosSolos(ruta.nombre, 'Escribe el nombre del tipo de entrada.');
		maxLength(ruta.nombre, 80, { message: 'El nombre admite hasta 80 caracteres.' });

		required(ruta.precio, { message: 'Escribe el precio.' });
		validate(ruta.precio, ({ value }) =>
			value() === '' || esPrecioValido(value())
				? null
				: { kind: 'precio', message: 'El precio tiene que ser mayor que 0, con hasta 2 decimales.' },
		);

		required(ruta.cantidadTotal, { message: 'Escribe la cantidad de entradas.' });
		validate(ruta.cantidadTotal, ({ value }) => {
			if (value() === '') {
				return null;
			}
			if (!/^\d{1,9}$/.test(value()) || Number(value()) < 1) {
				return { kind: 'cantidad', message: 'La cantidad es de 1 entrada o más.' };
			}
			const minimo = contexto.minimo();
			return Number(value()) < minimo
				? { kind: 'minimo', message: `No puede ser menor que ${minimo}, lo ya vendido y reservado.` }
				: null;
		});
	});
}
