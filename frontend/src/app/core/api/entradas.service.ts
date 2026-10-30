import { httpResource, HttpResourceRef } from '@angular/common/http';
import { Injectable, Signal } from '@angular/core';

import { Entrada, EntradasDelCliente } from '../modelos/entrada';

// Llamadas a /api/entradas (CU-08): solo lectura y solo el Cliente, con sus
// propias entradas. El backend lo valida en cada petición; la pantalla solo
// evita mostrarse a otros roles.
@Injectable({ providedIn: 'root' })
export class EntradasService {
	// httpResource necesita un contexto de inyección: se llaman al construir la
	// pantalla. Sin id no se pide nada.

	entradas(): HttpResourceRef<EntradasDelCliente | undefined> {
		return httpResource<EntradasDelCliente>(() => '/api/entradas');
	}

	entrada(idEntrada: Signal<number | null>): HttpResourceRef<Entrada | undefined> {
		return httpResource<Entrada>(() => {
			const id = idEntrada();
			return id === null ? undefined : `/api/entradas/${id}`;
		});
	}
}
