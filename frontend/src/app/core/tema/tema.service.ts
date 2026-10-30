import { computed, DestroyRef, DOCUMENT, effect, inject, Injectable, NgZone, signal } from '@angular/core';

import { Estilo, Modo } from '../../shared/apariencia/apariencia';
import {
	guardarEstilo,
	guardarModo,
	leerEstilo,
	leerModo,
	milisegundosHastaElCambio,
	Tema,
	temaDe,
} from './almacen-tema';

// El color de la barra del navegador en el celular: el fondo de cada paleta
// (--sarao-fondo en _tokens.scss). index.html tiene la misma tabla
const COLOR_DE_LA_BARRA: Record<Estilo, Record<Tema, string>> = {
	adultos: { oscuro: '#0b1020', claro: '#f6f7fb' },
	jovenes: { oscuro: '#120a24', claro: '#f7f5ff' },
	ninos: { oscuro: '#0b1f2a', claro: '#fff9ec' },
};

// La apariencia de la interfaz (decisiones.md → «Tres estilos y modo día/noche»).
// Adultos y noche van sin marca en <html> porque son los de :root; los demás se
// activan con data-estilo y data-tema="claro" (styles/_tokens.scss)
@Injectable({ providedIn: 'root' })
export class TemaService {
	private readonly documento = inject(DOCUMENT);
	private readonly estiloElegido = signal<Estilo>(leerEstilo());
	private readonly modoElegido = signal<Modo>(leerModo());
	// La hora solo importa en automático: se vuelve a leer al llegar las 6:00 o
	// las 19:00, y al volver a la pestaña, porque en segundo plano o con la
	// computadora suspendida el temporizador se atrasa
	private readonly ahora = signal(new Date());

	readonly estilo = this.estiloElegido.asReadonly();
	readonly modo = this.modoElegido.asReadonly();
	readonly tema = computed(() => temaDe(this.modoElegido(), this.ahora()));

	constructor() {
		effect(() => {
			const estilo = this.estiloElegido();
			const tema = this.tema();
			const raiz = this.documento.documentElement;
			if (estilo === 'adultos') {
				delete raiz.dataset['estilo'];
			} else {
				raiz.dataset['estilo'] = estilo;
			}
			if (tema === 'claro') {
				raiz.dataset['tema'] = 'claro';
			} else {
				delete raiz.dataset['tema'];
			}
			this.documento
				.querySelector('meta[name="theme-color"]')
				?.setAttribute('content', COLOR_DE_LA_BARRA[estilo][tema]);
		});

		// Fuera de la zona de Angular: un temporizador de horas dejaría la aplicación
		// siempre «inestable» (whenStable no terminaría nunca), y cada cambio de
		// pestaña dispararía la detección de cambios. La señal igual actualiza la vista
		const revisarLaHora = () => this.ahora.set(new Date());
		let temporizador: ReturnType<typeof setTimeout>;
		const programar = () => {
			temporizador = setTimeout(() => {
				revisarLaHora();
				programar();
			}, milisegundosHastaElCambio(new Date()));
		};
		inject(NgZone).runOutsideAngular(() => {
			this.documento.addEventListener('visibilitychange', revisarLaHora);
			programar();
		});
		inject(DestroyRef).onDestroy(() => {
			clearTimeout(temporizador);
			this.documento.removeEventListener('visibilitychange', revisarLaHora);
		});
	}

	// Se guarda solo lo que la persona elige: sin elección no queda nada guardado,
	// así un cambio futuro del valor por defecto le llega
	elegirEstilo(estilo: Estilo): void {
		this.estiloElegido.set(estilo);
		guardarEstilo(estilo);
	}

	elegirModo(modo: Modo): void {
		this.modoElegido.set(modo);
		guardarModo(modo);
	}
}
