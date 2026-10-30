import { httpResource, HttpResourceRef } from '@angular/common/http';
import { Injectable, Signal } from '@angular/core';

import { EventoDelCatalogo, EventoDelCatalogoResumen, FiltrosDelCatalogo } from '../modelos/catalogo';
import { Pagina } from '../modelos/pagina';

// Llamadas a /api/catalogo (CU-05): la parte pública, de solo lectura. No hace
// falta sesión; si hay token, el interceptor lo agrega igual y el backend lo
// ignora para esto.
@Injectable({ providedIn: 'root' })
export class CatalogoService {
	// httpResource necesita un contexto de inyección: se llaman al construir la
	// pantalla. Cada cambio cancela la petición anterior. Sin id no se pide nada.

	listado(filtros: Signal<FiltrosDelCatalogo>): HttpResourceRef<Pagina<EventoDelCatalogoResumen> | undefined> {
		return httpResource<Pagina<EventoDelCatalogoResumen>>(() => ({
			url: '/api/catalogo',
			params: parametros(filtros()),
		}));
	}

	evento(idEvento: Signal<number | null>): HttpResourceRef<EventoDelCatalogo | undefined> {
		return httpResource<EventoDelCatalogo>(() => {
			const id = idEvento();
			return id === null ? undefined : `/api/catalogo/${id}`;
		});
	}

	// La imagen es pública: va directo en un <img>, sin Blob ni token
	urlDeImagen(idEvento: number): string {
		return `/api/catalogo/${idEvento}/imagen`;
	}
}

// Solo viaja lo que filtra, así la primera página sin filtros es /api/catalogo
function parametros(filtros: FiltrosDelCatalogo): Record<string, string> {
	const resultado: Record<string, string> = {};
	const texto = filtros.texto?.trim() ?? '';
	if (texto !== '') {
		resultado['texto'] = texto;
	}
	if (filtros.desde !== null) {
		resultado['desde'] = filtros.desde;
	}
	if (filtros.hasta !== null) {
		resultado['hasta'] = filtros.hasta;
	}
	if (filtros.pagina > 0) {
		resultado['pagina'] = String(filtros.pagina);
	}
	return resultado;
}
