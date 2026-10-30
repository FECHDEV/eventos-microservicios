// Espejo de /api/reservas (CU-06). Una compra es de un solo tipo de entrada: no
// hay carrito (docs/decisiones.md → Una compra es de un solo tipo de entrada)

// Lo que el Cliente elige (ReservaRequest). El Cliente sale del token
export interface SolicitudDeReserva {
	idTipoEntrada: number;
	cantidad: number;
}

// CONFIRMADA la asigna el pago (CU-07)
export type EstadoReserva = 'VIGENTE' | 'EXPIRADA' | 'CONFIRMADA';

// La reserva para el Cliente (ReservaResponse). segundosRestantes los calcula el
// servidor: la cuenta regresiva no depende del reloj del navegador
export interface Reserva {
	idReserva: number;
	idEvento: number;
	nombreEvento: string;
	fecha: string;
	horaInicio: string;
	horaFin: string;
	lugar: string;
	idTipoEntrada: number;
	tipoEntrada: string;
	cantidad: number;
	precioUnitario: number;
	total: number;
	fechaExpiracion: string;
	segundosRestantes: number;
	estado: EstadoReserva;
}
