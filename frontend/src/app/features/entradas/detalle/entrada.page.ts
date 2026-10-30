import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { comoErrorApi } from '../../../core/api/autenticacion.service';
import { EntradasService } from '../../../core/api/entradas.service';
import { mensajeDeError } from '../../../core/api/mensajes-error';
import { etiquetaDelEvento, motivoSinQr, textoDelEstado } from '../../../core/modelos/entrada';
import { terminaAlDiaSiguiente } from '../../../core/modelos/evento';
import { fechaCorta, horaCorta } from '../../../shared/formato/fechas';
import { idDeLaUrl } from '../../../shared/navegacion/id-de-la-url';
import { CodigoQr } from '../../../shared/qr/codigo-qr';

// El detalle de una entrada del Cliente con su código QR (CU-08 pasos 3 y 4).
// El backend manda el código solo si la entrada puede servir para entrar; si
// no, la pantalla dice por qué no hay QR. Solo el Cliente llega (rolGuard).
@Component({
	selector: 'app-entrada',
	imports: [RouterLink, CodigoQr],
	templateUrl: './entrada.page.html',
	styleUrl: './entrada.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EntradaPage {
	private readonly parametros = toSignal(inject(ActivatedRoute).paramMap, { requireSync: true });

	// Basura en la URL no llega al backend
	private readonly id = computed(() => idDeLaUrl(this.parametros().get('id')));
	protected readonly recurso = inject(EntradasService).entrada(this.id);
	protected readonly entrada = computed(() => (this.recurso.hasValue() ? this.recurso.value() : undefined));

	// La de otro Cliente responde 404 igual que una inexistente (RF-2)
	protected readonly noEncontrada = computed(() => {
		const error = this.recurso.error();
		return this.id() === null || (error instanceof HttpErrorResponse && error.status === 404);
	});
	protected readonly errorDeCarga = computed(() => {
		const error = this.recurso.error();
		return error === undefined || this.noEncontrada() ? '' : mensajeDeError(comoErrorApi(error));
	});

	protected readonly fecha = fechaCorta;
	protected readonly hora = horaCorta;
	protected readonly terminaAlDiaSiguiente = terminaAlDiaSiguiente;
	protected readonly etiqueta = etiquetaDelEvento;
	protected readonly estado = textoDelEstado;
	protected readonly motivoSinQr = motivoSinQr;

	// LocalDateTime del backend: la hora va después de la T
	protected emitida(fechaHora: string): string {
		return `${fechaCorta(fechaHora)} ${horaCorta(fechaHora.slice(11))}`;
	}
}
