import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { EstadoEvento, NOMBRES_DE_ESTADO_DE_EVENTO } from '../../../core/modelos/evento';

// El estado de un evento, como lo muestran la lista y la ficha. No depende solo
// del color: cada estado tiene su forma y siempre su texto (decisiones.md →
// Reglas). Rombo, círculo, triángulo y cuadrado. «Terminado» no es un estado:
// se agrega al texto cuando ya pasó el fin de un evento a la venta (CU-05).
@Component({
	selector: 'app-estado-de-evento',
	template: `{{ nombre() }}{{ terminado() ? ' · Terminado' : '' }}`,
	styleUrl: './estado-de-evento.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
	host: { '[attr.data-estado]': 'estado()', '[attr.data-terminado]': "terminado() ? '' : null" },
})
export class EstadoDeEvento {
	readonly estado = input.required<EstadoEvento>();
	readonly terminado = input(false);
	protected readonly nombre = computed(() => NOMBRES_DE_ESTADO_DE_EVENTO[this.estado()]);
}
