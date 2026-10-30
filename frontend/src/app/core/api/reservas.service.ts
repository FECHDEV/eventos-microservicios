import { HttpClient, httpResource, HttpResourceRef } from '@angular/common/http';
import { inject, Injectable, Signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { Pago, ResultadoSimulado } from '../modelos/pago';
import { Reserva, SolicitudDeReserva } from '../modelos/reserva';
import { comoErrorApi } from './autenticacion.service';

// Llamadas a /api/reservas (CU-06) y al pago de cada reserva (CU-07). Solo el Cliente llega: el backend lo valida
// en cada petición, la pantalla solo evita mostrarse a otros roles.
@Injectable({ providedIn: 'root' })
export class ReservasService {
	private readonly http = inject(HttpClient);

	// httpResource necesita un contexto de inyección: se llama al construir la
	// pantalla. Sin id no se pide nada.
	reserva(idReserva: Signal<number | null>): HttpResourceRef<Reserva | undefined> {
		return httpResource<Reserva>(() => {
			const id = idReserva();
			return id === null ? undefined : `/api/reservas/${id}`;
		});
	}

	// Si el backend rechaza, falla con el ErrorApi ya armado, con los datos que
	// explican el rechazo (la edad mínima, cuántas puede agregar)
	async reservar(solicitud: SolicitudDeReserva): Promise<Reserva> {
		try {
			return await firstValueFrom(this.http.post<Reserva>('/api/reservas', solicitud));
		} catch (error) {
			throw comoErrorApi(error);
		}
	}

	// CU-07 paso 1: genera el cobro, o devuelve el que la reserva ya tiene
	async pagar(idReserva: number): Promise<Pago> {
		try {
			return await firstValueFrom(this.http.post<Pago>(`/api/reservas/${idReserva}/pago`, null));
		} catch (error) {
			throw comoErrorApi(error);
		}
	}

	// Una promesa y no un httpResource: la pantalla la repite cada pocos segundos
	// mientras el pago está pendiente. El backend verifica contra el sistema de pago
	async consultarPago(idReserva: number): Promise<Pago> {
		try {
			return await firstValueFrom(this.http.get<Pago>(`/api/reservas/${idReserva}/pago`));
		} catch (error) {
			throw comoErrorApi(error);
		}
	}

	// Solo existe con el simulador de pago (herramienta de pruebas)
	async simularPago(idReserva: number, resultado: ResultadoSimulado): Promise<void> {
		try {
			await firstValueFrom(this.http.post<void>(`/api/reservas/${idReserva}/pago/simulacion`, { resultado }));
		} catch (error) {
			throw comoErrorApi(error);
		}
	}
}
