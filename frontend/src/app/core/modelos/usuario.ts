// Espejo de UsuarioResponse del backend
// (servicios/*/src/main/java/com/sarao/*/dto/UsuarioResponse.java)

export type Rol = 'CLIENTE' | 'ADMINISTRADOR' | 'PERSONAL';

export type EstadoCuenta = 'ACTIVA' | 'DESACTIVADA';

export interface Usuario {
	idUsuario: number;
	nombre: string;
	apellido: string;
	correo: string;
	telefono: string | null;
	fechaNacimiento: string | null;
	rol: Rol;
	estado: EstadoCuenta;
	fechaRegistro: string;
}

export const ROLES: readonly Rol[] = ['CLIENTE', 'ADMINISTRADOR', 'PERSONAL'];

export const ESTADOS: readonly EstadoCuenta[] = ['ACTIVA', 'DESACTIVADA'];

// Cómo se nombran en pantalla; en el código siguen siendo los del backend
export const NOMBRES_DE_ROL: Record<Rol, string> = {
	CLIENTE: 'Cliente',
	ADMINISTRADOR: 'Administrador',
	PERSONAL: 'Personal autorizado',
};

export const NOMBRES_DE_ESTADO: Record<EstadoCuenta, string> = {
	ACTIVA: 'Activa',
	DESACTIVADA: 'Desactivada',
};

export function nombreCompleto(usuario: Usuario): string {
	return `${usuario.nombre} ${usuario.apellido}`;
}
