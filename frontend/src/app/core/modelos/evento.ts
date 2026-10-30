// Espejo de EventoResponse, EventoResumenResponse y EventoRequest del backend
// (servicios/*/src/main/java/com/sarao/*/dto/). Las fechas llegan como
// AAAA-MM-DD y las horas como HH:mm:ss, que es como Jackson escribe LocalDate y
// LocalTime.

export type EstadoEvento = 'BORRADOR' | 'ACTIVO' | 'POSTERGADO' | 'CANCELADO';

export const ESTADOS_DE_EVENTO: readonly EstadoEvento[] = ['BORRADOR', 'ACTIVO', 'POSTERGADO', 'CANCELADO'];

// Cómo se nombran en pantalla; en el código siguen siendo los del backend
export const NOMBRES_DE_ESTADO_DE_EVENTO: Record<EstadoEvento, string> = {
	BORRADOR: 'Borrador',
	ACTIVO: 'Activo',
	POSTERGADO: 'Postergado',
	CANCELADO: 'Cancelado',
};

// Una fila de la lista (CU-10 RF-3)
export interface EventoResumen {
	idEvento: number;
	nombre: string;
	fecha: string;
	horaInicio: string;
	horaFin: string;
	lugar: string;
	estado: EstadoEvento;
	ventaAbierta: boolean;
	tieneImagen: boolean;
}

// El detalle (CU-10 RF-6). La fecha original solo existe si se postergó
export interface Evento extends EventoResumen {
	descripcion: string | null;
	fechaOriginal: string | null;
	restriccionEdad: number | null;
	limiteCompra: number | null;
}

// Lo que responde la activación (CU-12): el evento y cuántas entradas usadas se
// reactivaron al volver de POSTERGADO (FA-02); 0 si venía de BORRADOR
export interface EventoActivado extends Evento {
	entradasReactivadas: number;
}

// Lo que se envía al registrar o modificar. Estado, venta y fecha original no
// están: son de CU-12 (CU-10 RF-20)
export interface DatosDeEvento {
	nombre: string;
	descripcion: string | null;
	fecha: string;
	horaInicio: string;
	horaFin: string;
	lugar: string;
	restriccionEdad: number | null;
	limiteCompra: number | null;
}

// Los filtros de la lista. null en estado es "todos"; la página empieza en 0,
// como en el backend
export interface FiltrosDeEventos {
	estado: EstadoEvento | null;
	pagina: number;
}

// Si la hora de fin es menor que la de inicio, el evento termina al día
// siguiente (decisiones.md → Fecha y horario). Se comparan HH:mm: el backend
// agrega los segundos y el formulario no.
export function terminaAlDiaSiguiente(horaInicio: string, horaFin: string): boolean {
	return horaInicio !== '' && horaFin !== '' && horaFin.slice(0, 5) < horaInicio.slice(0, 5);
}

// Espejo de Evento.seMuestraEnElCatalogo del backend: terminó si ya llegó su fin
// (fecha + hora de fin, un día después si cruza la medianoche). Solo cuenta para
// los que siguen a la venta: un borrador o un cancelado no «terminan». No es un
// estado: la administración lo muestra como etiqueta (decisiones.md → Catálogo
// de eventos)
export function yaTermino(
	evento: Pick<Evento, 'estado' | 'fecha' | 'horaInicio' | 'horaFin'>,
	ahora: Date,
): boolean {
	if (evento.estado !== 'ACTIVO' && evento.estado !== 'POSTERGADO') {
		return false;
	}
	const diaDelFin = terminaAlDiaSiguiente(evento.horaInicio, evento.horaFin)
		? diaSiguiente(evento.fecha)
		: evento.fecha;
	return momentoLocal(ahora) >= `${diaDelFin}T${evento.horaFin.slice(0, 5)}`;
}

// En UTC, desde el día del calendario: en hora local un cambio de horario podría
// correr la fecha
function diaSiguiente(fecha: string): string {
	const dia = new Date(`${fecha}T00:00:00Z`);
	dia.setUTCDate(dia.getUTCDate() + 1);
	return dia.toISOString().slice(0, 10);
}

// yyyy-MM-ddTHH:mm en la hora del navegador, comparable como texto
function momentoLocal(momento: Date): string {
	const dos = (valor: number) => String(valor).padStart(2, '0');
	return (
		`${momento.getFullYear()}-${dos(momento.getMonth() + 1)}-${dos(momento.getDate())}` +
		`T${dos(momento.getHours())}:${dos(momento.getMinutes())}`
	);
}
