import { EstadoCuenta, Rol } from './usuario';

// Espejo de UsuarioResumenResponse: lo que CU-09 muestra de cada cuenta, sin
// teléfono ni fecha de nacimiento (RNF-03)
export interface UsuarioResumen {
	idUsuario: number;
	nombre: string;
	apellido: string;
	correo: string;
	rol: Rol;
	estado: EstadoCuenta;
	fechaRegistro: string;
}

// CU-09 RF-23: lo que pide el alta de un miembro del personal
export interface AltaDePersonal {
	nombre: string;
	apellido: string;
	correo: string;
	telefono: string | null;
	rol: Rol;
	contrasena: string;
}

// Los filtros de la lista. null en rol o estado es "todos"; la página empieza
// en 0, como en el backend.
// grupo separa al personal de los clientes (CU-09 RF-27)
export interface FiltrosDeUsuarios {
	grupo: 'personal' | 'clientes';
	rol: Rol | null;
	estado: EstadoCuenta | null;
	texto: string;
	pagina: number;
}
