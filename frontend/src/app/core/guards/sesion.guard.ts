import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { destinoInterno } from '../../shared/navegacion/destino-interno';
import { SesionService } from '../sesion/sesion.service';

// Evita mostrar una pantalla que el backend iba a rechazar. No es una medida de
// seguridad: quien autoriza es el backend (CLAUDE.md → Usuarios y roles).
export const sesionGuard: CanActivateFn = (_ruta, estado) => {
	const sesion = inject(SesionService);
	const router = inject(Router);

	if (sesion.autenticado()) {
		return true;
	}

	return router.createUrlTree(['/inicio-sesion'], {
		queryParams: { destino: estado.url },
	});
};

// Para iniciar sesión y crear cuenta: quien ya entró va a donde quería ir, o a la
// portada. El destino se acepta solo si es del sitio
export const sinSesionGuard: CanActivateFn = (ruta) => {
	if (!inject(SesionService).autenticado()) {
		return true;
	}
	return inject(Router).parseUrl(destinoInterno(ruta.queryParamMap.get('destino')) ?? '/');
};
