import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { comoErrorApi } from '../../../core/api/autenticacion.service';
import { DevolucionesService } from '../../../core/api/devoluciones.service';
import { mensajeDeError } from '../../../core/api/mensajes-error';
import { terminaAlDiaSiguiente } from '../../../core/modelos/evento';
import { fechaCorta, horaCorta } from '../../../shared/formato/fechas';

// Los eventos cancelados y cuántas entradas tiene pendientes de devolución cada
// uno (CU-13 pasos 1 y 2). El orden viene del backend. Solo el Administrador
// llega (rolGuard).
@Component({
	selector: 'app-devoluciones',
	imports: [RouterLink],
	templateUrl: './devoluciones.page.html',
	styleUrl: './devoluciones.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DevolucionesPage {
	protected readonly recurso = inject(DevolucionesService).eventosCancelados();
	protected readonly cancelados = computed(() => (this.recurso.hasValue() ? this.recurso.value() : undefined));
	protected readonly errorDeCarga = computed(() => {
		const error = this.recurso.error();
		return error === undefined ? '' : mensajeDeError(comoErrorApi(error));
	});

	protected readonly fecha = fechaCorta;
	protected readonly hora = horaCorta;
	protected readonly terminaAlDiaSiguiente = terminaAlDiaSiguiente;
}
