import { ParamMap } from '@angular/router';

import { paginaDeLaUrl } from '../../shared/navegacion/pagina-de-la-url';
import { FiltrosDelCatalogo } from './catalogo';

// Los filtros del catálogo (CU-05) viven en la URL de la portada. Los leen y los
// escriben el catálogo y el buscador del encabezado: en un solo lugar, para que
// nunca digan cosas distintas

// El lugar admite hasta 200 caracteres: un texto más largo no coincide con nada
// y el backend lo rechaza (CatalogoService.LARGO_MAXIMO_TEXTO)
export const LARGO_MAXIMO_DEL_TEXTO = 200;

// Una fecha ISO real (yyyy-MM-dd); cualquier otra cosa en la URL se descarta
function fechaDeLaUrl(valor: string | null): string | null {
	if (valor === null || !/^\d{4}-\d{2}-\d{2}$/.test(valor)) {
		return null;
	}
	const fecha = new Date(`${valor}T00:00:00Z`);
	return !Number.isNaN(fecha.getTime()) && fecha.toISOString().startsWith(valor) ? valor : null;
}

export function leerFiltros(consulta: ParamMap): FiltrosDelCatalogo {
	const texto = consulta.get('texto')?.trim() ?? '';
	return {
		texto: texto === '' ? null : texto,
		desde: fechaDeLaUrl(consulta.get('desde')),
		hasta: fechaDeLaUrl(consulta.get('hasta')),
		pagina: paginaDeLaUrl(consulta.get('pagina')),
	};
}

// Solo lo que filtra queda en la URL, así la portada sin filtros es /
export function aConsulta(filtros: FiltrosDelCatalogo): Record<string, string | number | undefined> {
	return {
		texto: filtros.texto ?? undefined,
		desde: filtros.desde ?? undefined,
		hasta: filtros.hasta ?? undefined,
		pagina: filtros.pagina > 0 ? filtros.pagina : undefined,
	};
}
