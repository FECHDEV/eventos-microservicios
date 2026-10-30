// Espejo de /api/devoluciones (CU-13). Solo el Administrador. Las entradas van
// sin su código: para devolver alcanza con su número dentro de la compra

// Un evento cancelado y cuántas entradas tiene pendientes de devolución
export interface EventoCancelado {
	idEvento: number;
	nombre: string;
	fecha: string;
	horaInicio: string;
	horaFin: string;
	lugar: string;
	pendientes: number;
}

export interface EntradaParaDevolucion {
	idEntrada: number;
	numero: number;
}

// Una compra con lo que hace falta para devolver el dinero fuera del sistema.
// La cantidad es la de toda la compra; las entradas, solo las de la lista en la
// que aparece (pendientes o devueltas)
export interface CompraParaDevolucion {
	idReserva: number;
	cliente: string;
	correo: string;
	telefono: string | null;
	tipoEntrada: string;
	cantidad: number;
	monto: number;
	// El congelado al reservar: lo que se devuelve por cada entrada
	precioUnitario: number;
	fechaPago: string | null;
	entradas: EntradaParaDevolucion[];
}

export interface DevolucionesDelEvento {
	idEvento: number;
	nombre: string;
	fecha: string;
	horaInicio: string;
	horaFin: string;
	lugar: string;
	pendientes: CompraParaDevolucion[];
	devueltas: CompraParaDevolucion[];
}

// Cuántas entradas quedaron registradas como devueltas
export interface RegistroDevolucion {
	registradas: number;
}
