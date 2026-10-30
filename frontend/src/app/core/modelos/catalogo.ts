// Espejo de las respuestas públicas de /api/catalogo (CU-05). No traen el estado
// del evento ni cantidades: el cliente ve la fecha original si se postergó y si
// cada tipo está disponible (docs/decisiones.md → Catálogo de eventos)

// Una tarjeta del catálogo (EventoCatalogoResumenResponse)
export interface EventoDelCatalogoResumen {
	idEvento: number;
	nombre: string;
	fecha: string;
	fechaOriginal: string | null;
	horaInicio: string;
	horaFin: string;
	lugar: string;
	ventaAbierta: boolean;
	tieneImagen: boolean;
}

// Un tipo de entrada: si quedan, no cuántas (TipoEntradaCatalogoResponse)
export interface TipoEntradaDelCatalogo {
	idTipoEntrada: number;
	nombre: string;
	precio: number;
	disponible: boolean;
}

// El detalle de un evento (EventoCatalogoResponse)
export interface EventoDelCatalogo extends EventoDelCatalogoResumen {
	descripcion: string | null;
	restriccionEdad: number | null;
	limiteCompra: number | null;
	tiposEntrada: TipoEntradaDelCatalogo[];
}

// Lo que el cliente busca: texto y fechas ISO (yyyy-MM-dd), o null si no filtra
export interface FiltrosDelCatalogo {
	texto: string | null;
	desde: string | null;
	hasta: string | null;
	pagina: number;
}
