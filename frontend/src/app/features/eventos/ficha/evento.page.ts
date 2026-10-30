import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { Evento, yaTermino } from '../../../core/modelos/evento';
import { fechaCorta } from '../../../shared/formato/fechas';
import { EstadoDeEvento } from '../estado-de-evento/estado-de-evento';
import { EventoActual } from '../evento-actual';
import { ImagenDeEvento } from '../imagen-de-evento/imagen-de-evento';

// La ficha de un evento: el encabezado y tres pestañas que son rutas hijas
// (Datos, Tipos de entrada y Estado). Son enlaces con aria-current y no un
// tablist de ARIA: así cada pestaña tiene su URL y el botón «atrás» funciona.
@Component({
	selector: 'app-evento',
	imports: [RouterLink, RouterLinkActive, RouterOutlet, EstadoDeEvento, ImagenDeEvento],
	templateUrl: './evento.page.html',
	styleUrl: './evento.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
	providers: [EventoActual],
})
export class EventoPage {
	protected readonly actual = inject(EventoActual);
	protected readonly fecha = fechaCorta;

	// Depende de la hora, que no es una señal: se calcula al dibujar
	protected yaTermino(evento: Evento): boolean {
		return yaTermino(evento, new Date());
	}
}
