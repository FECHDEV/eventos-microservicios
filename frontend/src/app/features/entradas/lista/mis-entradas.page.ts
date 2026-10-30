import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { comoErrorApi } from '../../../core/api/autenticacion.service';
import { EntradasService } from '../../../core/api/entradas.service';
import { mensajeDeError } from '../../../core/api/mensajes-error';
import { CompraDelCliente, etiquetaDelEvento, textoDelEstado } from '../../../core/modelos/entrada';
import { terminaAlDiaSiguiente } from '../../../core/modelos/evento';
import { fechaCorta, horaCorta } from '../../../shared/formato/fechas';

interface Seccion {
	id: string;
	titulo: string;
	compras: CompraDelCliente[];
	// Las de eventos terminados se ven apagadas y con «Terminado»; las de
	// cancelados, con contorno rojo
	clase: 'proxima' | 'pasada' | 'cancelada';
}

// Las entradas del Cliente (CU-08 pasos 1 y 2): una tarjeta por compra
// confirmada, con sus entradas y el estado de cada una. El orden viene del
// backend; la pantalla no reordena. Los códigos están solo en el detalle.
// Solo el Cliente llega (rolGuard).
@Component({
	selector: 'app-mis-entradas',
	imports: [RouterLink],
	templateUrl: './mis-entradas.page.html',
	styleUrl: './mis-entradas.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MisEntradasPage {
	protected readonly recurso = inject(EntradasService).entradas();
	private readonly entradas = computed(() => (this.recurso.hasValue() ? this.recurso.value() : undefined));

	// Una sección sin compras no se muestra
	protected readonly secciones = computed((): Seccion[] | undefined => {
		const entradas = this.entradas();
		if (entradas === undefined) {
			return undefined;
		}
		return [
			{ id: 'proximos', titulo: 'Próximos', compras: entradas.proximas, clase: 'proxima' as const },
			{ id: 'pasados', titulo: 'Pasados', compras: entradas.pasadas, clase: 'pasada' as const },
			{ id: 'cancelados', titulo: 'Cancelados', compras: entradas.canceladas, clase: 'cancelada' as const },
		].filter((seccion) => seccion.compras.length > 0);
	});

	protected readonly errorDeCarga = computed(() => {
		const error = this.recurso.error();
		return error === undefined ? '' : mensajeDeError(comoErrorApi(error));
	});

	protected readonly fecha = fechaCorta;
	protected readonly hora = horaCorta;
	protected readonly terminaAlDiaSiguiente = terminaAlDiaSiguiente;
	protected readonly etiqueta = etiquetaDelEvento;
	protected readonly estado = textoDelEstado;
}
