import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';

import { SesionService } from '../sesion/sesion.service';

const API = '/api/';
const PUBLICAS = ['/api/auth/', '/api/visitas/'];

// Agrega el token a las peticiones de la API, salvo a las de autenticación y al
// contador de visitas: se atienden sin sesión, y un token vencido en el header
// solo estorba (en el contador, cerraría la sesión por contar una visita).
export const tokenInterceptor: HttpInterceptorFn = (peticion, siguiente) => {
	const token = inject(SesionService).token();
	const esDeLaApi = peticion.url.startsWith(API);
	const esPublica = PUBLICAS.some((prefijo) => peticion.url.startsWith(prefijo));

	if (token === null || !esDeLaApi || esPublica) {
		return siguiente(peticion);
	}

	return siguiente(
		peticion.clone({ setHeaders: { Authorization: `Bearer ${token}` } }),
	);
};
