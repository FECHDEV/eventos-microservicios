// Espejo de TipoEntradaResponse y TipoEntradaRequest del backend
// (servicios/*/src/main/java/com/sarao/*/dto/). El precio llega como
// número JSON con dos decimales.

export interface TipoEntrada {
	idTipoEntrada: number;
	nombre: string;
	precio: number;
	cantidadTotal: number;
	cantidadDisponible: number;
}

// La cantidad disponible no se envía: el backend la deriva de la total
// (CU-11 RF-18)
export interface DatosDeTipoEntrada {
	nombre: string;
	precio: number;
	cantidadTotal: number;
}

// Lo vendido y lo reservado, que es el mínimo al que puede bajar la cantidad
// total (decisiones.md → Cambiar la cantidad total de un tipo)
export function vendidasOReservadas(tipo: TipoEntrada): number {
	return tipo.cantidadTotal - tipo.cantidadDisponible;
}
