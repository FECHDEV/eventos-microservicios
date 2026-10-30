import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';

import { comoErrorApi } from '../../../core/api/autenticacion.service';
import { EventosService } from '../../../core/api/eventos.service';
import { mensajeDeError } from '../../../core/api/mensajes-error';
import {
	EstadoEvento,
	EventoResumen,
	ESTADOS_DE_EVENTO,
	FiltrosDeEventos,
	NOMBRES_DE_ESTADO_DE_EVENTO,
	terminaAlDiaSiguiente,
	yaTermino,
} from '../../../core/modelos/evento';
import { fechaCorta, horaCorta } from '../../../shared/formato/fechas';
import { cantidad } from '../../../shared/formato/precios';
import { paginaDeLaUrl } from '../../../shared/navegacion/pagina-de-la-url';
import { EstadoDeEvento } from '../estado-de-evento/estado-de-evento';
import { ImagenDeEvento } from '../imagen-de-evento/imagen-de-evento';

// Pantalla de la lista de CU-10. El filtro y la página viven en la URL:
// recargar o compartir el enlace muestra la misma lista.
@Component({
	selector: 'app-eventos',
	imports: [RouterLink, EstadoDeEvento, ImagenDeEvento],
	templateUrl: './eventos.page.html',
	styleUrl: './eventos.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EventosPage {
	private readonly eventos = inject(EventosService);
	private readonly ruta = inject(ActivatedRoute);
	private readonly router = inject(Router);

	private readonly consulta = toSignal(this.ruta.queryParamMap, { requireSync: true });
	protected readonly filtros = computed(() => leerFiltros(this.consulta()));

	protected readonly listado = this.eventos.listado(this.filtros);
	protected readonly pagina = computed(() => (this.listado.hasValue() ? this.listado.value() : undefined));
	// Un 403 (otro administrador le quitó el rol) o la red caída, con el diccionario
	protected readonly errorDeCarga = computed(() => {
		const error = this.listado.error();
		return error === undefined ? '' : mensajeDeError(comoErrorApi(error));
	});

	protected readonly estados = ESTADOS_DE_EVENTO;
	protected readonly nombresDeEstado = NOMBRES_DE_ESTADO_DE_EVENTO;
	protected readonly fecha = fechaCorta;
	protected readonly hora = horaCorta;
	protected readonly cantidad = cantidad;
	protected readonly terminaAlDiaSiguiente = terminaAlDiaSiguiente;

	// Depende de la hora, que no es una señal: se calcula al dibujar
	protected yaTermino(evento: EventoResumen): boolean {
		return yaTermino(evento, new Date());
	}

	// Cambiar el filtro cambia la lista entera: se vuelve a la primera página
	protected alElegirEstado(valor: string): void {
		this.navegar({ estado: esEstado(valor) ? valor : null, pagina: 0 });
	}

	protected irAPagina(pagina: number): void {
		this.navegar({ ...this.filtros(), pagina });
	}

	private navegar(filtros: FiltrosDeEventos): void {
		void this.router.navigate([], { relativeTo: this.ruta, queryParams: aConsulta(filtros) });
	}
}

function esEstado(valor: string | null): valor is EstadoEvento {
	return (ESTADOS_DE_EVENTO as readonly string[]).includes(valor ?? '');
}

// Lo que no es válido se descarta en vez de llegar al backend como un 400
function leerFiltros(consulta: ParamMap): FiltrosDeEventos {
	const estado = consulta.get('estado');
	return {
		estado: esEstado(estado) ? estado : null,
		pagina: paginaDeLaUrl(consulta.get('pagina')),
	};
}

// Solo lo que filtra queda en la URL, así la lista sin filtros es /eventos
function aConsulta(filtros: FiltrosDeEventos): Record<string, string | number | undefined> {
	return {
		estado: filtros.estado ?? undefined,
		pagina: filtros.pagina > 0 ? filtros.pagina : undefined,
	};
}
