import { ChangeDetectionStrategy, Component, computed, inject, signal, viewChild } from '@angular/core';
import { form, FormField, required, schema, submit, validate } from '@angular/forms/signals';
import { RouterLink } from '@angular/router';

import { EventosService } from '../../../core/api/eventos.service';
import { mensajeDeError, mensajeDelCampo } from '../../../core/api/mensajes-error';
import { comoError, ErrorApi } from '../../../core/modelos/error-api';
import { Evento } from '../../../core/modelos/evento';
import { DialogoConfirmacion, PedidoDeConfirmacion } from '../../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { fechaCorta } from '../../../shared/formato/fechas';
import { fechaLocalISO } from '../../../shared/validacion/reglas';
import { EventoActual } from '../evento-actual';

// Otro administrador se adelantó: el evento en pantalla quedó desactualizado,
// así que se explica y se vuelve a pedir
const CONFLICTOS_QUE_RECARGAN = ['CAMBIO_DE_ESTADO_NO_PERMITIDO', 'VENTA_YA_CERRADA', 'EVENTO_CANCELADO'];

interface Postergacion {
	fecha: string;
}

// La pestaña Estado de la ficha (CU-12). Solo aparecen las acciones que el
// estado permite (decisiones.md → Transiciones de estado del evento). Piden
// confirmación postergar, cerrar la venta, cancelar y activar un postergado,
// porque reactiva las entradas usadas y eso no se deshace (FA-02). Activar un
// borrador no la pide: se deshace cerrando la venta.
@Component({
	selector: 'app-estado-evento',
	imports: [FormField, RouterLink, DialogoConfirmacion],
	templateUrl: './estado-evento.page.html',
	styleUrl: './estado-evento.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EstadoEventoPage {
	private readonly eventos = inject(EventosService);
	private readonly dialogo = viewChild.required(DialogoConfirmacion);
	protected readonly actual = inject(EventoActual);

	protected readonly evento = computed(() => this.actual.evento());
	protected readonly estado = computed(() => this.evento()?.estado);
	protected readonly puedeActivarse = computed(() => this.estado() === 'BORRADOR' || this.estado() === 'POSTERGADO');
	protected readonly postergado = computed(() => this.estado() === 'POSTERGADO');
	protected readonly puedePostergarse = computed(() => this.estado() === 'ACTIVO' || this.estado() === 'POSTERGADO');
	protected readonly puedeCerrarLaVenta = computed(() => this.evento()?.ventaAbierta === true);
	protected readonly cancelado = computed(() => this.estado() === 'CANCELADO');

	// --- Postergar: la fecha nueva no puede ser anterior a hoy ni la actual (RF-11)
	private readonly postergacion = signal<Postergacion>({ fecha: '' });
	protected readonly formulario = form(
		this.postergacion,
		schema<Postergacion>((ruta) => {
			required(ruta.fecha, { message: 'Elige la fecha nueva.' });
			validate(ruta.fecha, ({ value }) => {
				if (value() === '') {
					return null;
				}
				if (value() < fechaLocalISO(new Date())) {
					return { kind: 'fechaPasada', message: 'La fecha no puede ser anterior a hoy.' };
				}
				return value() === this.evento()?.fecha
					? { kind: 'mismaFecha', message: 'Elige una fecha distinta de la actual.' }
					: null;
			});
		}),
	);

	protected readonly procesando = signal(false);
	protected readonly aviso = signal('');
	protected readonly exito = signal('');
	// El rechazo por falta de tipos ofrece ir a agregarlos
	protected readonly faltanTipos = signal(false);
	// El rechazo del backend describe la fecha que se envió: al elegir otra deja de valer
	private readonly fechaEnviada = signal<string | null>(null);
	private readonly errorDeFecha = signal<ErrorApi | null>(null);
	private readonly errorDeFechaVigente = computed(() =>
		this.fechaEnviada() === this.formulario.fecha().value() ? this.errorDeFecha() : null,
	);

	protected readonly fecha = fechaCorta;

	protected mensajeDeFecha(): string {
		return mensajeDelCampo(this.formulario.fecha(), 'fecha', this.errorDeFechaVigente());
	}

	protected async activar(): Promise<void> {
		const actual = this.evento();
		if (actual === undefined) {
			return;
		}
		// La ficha guarda el evento; la cantidad es solo para la confirmación
		let reactivadas = 0;
		await this.operar(
			async (id) => {
				const { entradasReactivadas, ...evento } = await this.eventos.activar(id);
				reactivadas = entradasReactivadas ?? 0;
				return evento;
			},
			() => `El evento quedó activo y con la venta abierta.${fraseDeReactivadas(reactivadas)}`,
			actual.estado === 'POSTERGADO'
				? {
						titulo: 'Activar evento',
						mensaje:
							`${actual.nombre} vuelve a venderse con su fecha nueva. ` +
							'Las entradas usadas antes de la postergación vuelven a estar activas para esa fecha.',
						confirmar: 'Activar',
					}
				: undefined,
		);
	}

	protected async postergar(evento: Event): Promise<void> {
		evento.preventDefault();
		if (this.procesando()) {
			return;
		}
		this.errorDeFecha.set(null);
		await submit(this.formulario, {
			action: async (formulario) => {
				const nueva = formulario().value().fecha;
				this.fechaEnviada.set(nueva);
				const actual = this.evento();
				if (actual === undefined) {
					return undefined;
				}
				await this.operar(
					(id) => this.eventos.postergar(id, nueva),
					() => `Evento postergado al ${fechaCorta(nueva)}.`,
					{
						titulo: 'Postergar evento',
						mensaje:
							`${actual.nombre} pasa del ${fechaCorta(actual.fecha)} al ${fechaCorta(nueva)}. ` +
							'Las entradas vendidas siguen valiendo para la fecha nueva. ' +
							'Avisar a quienes compraron se hace fuera del sistema.',
						confirmar: 'Postergar',
					},
				);
				return undefined;
			},
		});
	}

	protected async cerrarVenta(): Promise<void> {
		const actual = this.evento();
		if (actual === undefined) {
			return;
		}
		// La venta solo se reabre activando el evento, y activar vale desde
		// postergado, no desde activo (decisiones.md)
		const comoReabrir =
			actual.estado === 'POSTERGADO'
				? 'Vuelve a abrirse si activas el evento.'
				: 'Solo vuelve a abrirse activando el evento, y un evento activo tiene que postergarse antes.';
		await this.operar(
			(id) => this.eventos.cerrarVenta(id),
			() => 'La venta quedó cerrada.',
			{
				titulo: 'Cerrar la venta',
				mensaje: `Nadie más podrá comprar entradas para ${actual.nombre}. ${comoReabrir}`,
				confirmar: 'Cerrar la venta',
			},
		);
	}

	protected async cancelar(): Promise<void> {
		const actual = this.evento();
		if (actual === undefined) {
			return;
		}
		await this.operar(
			(id) => this.eventos.cancelar(id),
			() => 'El evento quedó cancelado.',
			{
				titulo: 'Cancelar evento',
				mensaje:
					`Es definitivo: ${actual.nombre} ya no se podrá editar ni vender. ` +
					'Las entradas vendidas quedan registradas para la devolución, que se hace fuera del sistema.',
				confirmar: 'Cancelar evento',
				desistir: 'No, mantenerlo',
			},
		);
	}

	// Pide confirmación si hace falta, llama al backend y deja el evento al día en
	// la ficha. Un segundo clic mientras tanto no manda otra petición
	private async operar(
		operacion: (idEvento: number) => Promise<Evento>,
		exito: () => string,
		confirmacion?: PedidoDeConfirmacion,
	): Promise<void> {
		const actual = this.evento();
		if (actual === undefined || this.procesando()) {
			return;
		}
		this.procesando.set(true);
		this.aviso.set('');
		this.exito.set('');
		this.faltanTipos.set(false);
		try {
			if (confirmacion !== undefined && !(await this.dialogo().abrir(confirmacion))) {
				return;
			}
			this.actual.reemplazar(await operacion(actual.idEvento));
			this.exito.set(exito());
			this.formulario().reset({ fecha: '' });
		} catch (error: unknown) {
			this.alFallar(comoError(error));
		} finally {
			this.procesando.set(false);
		}
	}

	private alFallar(problema: ErrorApi): void {
		if (problema.codigo === 'DATOS_INVALIDOS') {
			this.errorDeFecha.set(problema);
			this.aviso.set(mensajeDeError(problema));
			return;
		}
		this.aviso.set(mensajeDeError(problema));
		if (problema.codigo === 'EVENTO_SIN_TIPOS_DE_ENTRADA') {
			this.faltanTipos.set(true);
		} else if (problema.codigo !== undefined && CONFLICTOS_QUE_RECARGAN.includes(problema.codigo)) {
			this.actual.recargar();
		}
	}
}

// CU-12 FA-02 (RF-31): la confirmación suma las entradas usadas que volvieron a
// estar activas; sin ninguna no dice nada
function fraseDeReactivadas(cantidad: number): string {
	if (cantidad === 0) {
		return '';
	}
	return cantidad === 1
		? ' Se reactivó 1 entrada utilizada.'
		: ` Se reactivaron ${cantidad} entradas utilizadas.`;
}
