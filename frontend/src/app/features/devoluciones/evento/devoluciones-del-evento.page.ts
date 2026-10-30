import { NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
	afterNextRender,
	ChangeDetectionStrategy,
	Component,
	computed,
	ElementRef,
	inject,
	Injector,
	signal,
	viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { comoErrorApi } from '../../../core/api/autenticacion.service';
import { DevolucionesService } from '../../../core/api/devoluciones.service';
import { mensajeDeError } from '../../../core/api/mensajes-error';
import { CompraParaDevolucion, EntradaParaDevolucion, RegistroDevolucion } from '../../../core/modelos/devolucion';
import { comoError } from '../../../core/modelos/error-api';
import { terminaAlDiaSiguiente } from '../../../core/modelos/evento';
import { DialogoConfirmacion, PedidoDeConfirmacion } from '../../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { fechaCorta, horaCorta } from '../../../shared/formato/fechas';
import { precio } from '../../../shared/formato/precios';
import { idDeLaUrl } from '../../../shared/navegacion/id-de-la-url';

const NO_SE_DESHACE = 'Solo registra que la devolución se hizo fuera del sistema. No se deshace.';

// Las entradas de un evento cancelado (CU-13 pasos 3 a 7): las pendientes de
// devolución por compra, con lo necesario para devolver el dinero fuera del
// sistema, y aparte las devueltas. Registrar pide confirmación porque no se
// deshace. Solo el Administrador llega (rolGuard).
@Component({
	selector: 'app-devoluciones-del-evento',
	imports: [NgTemplateOutlet, RouterLink, DialogoConfirmacion],
	templateUrl: './devoluciones-del-evento.page.html',
	styleUrl: './devoluciones-del-evento.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DevolucionesDelEventoPage {
	private readonly devoluciones = inject(DevolucionesService);
	private readonly elemento = inject<ElementRef<HTMLElement>>(ElementRef);
	private readonly injector = inject(Injector);
	private readonly dialogo = viewChild.required(DialogoConfirmacion);
	private readonly parametros = toSignal(inject(ActivatedRoute).paramMap, { requireSync: true });

	// Basura en la URL no llega al backend
	private readonly id = computed(() => idDeLaUrl(this.parametros().get('id')));
	protected readonly recurso = this.devoluciones.delEvento(this.id);
	protected readonly evento = computed(() => (this.recurso.hasValue() ? this.recurso.value() : undefined));

	protected readonly noEncontrado = computed(() => {
		const error = this.recurso.error();
		return this.id() === null || (error instanceof HttpErrorResponse && error.status === 404);
	});
	// RF-8: un evento que no está cancelado no tiene devoluciones; reintentar no lo cambiaría
	protected readonly noCancelado = computed(() => {
		const error = this.recurso.error();
		return error !== undefined && comoErrorApi(error).codigo === 'EVENTO_NO_CANCELADO';
	});
	protected readonly errorDeCarga = computed(() => {
		const error = this.recurso.error();
		return error === undefined || this.noEncontrado() ? '' : mensajeDeError(comoErrorApi(error));
	});

	protected readonly procesando = signal(false);
	// Mientras recarga, los botones están sobre datos viejos: no se registra sobre ellos
	protected readonly bloqueado = computed(() => this.procesando() || this.recurso.isLoading());
	protected readonly exito = signal('');
	protected readonly aviso = signal('');

	protected readonly fecha = fechaCorta;
	protected readonly hora = horaCorta;
	protected readonly precio = precio;
	protected readonly terminaAlDiaSiguiente = terminaAlDiaSiguiente;

	protected async registrarCompra(compra: CompraParaDevolucion): Promise<void> {
		const cuantas = compra.entradas.length;
		await this.registrar(() => this.devoluciones.registrarCompra(compra.idReserva), {
			titulo: 'Registrar devolución',
			mensaje: `${compra.cliente}: ${cuantas} ${cuantas === 1 ? 'entrada' : 'entradas'} ${compra.tipoEntrada}. ${NO_SE_DESHACE}`,
			confirmar: 'Registrar',
		});
	}

	protected async registrarEntrada(compra: CompraParaDevolucion, entrada: EntradaParaDevolucion): Promise<void> {
		await this.registrar(() => this.devoluciones.registrarEntrada(entrada.idEntrada), {
			titulo: 'Registrar devolución',
			mensaje: `${compra.cliente}: entrada ${entrada.numero} de ${compra.tipoEntrada}. ${NO_SE_DESHACE}`,
			confirmar: 'Registrar',
		});
	}

	// Un segundo clic mientras tanto no abre otro diálogo ni manda otra petición.
	// Pase lo que pase, se recarga: otro Administrador pudo adelantarse (FA-02)
	private async registrar(operacion: () => Promise<RegistroDevolucion>, pedido: PedidoDeConfirmacion): Promise<void> {
		if (this.procesando()) {
			return;
		}
		this.procesando.set(true);
		this.exito.set('');
		this.aviso.set('');
		try {
			if (!(await this.dialogo().abrir(pedido))) {
				return;
			}
			const { registradas } = await operacion();
			this.exito.set(
				`Se registró la devolución de ${registradas} ${registradas === 1 ? 'entrada' : 'entradas'}.`,
			);
			this.recargar();
		} catch (error: unknown) {
			this.aviso.set(mensajeDeError(comoError(error)));
			this.recargar();
		} finally {
			this.procesando.set(false);
		}
	}

	// Al recargar, la compra puede dejar de estar pendiente y su botón desaparecer: el foco va al título de la sección,
	// que siempre está
	private recargar(): void {
		this.recurso.reload();
		afterNextRender(
			() => this.elemento.nativeElement.querySelector<HTMLElement>('#titulo-pendientes')?.focus(),
			{ injector: this.injector },
		);
	}
}
