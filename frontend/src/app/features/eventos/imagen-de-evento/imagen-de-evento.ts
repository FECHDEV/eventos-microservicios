import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';

import { EventosService } from '../../../core/api/eventos.service';

// La imagen de un evento. El <img> no puede mandar el token, así que se trae
// como Blob y se muestra con una URL de objeto, que se libera al cambiar de
// evento o al salir para no dejar memoria tomada. Sin imagen, o si no llega
// (otro administrador la quitó), queda el fondo neutro del contenedor.
@Component({
	selector: 'app-imagen-de-evento',
	template: `
		@if (url(); as url) {
			<img [src]="url" [alt]="descripcion()" width="1600" height="900" />
		}
	`,
	styleUrl: './imagen-de-evento.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ImagenDeEvento {
	readonly idEvento = input.required<number>();
	readonly tieneImagen = input.required<boolean>();
	// Vacía, la imagen es decorativa: en la lista el nombre ya está en la fila
	readonly descripcion = input('');
	// Sube cuando la imagen se reemplazó: el id es el mismo, pero hay que volver a
	// pedirla (EventoActual.versionDeImagen)
	readonly version = input(0);

	private readonly recurso = inject(EventosService).imagen(
		computed(() => (this.tieneImagen() ? this.idEvento() : null)),
	);
	protected readonly url = signal<string | null>(null);

	constructor() {
		effect(() => {
			if (this.version() > 0) {
				untracked(() => this.recurso.reload());
			}
		});

		// La URL de objeto es un recurso del navegador: se crea con el Blob y se
		// revoca cuando llega otro, cuando deja de haber uno o al destruirse
		effect((alLimpiar) => {
			const blob = this.recurso.hasValue() ? this.recurso.value() : undefined;
			if (blob === undefined) {
				this.url.set(null);
				return;
			}
			const url = URL.createObjectURL(blob);
			this.url.set(url);
			alLimpiar(() => URL.revokeObjectURL(url));
		});
	}
}
