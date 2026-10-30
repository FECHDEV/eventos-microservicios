import { HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, linkedSignal, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';

import { comoErrorApi } from '../../core/api/autenticacion.service';
import { EventosService } from '../../core/api/eventos.service';
import { mensajeDeError } from '../../core/api/mensajes-error';
import { Evento } from '../../core/modelos/evento';
import { idDeLaUrl } from '../../shared/navegacion/id-de-la-url';

// El evento de la ficha, compartido con sus pestañas. Lo provee la ficha
// (EventoPage), así las pestañas que se muestran en su <router-outlet> reciben
// esta misma instancia: el evento se pide una vez y cada escritura lo reemplaza
// con lo que devuelve el backend, para que el encabezado quede al día.
@Injectable()
export class EventoActual {
	private readonly parametros = toSignal(inject(ActivatedRoute).paramMap, { requireSync: true });

	// null si lo de la URL no es un id: en ese caso no se pide nada
	readonly id = computed(() => idDeLaUrl(this.parametros().get('id')));
	private readonly recurso = inject(EventosService).evento(this.id);

	// Al pasar a otro evento se vacía hasta que llega el nuevo, así nunca se
	// muestran datos del anterior. Si en cambio falla una recarga del mismo evento
	// (la que pide una pestaña después de un conflicto), se conserva el último:
	// sin él desaparecerían la pestaña y lo que se estaba escribiendo en ella
	private readonly ultimoEvento = linkedSignal<{ id: number | null; valor: Evento | undefined }, Evento | undefined>({
		source: () => ({ id: this.id(), valor: this.recurso.hasValue() ? this.recurso.value() : undefined }),
		computation: (actual, previo) =>
			actual.valor ?? (previo !== undefined && previo.source.id === actual.id ? previo.value : undefined),
	});
	readonly evento = this.ultimoEvento.asReadonly();

	readonly noEncontrado = computed(() => {
		const error = this.recurso.error();
		return this.id() === null || (error instanceof HttpErrorResponse && error.status === 404);
	});

	// Cualquier otro fallo de la lectura (red caída, 500, 403) se ofrece reintentar
	readonly errorDeCarga = computed(() => {
		const error = this.recurso.error();
		return error === undefined || this.noEncontrado() ? '' : mensajeDeError(comoErrorApi(error));
	});

	// Reemplazar la imagen no cambia el id ni tieneImagen, así que la petición de
	// la imagen sería la misma: la versión avisa que hay que volver a pedirla
	readonly versionDeImagen = signal(0);

	reemplazar(evento: Evento): void {
		this.recurso.value.set(evento);
	}

	imagenCambiada(evento: Evento): void {
		this.reemplazar(evento);
		this.versionDeImagen.update((version) => version + 1);
	}

	recargar(): void {
		this.recurso.reload();
	}
}
