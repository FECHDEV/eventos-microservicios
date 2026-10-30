import { HttpClient, httpResource, HttpResourceRef } from '@angular/common/http';
import { inject, Injectable, Signal } from '@angular/core';

import { DatosDeEvento, Evento, EventoActivado, EventoResumen, FiltrosDeEventos } from '../modelos/evento';
import { Pagina } from '../modelos/pagina';
import { DatosDeTipoEntrada, TipoEntrada } from '../modelos/tipo-entrada';
import { escribir } from './escribir';

// Llamadas a /api/eventos: el evento (CU-10), sus tipos de entrada (CU-11) y su
// estado (CU-12). Solo el administrador llega: el backend lo valida en cada
// petición, la pantalla solo evita mostrarse a otros roles.
@Injectable({ providedIn: 'root' })
export class EventosService {
	private readonly http = inject(HttpClient);

	// --- Lecturas. httpResource necesita un contexto de inyección: se llaman al
	// construir la pantalla. Cada cambio cancela la petición anterior, así una
	// respuesta que llega tarde no pisa la nueva. Sin id no se pide nada.

	listado(filtros: Signal<FiltrosDeEventos>): HttpResourceRef<Pagina<EventoResumen> | undefined> {
		return httpResource<Pagina<EventoResumen>>(() => ({
			url: '/api/eventos',
			params: parametros(filtros()),
		}));
	}

	evento(idEvento: Signal<number | null>): HttpResourceRef<Evento | undefined> {
		return httpResource<Evento>(() => {
			const id = idEvento();
			return id === null ? undefined : `/api/eventos/${id}`;
		});
	}

	tipos(idEvento: Signal<number | null>): HttpResourceRef<TipoEntrada[] | undefined> {
		return httpResource<TipoEntrada[]>(() => {
			const id = idEvento();
			return id === null ? undefined : `/api/eventos/${id}/tipos-entrada`;
		});
	}

	// El <img> no puede mandar el token: la imagen se trae como Blob y la pantalla
	// la muestra con una URL de objeto
	imagen(idEvento: Signal<number | null>): HttpResourceRef<Blob | undefined> {
		return httpResource.blob(() => {
			const id = idEvento();
			return id === null ? undefined : `/api/eventos/${id}/imagen`;
		});
	}

	// --- El evento (CU-10)

	registrar(datos: DatosDeEvento): Promise<Evento> {
		return escribir(this.http.post<Evento>('/api/eventos', datos));
	}

	modificar(idEvento: number, datos: DatosDeEvento): Promise<Evento> {
		return escribir(this.http.put<Evento>(`/api/eventos/${idEvento}`, datos));
	}

	// El nombre del archivo viaja pero el backend lo descarta (CU-10 RF-29)
	subirImagen(idEvento: number, archivo: File): Promise<Evento> {
		const cuerpo = new FormData();
		cuerpo.append('imagen', archivo);
		return escribir(this.http.post<Evento>(`/api/eventos/${idEvento}/imagen`, cuerpo));
	}

	quitarImagen(idEvento: number): Promise<Evento> {
		return escribir(this.http.delete<Evento>(`/api/eventos/${idEvento}/imagen`));
	}

	// --- Sus tipos de entrada (CU-11)

	registrarTipo(idEvento: number, datos: DatosDeTipoEntrada): Promise<TipoEntrada> {
		return escribir(this.http.post<TipoEntrada>(`/api/eventos/${idEvento}/tipos-entrada`, datos));
	}

	modificarTipo(idEvento: number, idTipoEntrada: number, datos: DatosDeTipoEntrada): Promise<TipoEntrada> {
		return escribir(
			this.http.put<TipoEntrada>(`/api/eventos/${idEvento}/tipos-entrada/${idTipoEntrada}`, datos),
		);
	}

	// --- Su estado (CU-12). Las cuatro operaciones devuelven el evento actualizado;
	// activar, además, cuántas entradas usadas reactivó

	activar(idEvento: number): Promise<EventoActivado> {
		return escribir(this.http.post<EventoActivado>(`/api/eventos/${idEvento}/activacion`, null));
	}

	postergar(idEvento: number, fecha: string): Promise<Evento> {
		return escribir(this.http.post<Evento>(`/api/eventos/${idEvento}/postergacion`, { fecha }));
	}

	cancelar(idEvento: number): Promise<Evento> {
		return escribir(this.http.post<Evento>(`/api/eventos/${idEvento}/cancelacion`, null));
	}

	cerrarVenta(idEvento: number): Promise<Evento> {
		return escribir(this.http.post<Evento>(`/api/eventos/${idEvento}/cierre-de-venta`, null));
	}
}

// Las escrituras devuelven una promesa que, si el backend rechaza, falla con el
// ErrorApi ya armado, igual que en los demás servicios
// Solo viaja lo que filtra: sin estado son todos y la página 0 es la que el
// backend usa por defecto
function parametros(filtros: FiltrosDeEventos): Record<string, string> {
	const resultado: Record<string, string> = {};
	if (filtros.estado !== null) {
		resultado['estado'] = filtros.estado;
	}
	if (filtros.pagina > 0) {
		resultado['pagina'] = String(filtros.pagina);
	}
	return resultado;
}
