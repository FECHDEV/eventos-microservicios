// Espejo de DatosPersonalesRequest y CambioContrasenaRequest del backend
// (servicios/*/src/main/java/com/sarao/*/dto/). Correo, fecha de
// nacimiento, rol y estado no se modifican en CU-04, así que no están acá.
export interface DatosPersonales {
	nombre: string;
	apellido: string;
	telefono: string;
}

export interface CambioDeContrasena {
	contrasenaActual: string;
	contrasenaNueva: string;
}
