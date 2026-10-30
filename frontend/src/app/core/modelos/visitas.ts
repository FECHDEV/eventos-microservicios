// Espejo del enum Pagina del backend: las páginas que cuentan visitas, una por
// pantalla, sin los ids de la ruta. Cada ruta de app.routes.ts dice la suya en
// data.pagina (decisiones.md → «Contador de visitas por página»)
export const PAGINAS_VISITADAS = [
	'PORTADA',
	'DETALLE_DE_EVENTO',
	'SELECCION_DE_ENTRADAS',
	'RESERVA',
	'MIS_ENTRADAS',
	'DETALLE_DE_ENTRADA',
	'INICIO_SESION',
	'REGISTRO',
	'RECUPERACION',
	'RESTABLECIMIENTO',
	'PANEL',
	'MIS_DATOS',
	'USUARIOS',
	'EVENTOS',
	'NUEVO_EVENTO',
	'FICHA_DE_EVENTO',
	'DEVOLUCIONES',
	'DEVOLUCIONES_DEL_EVENTO',
	'REPORTES',
	'VALIDACION',
] as const;

export type PaginaVisitada = (typeof PAGINAS_VISITADAS)[number];
