import { ChangeDetectionStrategy, Component, computed, ElementRef, inject, output, signal } from '@angular/core';
import {
	email,
	form,
	FormField,
	maxLength,
	pattern,
	required,
	schema,
	submit,
	validate,
} from '@angular/forms/signals';

import { UsuariosService } from '../../core/api/usuarios.service';
import { EstadoDelCampo, mensajeDeError, mensajeDelCampo } from '../../core/api/mensajes-error';
import { comoError, ErrorApi } from '../../core/modelos/error-api';
import { DESCRIPCIONES_DE_ROL, NOMBRES_DE_ROL, Rol, ROLES_DEL_PERSONAL } from '../../core/modelos/usuario';
import { UsuarioResumen } from '../../core/modelos/usuario-resumen';
import { enfocarCampo, primerCampoConError, primerCampoRechazado } from '../../shared/formularios/primer-campo-con-error';
import { sinEspaciosSolos } from '../../shared/formularios/reglas-de-campo';
import { AYUDA_CONTRASENA, bytesUtf8, CONTRASENA_SEGURA } from '../../shared/validacion/reglas';

interface DatosDeAlta {
	nombre: string;
	apellido: string;
	correo: string;
	telefono: string;
	rol: string;
	contrasena: string;
}

type Campo = keyof DatosDeAlta;

const ORDEN: readonly Campo[] = ['nombre', 'apellido', 'correo', 'telefono', 'rol', 'contrasena'];

const SIN_DATOS: DatosDeAlta = { nombre: '', apellido: '', correo: '', telefono: '', rol: '', contrasena: '' };

// Las reglas de AltaPersonalRequest (CU-09 RF-23); el backend sigue siendo quien decide
const esquema = schema<DatosDeAlta>((ruta) => {
	required(ruta.nombre, { message: 'Escribe el nombre.' });
	sinEspaciosSolos(ruta.nombre, 'Escribe el nombre.');
	maxLength(ruta.nombre, 80, { message: 'El nombre admite hasta 80 caracteres.' });
	required(ruta.apellido, { message: 'Escribe el apellido.' });
	sinEspaciosSolos(ruta.apellido, 'Escribe el apellido.');
	maxLength(ruta.apellido, 80, { message: 'El apellido admite hasta 80 caracteres.' });
	required(ruta.correo, { message: 'Escribe el correo.' });
	sinEspaciosSolos(ruta.correo, 'Escribe el correo.');
	email(ruta.correo, { message: 'Ese correo no tiene formato de correo.' });
	maxLength(ruta.correo, 120, { message: 'El correo admite hasta 120 caracteres.' });
	maxLength(ruta.telefono, 20, { message: 'El teléfono admite hasta 20 caracteres.' });
	required(ruta.rol, { message: 'Elige el rol.' });
	required(ruta.contrasena, { message: 'Escribe una contraseña inicial.' });
	pattern(ruta.contrasena, CONTRASENA_SEGURA, { message: `La contraseña necesita ${AYUDA_CONTRASENA}` });
	validate(ruta.contrasena, ({ value }) =>
		bytesUtf8(value()) > 72
			? { kind: 'contrasenaLarga', message: 'Esa contraseña es demasiado larga. Prueba con una más corta.' }
			: null,
	);
});

// CU-09 FA-03: el Administrador da de alta a un miembro del personal con una
// contraseña inicial, que la persona cambia después en «Mis datos»
@Component({
	selector: 'app-alta-personal',
	imports: [FormField],
	templateUrl: './alta-personal.html',
	styleUrl: './alta-personal.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AltaPersonal {
	private readonly usuarios = inject(UsuariosService);
	private readonly elemento = inject(ElementRef<HTMLElement>);

	readonly creada = output<UsuarioResumen>();
	readonly cancelada = output<void>();

	protected readonly roles = ROLES_DEL_PERSONAL;
	protected readonly nombresDeRol = NOMBRES_DE_ROL;

	private readonly datos = signal<DatosDeAlta>({ ...SIN_DATOS });
	protected readonly formulario = form(this.datos, esquema);
	protected readonly enviando = signal(false);
	private readonly errorDelServidor = signal<ErrorApi | null>(null);

	protected readonly avisoDeError = computed(() => mensajeDeError(this.errorDelServidor()));
	protected readonly descripcion = computed(() => {
		const rol = this.formulario.rol().value();
		return rol === '' ? '' : DESCRIPCIONES_DE_ROL[rol as Rol];
	});

	protected async enviar(evento: Event): Promise<void> {
		evento.preventDefault();
		this.errorDelServidor.set(null);
		this.enviando.set(true);
		await submit(this.formulario, {
			action: async (formulario) => {
				const datos = formulario().value();
				try {
					this.creada.emit(
						await this.usuarios.darDeAlta({
							nombre: datos.nombre.trim(),
							apellido: datos.apellido.trim(),
							correo: datos.correo.trim(),
							telefono: datos.telefono.trim() || null,
							rol: datos.rol as Rol,
							contrasena: datos.contrasena,
						}),
					);
				} catch (error: unknown) {
					const errorApi = comoError(error);
					this.errorDelServidor.set(errorApi);
					enfocarCampo(
						this.elemento.nativeElement,
						errorApi.codigo === 'CUENTA_EXISTENTE' ? 'correo' : primerCampoRechazado(ORDEN, errorApi.errores),
					);
				}
				return undefined;
			},
		});
		this.enviando.set(false);
		if (this.formulario().invalid()) {
			enfocarCampo(
				this.elemento.nativeElement,
				primerCampoConError(ORDEN, Object.fromEntries(ORDEN.map((campo) => [campo, this.formulario[campo]().invalid()])) as Record<Campo, boolean>),
			);
		}
	}

	protected mensaje(estado: EstadoDelCampo, campo: Campo): string {
		return mensajeDelCampo(estado, campo, this.errorDelServidor());
	}
}
