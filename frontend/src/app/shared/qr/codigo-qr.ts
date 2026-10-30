import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import qrcode from 'qrcode-generator';

// La zona silenciosa que exige el estándar: 4 módulos claros alrededor, para
// que el lector encuentre el código
const MARGEN = 4;

interface Dibujo {
	lado: number;
	trazo: string;
}

// Un código QR dibujado a partir de un texto: el de una entrada o el de un cobro
// (CU-08). La librería calcula qué módulos van oscuros y el componente los
// dibuja como un <svg> con un solo <path>, sin innerHTML. Corrección de errores
// M; el tamaño lo elige la librería según el largo del texto.
@Component({
	selector: 'app-codigo-qr',
	template: `
		@if (dibujo(); as qr) {
			<svg
				role="img"
				[attr.aria-label]="etiqueta()"
				[attr.viewBox]="'0 0 ' + qr.lado + ' ' + qr.lado"
				shape-rendering="crispEdges"
			>
				<rect class="claro" width="100%" height="100%" />
				<path class="oscuro" [attr.d]="qr.trazo" />
			</svg>
		}
	`,
	styleUrl: './codigo-qr.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CodigoQr {
	readonly valor = input.required<string>();
	// El nombre accesible: de qué es el código
	readonly etiqueta = input.required<string>();

	protected readonly dibujo = computed((): Dibujo | null => {
		const valor = this.valor();
		if (valor === '') {
			return null;
		}
		const qr = qrcode(0, 'M');
		qr.addData(valor);
		qr.make();
		const modulos = qr.getModuleCount();
		const partes: string[] = [];
		for (let fila = 0; fila < modulos; fila++) {
			for (let columna = 0; columna < modulos; columna++) {
				if (qr.isDark(fila, columna)) {
					partes.push(`M${columna + MARGEN} ${fila + MARGEN}h1v1h-1z`);
				}
			}
		}
		return { lado: modulos + 2 * MARGEN, trazo: partes.join('') };
	});
}
