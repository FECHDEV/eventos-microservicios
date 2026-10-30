import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { PaginaVisitada } from '../modelos/visitas';

// Llamadas a /api/visitas: el contador del pie de cada pantalla, con o sin
// sesión. Un Observable y no una promesa: el pie cancela la cuenta de la página
// anterior si se cambia de pantalla antes de la respuesta
@Injectable({ providedIn: 'root' })
export class VisitasService {
	private readonly http = inject(HttpClient);

	registrar(pagina: PaginaVisitada): Observable<number> {
		return this.http.post<{ total: number }>(`/api/visitas/${pagina}`, null).pipe(map((respuesta) => respuesta.total));
	}
}
