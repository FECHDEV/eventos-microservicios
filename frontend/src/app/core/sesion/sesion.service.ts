import { computed, DestroyRef, DOCUMENT, inject, Injectable, NgZone, signal } from '@angular/core';

import { Sesion } from '../modelos/sesion';
import { Rol, Usuario } from '../modelos/usuario';
import { borrarSesionSiEs, CLAVE_SESION, guardarSesion, leerSesion } from './almacen-token';

// Estado de la sesión para toda la aplicación. Quien autoriza de verdad es el
// backend: acá solo se guarda lo necesario para mostrar la interfaz que
// corresponde y para enviar el token en cada petición.
@Injectable({ providedIn: 'root' })
export class SesionService {
	private readonly sesion = signal<Sesion | null>(leerSesion());
	private temporizador: ReturnType<typeof setTimeout> | null = null;
	private readonly zona = inject(NgZone);
	private readonly documento = inject(DOCUMENT);

	readonly usuario = computed<Usuario | null>(() => this.sesion()?.usuario ?? null);
	readonly rol = computed<Rol | null>(() => this.sesion()?.usuario.rol ?? null);
	readonly token = computed<string | null>(() => this.sesion()?.token ?? null);
	readonly autenticado = computed<boolean>(() => this.sesion() !== null);

	constructor() {
		this.programarCierre();
		// Las pestañas comparten el almacenamiento: iniciar o cerrar sesión en una
		// se refleja en las demás. El evento llega solo a las otras pestañas
		const alCambiar = (evento: StorageEvent) => {
			if (evento.key === CLAVE_SESION || evento.key === null) {
				this.zona.run(() => {
					const antes = this.sesion()?.usuario.idUsuario;
					this.sesion.set(leerSesion());
					this.programarCierre();
					// Otro usuario o sin sesión: la pantalla abierta muestra datos del
					// anterior, y al recargar los guards deciden de nuevo
					if (this.sesion()?.usuario.idUsuario !== antes) {
						this.recargar();
					}
				});
			}
		};
		window.addEventListener('storage', alCambiar);
		inject(DestroyRef).onDestroy(() => {
			this.cancelarCierre();
			window.removeEventListener('storage', alCambiar);
		});
	}

	iniciar(sesion: Sesion): void {
		guardarSesion(sesion);
		this.sesion.set(sesion);
		this.programarCierre();
	}

	// Borra del almacenamiento solo la sesión propia: si otra pestaña ya inició
	// otra, un 401 o el vencimiento de esta no la cierran
	cerrar(): void {
		this.cancelarCierre();
		const propia = this.sesion();
		if (propia !== null) {
			borrarSesionSiEs(propia.token);
		}
		this.sesion.set(null);
	}

	// CU-04: los datos de la cuenta cambiaron pero el token sigue valiendo. Se
	// guarda de nuevo para que una recarga no vuelva a mostrar los anteriores.
	actualizarUsuario(usuario: Usuario): void {
		const actual = this.sesion();
		if (actual === null) {
			return;
		}
		const nueva: Sesion = { ...actual, usuario };
		guardarSesion(nueva);
		this.sesion.set(nueva);
	}

	esAdministrador(): boolean {
		return this.rol() === 'ADMINISTRADOR';
	}

	// Sin esto, una pestaña abierta seguiría mostrándose como autenticada después
	// de que el token venciera, hasta que alguna petición volviera con 401
	private programarCierre(): void {
		this.cancelarCierre();
		const sesion = this.sesion();
		if (sesion === null) {
			return;
		}

		// Fuera de la zona de Angular: si no, esta espera de horas mantendría a la
		// aplicación como "ocupada" y las pruebas nunca se estabilizarían
		const restante = new Date(sesion.expiracion).getTime() - Date.now();
		this.temporizador = this.zona.runOutsideAngular(() =>
			setTimeout(
				() =>
					this.zona.run(() => {
						this.cerrar();
						this.recargar();
					}),
				Math.max(restante, 0),
			),
		);
	}

	// Recargar y no navegar: descarta los datos que la pantalla ya tenía y vuelve a
	// pasar por los guards de la ruta actual, pública o no
	private recargar(): void {
		this.documento.location.reload();
	}

	private cancelarCierre(): void {
		if (this.temporizador !== null) {
			clearTimeout(this.temporizador);
			this.temporizador = null;
		}
	}
}
