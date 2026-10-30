import { HttpClient, httpResource, HttpResourceRef } from '@angular/common/http';
import { inject, Injectable, Signal } from '@angular/core';
import { DevolucionesDelEvento, EventoCancelado, RegistroDevolucion } from '../modelos/devolucion';
import { escribir } from './escribir';

// Llamadas a /api/devoluciones (CU-13). Solo el Administrador llega: el backend
// lo valida en cada petición, la pantalla solo evita mostrarse a otros roles.
@Injectable({ providedIn: 'root' })
export class DevolucionesService {
	private readonly http = inject(HttpClient);

	// httpResource necesita un contexto de inyección: se llaman al construir la
	// pantalla. Sin id no se pide nada.

	eventosCancelados(): HttpResourceRef<EventoCancelado[] | undefined> {
		return httpResource<EventoCancelado[]>(() => '/api/devoluciones');
	}

	delEvento(idEvento: Signal<number | null>): HttpResourceRef<DevolucionesDelEvento | undefined> {
		return httpResource<DevolucionesDelEvento>(() => {
			const id = idEvento();
			return id === null ? undefined : `/api/devoluciones/${id}`;
		});
	}

	// Si el backend rechaza, fallan con el ErrorApi ya armado (FA-02: ENTRADA_YA_DEVUELTA)

	registrarCompra(idReserva: number): Promise<RegistroDevolucion> {
		return escribir(this.http.post<RegistroDevolucion>(`/api/devoluciones/compras/${idReserva}`, null));
	}

	registrarEntrada(idEntrada: number): Promise<RegistroDevolucion> {
		return escribir(this.http.post<RegistroDevolucion>(`/api/devoluciones/entradas/${idEntrada}`, null));
	}
}
