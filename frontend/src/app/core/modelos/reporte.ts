// Espejo de /api/reportes (CU-14). Solo el Administrador. Un reporte es un
// resumen y un detalle, cada uno una tabla genérica: las columnas dicen de qué
// tipo es cada valor y la pantalla formatea según eso

export type TipoReporte = 'VENTAS' | 'INGRESOS' | 'ENTRADAS' | 'CLIENTES' | 'ACCESOS';

export const TIPOS_DE_REPORTE: readonly TipoReporte[] = ['VENTAS', 'INGRESOS', 'ENTRADAS', 'CLIENTES', 'ACCESOS'];

export const NOMBRES_DE_REPORTE: Record<TipoReporte, string> = {
	VENTAS: 'Ventas',
	INGRESOS: 'Ingresos',
	ENTRADAS: 'Entradas',
	CLIENTES: 'Clientes',
	ACCESOS: 'Accesos',
};

// Los nombres de los campos en la API
export type Criterio = 'desde' | 'hasta' | 'idEvento' | 'idTipoEntrada' | 'estado' | 'idPersonal';

// Los filtros de cada tipo (decisiones.md → Criterios de filtro por tipo). Es
// usabilidad: quien valida qué criterios admite cada reporte es el backend
export const CRITERIOS_POR_TIPO: Record<TipoReporte, readonly Criterio[]> = {
	VENTAS: ['desde', 'hasta', 'idEvento', 'idTipoEntrada'],
	INGRESOS: ['desde', 'hasta', 'idEvento'],
	ENTRADAS: ['idEvento', 'idTipoEntrada', 'estado'],
	CLIENTES: ['desde', 'hasta', 'idEvento'],
	ACCESOS: ['desde', 'hasta', 'idEvento', 'idPersonal'],
};

export type TipoDato = 'TEXTO' | 'ENTERO' | 'MONTO' | 'FECHA' | 'FECHA_HORA';

export interface Columna {
	titulo: string;
	tipo: TipoDato;
}

// Las fechas llegan como texto ISO; los montos y las cantidades, como números
export type Valor = string | number | null;

export interface Tabla {
	columnas: Columna[];
	filas: Valor[][];
	// El detalle no lleva totales
	totales: Valor[] | null;
}

export interface CriterioAplicado {
	nombre: string;
	valor: string;
}

export interface Reporte {
	idReporte: number;
	tipo: TipoReporte;
	titulo: string;
	fechaGeneracion: string;
	criterios: CriterioAplicado[];
	resumen: Tabla;
	detalle: Tabla;
}

// Solo viajan los criterios del tipo que tienen valor (FA-01: ninguno)
export type SolicitudDeReporte = { tipo: TipoReporte } & Partial<Record<Criterio, string | number>>;

export interface TipoDeEntradaOpcion {
	idTipoEntrada: number;
	nombre: string;
}

export interface EventoOpcion {
	idEvento: number;
	nombre: string;
	fecha: string;
	tipos: TipoDeEntradaOpcion[];
}

export interface PersonalOpcion {
	idUsuario: number;
	nombre: string;
}

// Las opciones de los filtros: los eventos que no están en borrador, con sus
// tipos, y el Personal que validó alguna entrada. También el dominio al que se
// puede mandar un reporte por correo
export interface OpcionesDeReporte {
	eventos: EventoOpcion[];
	personal: PersonalOpcion[];
	dominioDeCorreo: string;
}

export type FormatoDeArchivo = 'pdf' | 'excel';

// CU-14 RF-19 a RF-24: el envío del reporte ya generado a buzones del dominio
// propio. Los formatos van con mayúsculas como los enum de la API
export type FormatoDeEnvio = 'PDF' | 'EXCEL' | 'AMBOS';

export interface SolicitudDeEnvio {
	destinatarios: string[];
	formato: FormatoDeEnvio;
}

// A cuántos buzones salió el correo: los archivos van adjuntos, no en la respuesta
export interface EnvioDeReporte {
	destinatarios: number;
}
