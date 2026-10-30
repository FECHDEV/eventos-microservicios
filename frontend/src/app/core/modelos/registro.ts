// Espejo de RegistroClienteRequest del backend
// (servicios/*/src/main/java/com/sarao/*/dto/RegistroClienteRequest.java).
// El teléfono es el único dato opcional; la fecha de nacimiento viaja como
// AAAA-MM-DD, que es lo que produce un <input type="date"> y lo que espera
// LocalDate.
export interface DatosDeRegistro {
	nombre: string;
	apellido: string;
	correo: string;
	contrasena: string;
	telefono: string;
	fechaNacimiento: string;
}
