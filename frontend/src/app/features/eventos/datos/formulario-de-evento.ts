import { disabled, maxLength, required, schema, validate } from '@angular/forms/signals';

import { DatosDeEvento, Evento } from '../../../core/modelos/evento';
import { sinEspaciosSolos } from '../../../shared/formularios/reglas-de-campo';
import { bytesUtf8 } from '../../../shared/validacion/reglas';

// El formulario de datos de un evento (CU-10). Todos los campos son texto,
// como los dan los controles: la edad y el límite vacíos significan «sin
// restricción» y «sin límite», y un número vacío no existe (Signal Forms pide
// no usar null en el modelo). Se convierten al enviar.
export interface FormularioDeEvento {
	nombre: string;
	descripcion: string;
	fecha: string;
	horaInicio: string;
	horaFin: string;
	lugar: string;
	restriccionEdad: string;
	limiteCompra: string;
}

// El orden en que aparecen en pantalla: el foco va al primero que falle
export const CAMPOS_DEL_EVENTO: readonly (keyof FormularioDeEvento)[] = [
	'nombre',
	'descripcion',
	'fecha',
	'horaInicio',
	'horaFin',
	'lugar',
	'restriccionEdad',
	'limiteCompra',
];

export const FORMULARIO_VACIO: FormularioDeEvento = {
	nombre: '',
	descripcion: '',
	fecha: '',
	horaInicio: '',
	horaFin: '',
	lugar: '',
	restriccionEdad: '',
	limiteCompra: '',
};

// Las horas del backend traen segundos; el <input type="time"> usa HH:mm
export function aFormulario(evento: Evento | undefined): FormularioDeEvento {
	if (evento === undefined) {
		return { ...FORMULARIO_VACIO };
	}
	return {
		nombre: evento.nombre,
		descripcion: evento.descripcion ?? '',
		fecha: evento.fecha,
		horaInicio: evento.horaInicio.slice(0, 5),
		horaFin: evento.horaFin.slice(0, 5),
		lugar: evento.lugar,
		restriccionEdad: evento.restriccionEdad === null ? '' : String(evento.restriccionEdad),
		limiteCompra: evento.limiteCompra === null ? '' : String(evento.limiteCompra),
	};
}

export function aDatos(formulario: FormularioDeEvento): DatosDeEvento {
	return {
		nombre: formulario.nombre,
		descripcion: formulario.descripcion.trim() === '' ? null : formulario.descripcion,
		fecha: formulario.fecha,
		horaInicio: formulario.horaInicio,
		horaFin: formulario.horaFin,
		lugar: formulario.lugar,
		restriccionEdad: formulario.restriccionEdad === '' ? null : Number(formulario.restriccionEdad),
		limiteCompra: formulario.limiteCompra === '' ? null : Number(formulario.limiteCompra),
	};
}

// Lo que el esquema necesita saber del evento y del día
export interface ContextoDelFormulario {
	// AAAA-MM-DD de hoy, en el calendario de quien usa la pantalla
	hoy(): string;
	// La fecha que el evento ya tiene (null al registrar): conservarla no se
	// valida contra hoy, porque la regla es sobre la fecha que se elige (RF-21)
	fechaCargada(): string | null;
	// ACTIVO o POSTERGADO: la fecha y las horas no se tocan, eso es postergar
	horarioBloqueado(): boolean;
	// CANCELADO: no se edita nada
	soloLectura(): boolean;
}

// Las reglas de EventoRequest y las del servicio (CU-10 RF-8 a RF-13 y RF-21),
// para avisar antes de enviar. Quien decide sigue siendo el backend.
export function esquemaDelEvento(contexto: ContextoDelFormulario) {
	return schema<FormularioDeEvento>((ruta) => {
		disabled(ruta, { when: () => contexto.soloLectura() });
		disabled(ruta.fecha, { when: () => contexto.horarioBloqueado() });
		disabled(ruta.horaInicio, { when: () => contexto.horarioBloqueado() });
		disabled(ruta.horaFin, { when: () => contexto.horarioBloqueado() });

		required(ruta.nombre, { message: 'Escribe el nombre del evento.' });
		sinEspaciosSolos(ruta.nombre, 'Escribe el nombre del evento.');
		maxLength(ruta.nombre, 150, { message: 'El nombre admite hasta 150 caracteres.' });

		// La columna es TEXT: 65 535 bytes, no caracteres
		validate(ruta.descripcion, ({ value }) =>
			bytesUtf8(value()) > 65_535 ? { kind: 'descripcionLarga', message: 'La descripción es demasiado larga.' } : null,
		);

		required(ruta.fecha, { message: 'Elige la fecha del evento.' });
		validate(ruta.fecha, ({ value }) =>
			value() !== '' && value() !== contexto.fechaCargada() && value() < contexto.hoy()
				? { kind: 'fechaPasada', message: 'La fecha no puede ser anterior a hoy.' }
				: null,
		);

		required(ruta.horaInicio, { message: 'Elige la hora de inicio.' });
		required(ruta.horaFin, { message: 'Elige la hora de fin.' });
		// Si es menor que la de inicio, el evento termina al día siguiente; iguales no
		validate(ruta.horaFin, ({ value, valueOf }) =>
			value() !== '' && value() === valueOf(ruta.horaInicio)
				? { kind: 'horasIguales', message: 'La hora de fin tiene que ser distinta de la de inicio.' }
				: null,
		);

		required(ruta.lugar, { message: 'Escribe el lugar.' });
		sinEspaciosSolos(ruta.lugar, 'Escribe el lugar.');
		maxLength(ruta.lugar, 200, { message: 'El lugar admite hasta 200 caracteres.' });

		// Vacío es «sin restricción»; con valor, de 1 a 99 (decisiones.md)
		validate(ruta.restriccionEdad, ({ value }) =>
			value() === '' || (/^\d{1,2}$/.test(value()) && Number(value()) >= 1)
				? null
				: { kind: 'edad', message: 'La edad mínima va de 1 a 99 años.' },
		);
		// Vacío es «sin límite»; con valor, 1 o más, dentro del int del backend
		validate(ruta.limiteCompra, ({ value }) =>
			value() === '' || (/^\d{1,9}$/.test(value()) && Number(value()) >= 1)
				? null
				: { kind: 'limite', message: 'El límite por cliente es de 1 entrada o más.' },
		);
	});
}
