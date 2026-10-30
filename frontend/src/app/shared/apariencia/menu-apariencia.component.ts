import { ChangeDetectionStrategy, Component, ElementRef, inject, input, output, signal, viewChild } from '@angular/core';

import { Estilo, ESTILOS, Modo, MODOS } from './apariencia';

// El menú «Apariencia» del encabezado (decisiones.md → «Tres estilos y modo
// día/noche»): elige el estilo y el modo con radios nativos, que se recorren con
// las flechas. Cada elección se aplica en el momento; el menú se cierra con el
// botón, con Escape adentro, con un clic afuera o cuando el foco sale con Tab
@Component({
	selector: 'app-menu-apariencia',
	templateUrl: './menu-apariencia.component.html',
	styleUrl: './menu-apariencia.component.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
	host: {
		'(document:click)': 'alHacerClic($event)',
		'(keydown.escape)': 'cerrarConEscape()',
		'(focusout)': 'alPerderElFoco($event)',
	},
})
export class MenuAparienciaComponent {
	readonly estilo = input.required<Estilo>();
	readonly modo = input.required<Modo>();
	readonly estiloElegido = output<Estilo>();
	readonly modoElegido = output<Modo>();

	private readonly elemento = inject<ElementRef<HTMLElement>>(ElementRef);
	private readonly disparador = viewChild.required<ElementRef<HTMLButtonElement>>('disparador');

	protected readonly abierto = signal(false);
	protected readonly estilos = ESTILOS;
	protected readonly modos = MODOS;

	protected alternar(): void {
		this.abierto.update((abierto) => !abierto);
	}

	protected alHacerClic(evento: MouseEvent): void {
		if (this.abierto() && !this.elemento.nativeElement.contains(evento.target as Node)) {
			this.abierto.set(false);
		}
	}

	// Sin destino (un clic en una parte del menú que no recibe el foco, o el foco
	// que va a la barra del navegador) no se cierra: del clic afuera se encarga
	// alHacerClic
	protected alPerderElFoco(evento: FocusEvent): void {
		const destino = evento.relatedTarget as Node | null;
		if (destino !== null && !this.elemento.nativeElement.contains(destino)) {
			this.abierto.set(false);
		}
	}

	protected cerrarConEscape(): void {
		if (this.abierto()) {
			this.abierto.set(false);
			this.disparador().nativeElement.focus();
		}
	}
}
