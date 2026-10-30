// Espejo de /api/reservas/{id}/pago (CU-07). No trae la referencia del sistema
// de pago ni su nombre: la pantalla no los necesita

import { EstadoReserva } from './reserva';

// EXPIRADO: la reserva venció antes de que llegara el pago
export type EstadoPago = 'PENDIENTE' | 'PAGADO' | 'EXPIRADO' | 'ERROR';

// Lo que se puede forzar en el simulador (herramienta de pruebas)
export type ResultadoSimulado = 'PAGADO' | 'ERROR';

// El cobro de una reserva y cómo va (PagoResponse). codigoQr es el texto del QR
// de cobro; simulador avisa que la pantalla puede mostrar los botones de prueba
export interface Pago {
	idReserva: number;
	estado: EstadoPago;
	estadoReserva: EstadoReserva;
	codigoQr: string;
	urlPasarela: string | null;
	segundosRestantes: number;
	requiereDevolucion: boolean;
	entradas: number;
	simulador: boolean;
}
