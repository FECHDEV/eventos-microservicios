import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { Tabla } from '../../../core/modelos/reporte';
import { esNumerica, formatearCelda } from '../celda';

// Una tabla del reporte (resumen o detalle), dibujada a partir de sus columnas:
// sirve igual para los cinco tipos. Los totales van al pie, solo si hay
@Component({
	selector: 'app-tabla-de-reporte',
	templateUrl: './tabla-de-reporte.html',
	styleUrl: './tabla-de-reporte.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TablaDeReporte {
	readonly tabla = input.required<Tabla>();
	readonly titulo = input.required<string>();

	protected readonly formatear = formatearCelda;
	protected readonly esNumerica = esNumerica;
}
