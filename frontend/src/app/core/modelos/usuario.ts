// Espejo de UsuarioResponse del backend
// (servicios/*/src/main/java/com/sarao/*/dto/UsuarioResponse.java)

export type Rol = 'CLIENTE' | 'ADMINISTRADOR' | 'ORGANIZADOR' | 'CONTADOR' | 'PERSONAL';

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

export const ROLES: readonly Rol[] = ['CLIENTE', 'ADMINISTRADOR', 'ORGANIZADOR', 'CONTADOR', 'PERSONAL'];

// El Cliente es la cuenta de quien compra; estos son los roles del personal,
// que da de alta el Administrador (docs/decisiones.md → Roles del personal)
export const ROLES_DEL_PERSONAL: readonly Rol[] = ['ADMINISTRADOR', 'ORGANIZADOR', 'CONTADOR', 'PERSONAL'];

export const ESTADOS: readonly EstadoCuenta[] = ['ACTIVA', 'DESACTIVADA'];

// Cómo se nombran en pantalla; en el código siguen siendo los del backend
export const NOMBRES_DE_ROL: Record<Rol, string> = {
	CLIENTE: 'Cliente',
	ADMINISTRADOR: 'Administrador',
	ORGANIZADOR: 'Organizador de eventos',
	CONTADOR: 'Contador',
	PERSONAL: 'Control de acceso',
};

// Qué hace cada rol, en una línea: se muestra al elegirlo y en el panel
export const DESCRIPCIONES_DE_ROL: Record<Rol, string> = {
	CLIENTE: 'Compra entradas en la web y las presenta en la puerta.',
	ADMINISTRADOR: 'Todo el sistema: personal, clientes, eventos, devoluciones, reportes y validación.',
	ORGANIZADOR: 'Crea los eventos y sus entradas, y los activa, posterga o cancela.',
	CONTADOR: 'Solo reportes: ventas, ingresos, entradas, clientes y accesos, en PDF o Excel.',
	PERSONAL: 'Valida las entradas con el código QR en la puerta del evento.',
};

// Qué áreas abre cada rol. Es usabilidad (menú, panel y guards): quien
// autoriza es el backend
export const ROLES_POR_AREA = {
	misEntradas: ['CLIENTE'],
	usuarios: ['ADMINISTRADOR'],
	eventos: ['ADMINISTRADOR', 'ORGANIZADOR'],
	devoluciones: ['ADMINISTRADOR'],
	reportes: ['ADMINISTRADOR', 'CONTADOR'],
	validar: ['ADMINISTRADOR', 'PERSONAL'],
} as const satisfies Record<string, readonly Rol[]>;

export const NOMBRES_DE_ESTADO: Record<EstadoCuenta, string> = {
	ACTIVA: 'Activa',
	DESACTIVADA: 'Desactivada',
};

export function nombreCompleto(usuario: Usuario): string {
	return `${usuario.nombre} ${usuario.apellido}`;
}
