import { required, schema, validate } from '@angular/forms/signals';

// El formulario de la compra (CU-06 paso 1). La cantidad es texto, como en
// CU-11: un <input type="number"> acepta cosas como «1e3» y en algunos
// navegadores no deja leer lo escrito. Se convierte al enviar.
export interface FormularioDeCompra {
	cantidad: string;
}

// Un entero de 1 o más, con hasta 9 cifras para que entre en un entero del backend
export function esCantidadValida(cantidad: string): boolean {
	return /^\d{1,9}$/.test(cantidad) && Number(cantidad) >= 1;
}

// Lo que el esquema necesita saber del evento
export interface ContextoDeCompra {
	// El límite por cliente, o null si no tiene
	limite(): number | null;
}

// Aviso previo, para no enviar lo que el backend va a rechazar. El límite real
// es acumulado (cuenta las reservas que ya tiene): eso solo lo sabe el backend
export function esquemaDeCompra(contexto: ContextoDeCompra) {
	return schema<FormularioDeCompra>((ruta) => {
		required(ruta.cantidad, { message: 'Escribe la cantidad de entradas.' });
		validate(ruta.cantidad, ({ value }) => {
			if (value() === '') {
				return null;
			}
			if (!esCantidadValida(value())) {
				return { kind: 'cantidad', message: 'La cantidad es un número entero de 1 o más.' };
			}
			const limite = contexto.limite();
			return limite !== null && Number(value()) > limite
				? { kind: 'limite', message: `Puedes comprar hasta ${limite} entradas en este evento.` }
				: null;
		});
	});
}
