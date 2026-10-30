import { HttpClient, httpResource, HttpResourceRef } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import { EventoParaValidar, ResultadoValidacion } from '../modelos/acceso';
import { escribir } from './escribir';

// Llamadas a /api/accesos (CU-15). Solo el Personal autorizado llega: el
// backend lo valida en cada petición, la pantalla solo evita mostrarse a otros
// roles.
@Injectable({ providedIn: 'root' })
export class AccesosService {
	private readonly http = inject(HttpClient);

	// httpResource necesita un contexto de inyección: se llama al construir la pantalla
	eventosParaValidar(): HttpResourceRef<EventoParaValidar[] | undefined> {
		return httpResource<EventoParaValidar[]>(() => '/api/accesos/eventos');
	}

	// Un rechazo de la entrada vuelve como resultado; falla solo si el pedido no
	// tiene sentido (EVENTO_NO_DISPONIBLE) o no llega
	validar(idEvento: number, codigo: string): Promise<ResultadoValidacion> {
		return escribir(this.http.post<ResultadoValidacion>('/api/accesos', { idEvento, codigo }));
	}
}
