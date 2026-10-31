package com.sarao.eventos.entity;

// Las páginas que cuentan visitas: una por pantalla del frontend, sin los ids de la ruta (todas las fichas de
// eventos son DETALLE_DE_EVENTO). Cerradas, así nadie crea filas pidiendo páginas inventadas (decisiones.md →
// «Contador de visitas por página»). Una pantalla nueva agrega su valor acá y en el data de su ruta
public enum Pagina {
	PORTADA,
	DETALLE_DE_EVENTO,
	SELECCION_DE_ENTRADAS,
	RESERVA,
	MIS_ENTRADAS,
	DETALLE_DE_ENTRADA,
	INICIO_SESION,
	REGISTRO,
	RECUPERACION,
	RESTABLECIMIENTO,
	PANEL,
	MIS_DATOS,
	USUARIOS,
	EVENTOS,
	NUEVO_EVENTO,
	FICHA_DE_EVENTO,
	DEVOLUCIONES,
	DEVOLUCIONES_DEL_EVENTO,
	REPORTES,
	VALIDACION
}
