import { ChangeDetectionStrategy, Component, computed, ElementRef, inject, linkedSignal, signal } from '@angular/core';
import { form, FormField, submit } from '@angular/forms/signals';

import { comoErrorApi } from '../../../core/api/autenticacion.service';
import { EventosService } from '../../../core/api/eventos.service';
import { mensajeDeError, mensajeDelCampo } from '../../../core/api/mensajes-error';
import { ConCambiosSinGuardar } from '../../../core/guards/cambios-sin-guardar.guard';
import { comoError, ErrorApi } from '../../../core/modelos/error-api';
import { TipoEntrada, vendidasOReservadas } from '../../../core/modelos/tipo-entrada';
import { iguales } from '../../../shared/formularios/iguales';
import {
	enfocarCampo,
	primerCampoConError,
	primerCampoRechazado,
} from '../../../shared/formularios/primer-campo-con-error';
import { cantidad, precio } from '../../../shared/formato/precios';
import { EventoActual } from '../evento-actual';
import {
	aDatosDeTipo,
	aFormularioDeTipo,
	CAMPOS_DEL_TIPO,
	esquemaDelTipo,
	FormularioDeTipo,
	TIPO_VACIO,
} from './formulario-de-tipo';

// La pestaña Tipos de entrada de la ficha (CU-11). Lista los tipos con su
// disponibilidad y tiene un solo formulario, que agrega un tipo o edita el
// elegido. Los tipos no se eliminan (decisiones.md).
@Component({
	selector: 'app-tipos-entrada',
	imports: [FormField],
	templateUrl: './tipos-entrada.page.html',
	styleUrl: './tipos-entrada.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
	host: { '(window:beforeunload)': 'alCerrarLaPestana($event)' },
})
export class TiposEntradaPage implements ConCambiosSinGuardar {
	private readonly eventos = inject(EventosService);
	private readonly elemento = inject(ElementRef<HTMLElement>);
	protected readonly actual = inject(EventoActual);

	protected readonly recurso = this.eventos.tipos(this.actual.id);
	protected readonly tipos = computed(() => (this.recurso.hasValue() ? this.recurso.value() : undefined));
	protected readonly errorDeCarga = computed(() => {
		const error = this.recurso.error();
		return error === undefined ? '' : mensajeDeError(comoErrorApi(error));
	});
	// Un cancelado no se toca, pero sus tipos se siguen viendo (CU-11 RF-5, RF-20)
	protected readonly soloLectura = computed(() => this.actual.evento()?.estado === 'CANCELADO');

	// El tipo que se edita; null es agregar uno nuevo
	protected readonly editando = signal<TipoEntrada | null>(null);
	private readonly modelo = linkedSignal<FormularioDeTipo>(() => aFormularioDeTipo(this.editando() ?? undefined));
	protected readonly formulario = form(
		this.modelo,
		esquemaDelTipo({
			minimo: () => {
				const tipo = this.editando();
				return tipo === null ? 0 : vendidasOReservadas(tipo);
			},
		}),
	);

	protected readonly guardando = signal(false);
	private readonly error = signal<ErrorApi | null>(null);
	private readonly enviados = signal<FormularioDeTipo | null>(null);
	protected readonly avisoDeConflicto = signal('');
	protected readonly exito = signal('');

	private readonly errorVigente = computed(() =>
		iguales(this.enviados(), this.formulario().value()) ? this.error() : null,
	);
	// El nombre repetido se marca en su campo, no arriba
	protected readonly aviso = computed(() => {
		const error = this.errorVigente();
		return this.avisoDeConflicto() || (error?.codigo === 'TIPO_ENTRADA_DUPLICADO' ? '' : mensajeDeError(error));
	});

	protected readonly precio = precio;
	protected readonly cantidad = cantidad;
	protected readonly vendidas = vendidasOReservadas;

	tieneCambiosSinGuardar(): boolean {
		const referencia = aFormularioDeTipo(this.editando() ?? undefined);
		return !this.soloLectura() && !iguales(referencia, this.formulario().value());
	}

	protected alCerrarLaPestana(evento: BeforeUnloadEvent): void {
		if (this.tieneCambiosSinGuardar()) {
			evento.preventDefault();
		}
	}

	protected mensaje(campo: keyof FormularioDeTipo): string {
		const error = this.errorVigente();
		if (campo === 'nombre' && error?.codigo === 'TIPO_ENTRADA_DUPLICADO') {
			return mensajeDeError(error);
		}
		return mensajeDelCampo(this.formulario[campo](), campo, error);
	}

	protected editar(tipo: TipoEntrada): void {
		this.limpiarAvisos();
		this.editando.set(tipo);
		this.formulario().reset(aFormularioDeTipo(tipo));
		enfocarCampo(this.elemento.nativeElement, 'nombre');
	}

	protected cancelarEdicion(): void {
		this.limpiarAvisos();
		this.volverAAgregar();
	}

	protected async guardar(evento: Event): Promise<void> {
		evento.preventDefault();
		// Un segundo clic mientras se guarda no manda otra petición
		if (this.guardando() || this.soloLectura()) {
			return;
		}
		this.limpiarAvisos();
		this.guardando.set(true);

		await submit(this.formulario, {
			action: async (formulario) => {
				const enviados = formulario().value();
				const editado = this.editando();
				const idEvento = this.actual.id();
				if (idEvento === null) {
					return undefined;
				}
				try {
					const guardado =
						editado === null
							? await this.eventos.registrarTipo(idEvento, aDatosDeTipo(enviados))
							: await this.eventos.modificarTipo(idEvento, editado.idTipoEntrada, aDatosDeTipo(enviados));
					this.alGuardar(guardado, editado === null);
				} catch (error: unknown) {
					this.alFallar(enviados, comoError(error));
				}
				return undefined;
			},
		});

		this.guardando.set(false);

		if (this.formulario().invalid()) {
			enfocarCampo(
				this.elemento.nativeElement,
				primerCampoConError(CAMPOS_DEL_TIPO, {
					nombre: this.formulario.nombre().invalid(),
					precio: this.formulario.precio().invalid(),
					cantidadTotal: this.formulario.cantidadTotal().invalid(),
				}),
			);
		}
	}

	// La tabla se actualiza con lo que devolvió el backend, sin volver a pedirla
	private alGuardar(guardado: TipoEntrada, nuevo: boolean): void {
		this.recurso.value.update((tipos) =>
			nuevo
				? [...(tipos ?? []), guardado]
				: (tipos ?? []).map((cada) => (cada.idTipoEntrada === guardado.idTipoEntrada ? guardado : cada)),
		);
		this.exito.set(nuevo ? 'Tipo de entrada agregado.' : 'Tipo de entrada guardado.');
		this.volverAAgregar();
	}

	private alFallar(enviados: FormularioDeTipo, problema: ErrorApi): void {
		// Otro administrador lo canceló: se recarga el evento y la pestaña queda de solo lectura
		if (problema.codigo === 'EVENTO_CANCELADO') {
			this.avisoDeConflicto.set(mensajeDeError(problema));
			this.actual.recargar();
			return;
		}
		if (problema.codigo === 'TIPO_ENTRADA_NO_ENCONTRADO') {
			this.avisoDeConflicto.set(mensajeDeError(problema));
			this.recurso.reload();
			this.volverAAgregar();
			return;
		}
		this.enviados.set(enviados);
		this.error.set(problema);
		enfocarCampo(
			this.elemento.nativeElement,
			problema.codigo === 'TIPO_ENTRADA_DUPLICADO' ? 'nombre' : primerCampoRechazado(CAMPOS_DEL_TIPO, problema.errores),
		);
	}

	private volverAAgregar(): void {
		this.editando.set(null);
		this.formulario().reset({ ...TIPO_VACIO });
	}

	private limpiarAvisos(): void {
		this.error.set(null);
		this.enviados.set(null);
		this.avisoDeConflicto.set('');
		this.exito.set('');
	}
}
