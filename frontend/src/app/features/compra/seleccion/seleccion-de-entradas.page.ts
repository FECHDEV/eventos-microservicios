import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, ElementRef, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { form, FormField, submit } from '@angular/forms/signals';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { comoErrorApi } from '../../../core/api/autenticacion.service';
import { CatalogoService } from '../../../core/api/catalogo.service';
import { mensajeDeError, mensajeDelCampo } from '../../../core/api/mensajes-error';
import { enfocarCampo } from '../../../shared/formularios/primer-campo-con-error';
import { ReservasService } from '../../../core/api/reservas.service';
import { comoError, ErrorApi } from '../../../core/modelos/error-api';
import { terminaAlDiaSiguiente } from '../../../core/modelos/evento';
import { fechaCorta, horaCorta } from '../../../shared/formato/fechas';
import { precio } from '../../../shared/formato/precios';
import { iguales } from '../../../shared/formularios/iguales';
import { idDeLaUrl } from '../../../shared/navegacion/id-de-la-url';
import { esCantidadValida, esquemaDeCompra, FormularioDeCompra } from './formulario-de-compra';

// Elegir la cantidad de un tipo de entrada y reservarla (CU-06, pasos 1 a 5).
// Solo el Cliente llega (rolGuard); el evento se lee del catálogo público.
@Component({
	selector: 'app-seleccion-de-entradas',
	imports: [FormField, RouterLink],
	templateUrl: './seleccion-de-entradas.page.html',
	styleUrl: './seleccion-de-entradas.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SeleccionDeEntradasPage {
	private readonly catalogo = inject(CatalogoService);
	private readonly reservas = inject(ReservasService);
	private readonly router = inject(Router);
	private readonly elemento = inject(ElementRef<HTMLElement>);
	private readonly parametros = toSignal(inject(ActivatedRoute).paramMap, { requireSync: true });

	// Basura en la URL no llega al backend
	private readonly idEvento = computed(() => idDeLaUrl(this.parametros().get('id')));
	private readonly idTipo = computed(() => idDeLaUrl(this.parametros().get('idTipo')));
	protected readonly recurso = this.catalogo.evento(this.idEvento);
	protected readonly evento = computed(() => (this.recurso.hasValue() ? this.recurso.value() : undefined));
	protected readonly tipo = computed(() =>
		this.evento()?.tiposEntrada.find((cada) => cada.idTipoEntrada === this.idTipo()),
	);
	protected readonly urlDelEvento = computed(() => {
		const id = this.idEvento();
		return id === null ? '/' : `/catalogo/${id}`;
	});

	// Agotado, venta cerrada, un tipo de otro evento o un evento que ya no se
	// muestra (404): no hay nada que comprar. El backend lo vuelve a validar
	protected readonly noALaVenta = computed(() => {
		if (this.idEvento() === null || this.idTipo() === null) {
			return true;
		}
		const error = this.recurso.error();
		if (error instanceof HttpErrorResponse && error.status === 404) {
			return true;
		}
		const evento = this.evento();
		const tipo = this.tipo();
		return evento !== undefined && (!evento.ventaAbierta || tipo === undefined || !tipo.disponible);
	});
	protected readonly errorDeCarga = computed(() => {
		const error = this.recurso.error();
		return error === undefined || this.noALaVenta() ? '' : mensajeDeError(comoErrorApi(error));
	});

	private readonly modelo = signal<FormularioDeCompra>({ cantidad: '' });
	protected readonly formulario = form(
		this.modelo,
		esquemaDeCompra({ limite: () => this.evento()?.limiteCompra ?? null }),
	);

	protected readonly enviando = signal(false);
	private readonly error = signal<ErrorApi | null>(null);
	private readonly enviados = signal<FormularioDeCompra | null>(null);
	// El aviso es de lo que se envió: al cambiar la cantidad deja de valer
	private readonly errorVigente = computed(() =>
		iguales(this.enviados(), this.formulario().value()) ? this.error() : null,
	);
	protected readonly aviso = computed(() => mensajeDeError(this.errorVigente()));

	// Precio × cantidad mientras se escribe, solo con una cantidad válida
	protected readonly total = computed(() => {
		const tipo = this.tipo();
		const escrita = this.formulario.cantidad().value();
		return tipo === undefined || !esCantidadValida(escrita) ? '' : precio(tipo.precio * Number(escrita));
	});

	protected readonly fecha = fechaCorta;
	protected readonly hora = horaCorta;
	protected readonly precio = precio;
	protected readonly terminaAlDiaSiguiente = terminaAlDiaSiguiente;

	protected mensajeDeCantidad(): string {
		return mensajeDelCampo(this.formulario.cantidad(), 'cantidad', this.errorVigente());
	}

	protected async reservar(evento: Event): Promise<void> {
		evento.preventDefault();
		const tipo = this.tipo();
		// Un segundo clic mientras se reserva no manda otra petición
		if (this.enviando() || tipo === undefined) {
			return;
		}
		this.error.set(null);
		this.enviados.set(null);
		this.enviando.set(true);

		await submit(this.formulario, {
			action: async (formulario) => {
				const enviados = formulario().value();
				try {
					const reserva = await this.reservas.reservar({
						idTipoEntrada: tipo.idTipoEntrada,
						cantidad: Number(enviados.cantidad),
					});
					await this.irALaReserva(reserva.idReserva);
				} catch (error: unknown) {
					this.alFallar(enviados, comoError(error));
				}
				return undefined;
			},
		});

		this.enviando.set(false);
		if (this.formulario().invalid()) {
			this.enfocarLaCantidad();
		}
	}

	// La reserva ya está hecha: un problema al navegar no puede contarse como un
	// rechazo de la compra
	private async irALaReserva(idReserva: number): Promise<void> {
		try {
			await this.router.navigate(['/reservas', idReserva]);
		} catch {
			// Navegar puede fallar por fuera de este caso de uso
		}
	}

	private alFallar(enviados: FormularioDeCompra, problema: ErrorApi): void {
		this.enviados.set(enviados);
		this.error.set(problema);
		if (problema.errores?.some((cada) => cada.campo === 'cantidad')) {
			this.enfocarLaCantidad();
		}
	}

	private enfocarLaCantidad(): void {
		enfocarCampo(this.elemento.nativeElement, 'cantidad');
	}
}
