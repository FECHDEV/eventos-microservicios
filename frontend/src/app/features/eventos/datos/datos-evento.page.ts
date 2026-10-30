import {
	ChangeDetectionStrategy,
	Component,
	computed,
	ElementRef,
	inject,
	linkedSignal,
	signal,
	viewChild,
} from '@angular/core';
import { form, FormField, submit } from '@angular/forms/signals';
import { Router, RouterLink } from '@angular/router';

import { EventosService } from '../../../core/api/eventos.service';
import { mensajeDeError, mensajeDelCampo } from '../../../core/api/mensajes-error';
import { ConCambiosSinGuardar } from '../../../core/guards/cambios-sin-guardar.guard';
import { comoError, ErrorApi } from '../../../core/modelos/error-api';
import { Evento, terminaAlDiaSiguiente } from '../../../core/modelos/evento';
import { DialogoConfirmacion } from '../../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { iguales } from '../../../shared/formularios/iguales';
import {
	enfocarCampo,
	primerCampoConError,
	primerCampoRechazado,
} from '../../../shared/formularios/primer-campo-con-error';
import { fechaLocalISO } from '../../../shared/validacion/reglas';
import { EventoActual } from '../evento-actual';
import { ImagenDeEvento } from '../imagen-de-evento/imagen-de-evento';
import {
	aDatos,
	aFormulario,
	CAMPOS_DEL_EVENTO,
	esquemaDelEvento,
	FormularioDeEvento,
} from './formulario-de-evento';

// decisiones.md → Imagen del evento. Se revisa antes de subir para avisar sin
// esperar al servidor; quien decide es el backend, por el contenido del archivo
const TIPOS_DE_IMAGEN = ['image/jpeg', 'image/png', 'image/webp'];
const BYTES_MAXIMOS_DE_IMAGEN = 2 * 1024 * 1024;

// Conflictos que dejan el evento desactualizado en pantalla: se explican y se
// vuelve a pedir el evento, que puede haber cambiado de estado
const CONFLICTOS_QUE_RECARGAN = ['FECHA_NO_MODIFICABLE', 'EVENTO_CANCELADO'];

// La pestaña Datos de la ficha (CU-10 RF-15 a RF-29) y la pantalla de alta
// (/eventos/nuevo, RF-7 a RF-14). En la ficha recibe el evento de EventoActual;
// en el alta no hay ficha, y eso es lo que distingue un modo del otro.
@Component({
	selector: 'app-datos-evento',
	imports: [FormField, RouterLink, ImagenDeEvento, DialogoConfirmacion],
	templateUrl: './datos-evento.page.html',
	styleUrl: './datos-evento.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
	host: { '(window:beforeunload)': 'alCerrarLaPestana($event)' },
})
export class DatosEventoPage implements ConCambiosSinGuardar {
	private readonly eventos = inject(EventosService);
	private readonly router = inject(Router);
	private readonly elemento = inject(ElementRef<HTMLElement>);
	private readonly dialogo = viewChild.required(DialogoConfirmacion);
	protected readonly actual = inject(EventoActual, { optional: true });

	protected readonly esNuevo = this.actual === null;
	protected readonly evento = computed(() => this.actual?.evento());
	// ACTIVO o POSTERGADO: cambiar la fecha es postergar (CU-12)
	protected readonly horarioBloqueado = computed(() => {
		const estado = this.evento()?.estado;
		return estado === 'ACTIVO' || estado === 'POSTERGADO';
	});
	protected readonly soloLectura = computed(() => this.evento()?.estado === 'CANCELADO');

	// Los datos del evento que muestra el formulario, comparados por valor: si el
	// evento cambia solo en otra cosa (la imagen, la venta), el formulario no se
	// rellena y no se pierde lo que se estaba escribiendo
	private readonly datosDelEvento = computed(() => aFormulario(this.evento()), {
		equal: (anteriores, nuevos) => iguales(anteriores, nuevos),
	});
	// Se rellena al cargar, después de guardar y si otra pestaña cambia estos datos
	private readonly modelo = linkedSignal<FormularioDeEvento>(() => this.datosDelEvento());
	protected readonly formulario = form(
		this.modelo,
		esquemaDelEvento({
			hoy: () => fechaLocalISO(new Date()),
			fechaCargada: () => this.evento()?.fecha ?? null,
			horarioBloqueado: () => this.horarioBloqueado(),
			soloLectura: () => this.soloLectura(),
		}),
	);

	protected readonly terminaAlDiaSiguiente = computed(() =>
		terminaAlDiaSiguiente(this.formulario.horaInicio().value(), this.formulario.horaFin().value()),
	);

	protected readonly guardando = signal(false);
	private readonly error = signal<ErrorApi | null>(null);
	private readonly enviados = signal<FormularioDeEvento | null>(null);
	private readonly guardados = signal<FormularioDeEvento | null>(null);
	// Un conflicto recarga el evento y el formulario se rellena de nuevo: su aviso
	// no puede depender de que la pantalla siga igual a lo enviado
	protected readonly avisoDeConflicto = signal('');

	// La respuesta del backend describe lo que se envió: al corregir un dato deja
	// de valer, igual que en el registro de cuentas
	private readonly errorVigente = computed(() =>
		iguales(this.enviados(), this.formulario().value()) ? this.error() : null,
	);
	protected readonly aviso = computed(() => this.avisoDeConflicto() || mensajeDeError(this.errorVigente()));
	// La confirmación habla de lo que está en pantalla: si se edita, ya no
	protected readonly datosAlDia = computed(() => iguales(this.guardados(), this.formulario().value()));

	// --- Imagen ---
	protected readonly procesandoImagen = signal(false);
	protected readonly avisoDeImagen = signal('');
	protected readonly exitoDeImagen = signal('');

	// --- Cambios sin guardar ---

	private readonly referencia = computed(() => this.guardados() ?? this.datosDelEvento());
	// «Cancelar» en el alta ya preguntó con el diálogo: el guard no vuelve a hacerlo
	private descartado = false;

	// Lo consulta cambiosSinGuardarGuard antes de salir de la pantalla
	tieneCambiosSinGuardar(): boolean {
		return !this.descartado && !this.soloLectura() && !iguales(this.referencia(), this.formulario().value());
	}

	// Cerrar la pestaña o recargar: el navegador muestra su propio aviso
	protected alCerrarLaPestana(evento: BeforeUnloadEvent): void {
		if (this.tieneCambiosSinGuardar()) {
			evento.preventDefault();
		}
	}

	protected mensaje(campo: keyof FormularioDeEvento): string {
		return mensajeDelCampo(this.formulario[campo](), campo, this.errorVigente());
	}

	protected async guardar(evento: Event): Promise<void> {
		evento.preventDefault();
		// Un segundo clic mientras se guarda no manda otra petición
		if (this.guardando() || this.soloLectura()) {
			return;
		}
		this.error.set(null);
		this.enviados.set(null);
		this.guardados.set(null);
		this.avisoDeConflicto.set('');
		this.guardando.set(true);

		await submit(this.formulario, {
			action: async (formulario) => {
				const enviados = formulario().value();
				try {
					const guardado = await this.escribir(enviados);
					this.alGuardar(enviados, guardado);
				} catch (error: unknown) {
					this.alFallar(enviados, comoError(error));
				}
				return undefined;
			},
		});

		this.guardando.set(false);

		if (this.formulario().invalid()) {
			const invalidos = Object.fromEntries(
				CAMPOS_DEL_EVENTO.map((campo) => [campo, this.formulario[campo]().invalid()]),
			) as Record<keyof FormularioDeEvento, boolean>;
			enfocarCampo(this.elemento.nativeElement, primerCampoConError(CAMPOS_DEL_EVENTO, invalidos));
		}
	}

	// Solo en el alta: sin nada escrito vuelve directo; con datos, confirma antes
	// de perderlos
	protected async cancelar(): Promise<void> {
		if (this.tieneCambiosSinGuardar()) {
			const confirmado = await this.dialogo().abrir({
				titulo: 'Descartar el evento',
				mensaje: 'Lo que escribiste no se va a guardar.',
				confirmar: 'Descartar',
				desistir: 'Seguir editando',
			});
			if (!confirmado) {
				return;
			}
			this.descartado = true;
		}
		void this.router.navigate(['/eventos']);
	}

	protected async alElegirImagen(control: HTMLInputElement): Promise<void> {
		const archivo = control.files?.[0];
		// El mismo archivo se puede volver a elegir después de un error
		control.value = '';
		const evento = this.evento();
		if (archivo === undefined || evento === undefined || this.procesandoImagen()) {
			return;
		}
		this.avisoDeImagen.set('');
		this.exitoDeImagen.set('');
		if (!TIPOS_DE_IMAGEN.includes(archivo.type)) {
			this.avisoDeImagen.set('Elige una imagen JPG, PNG o WebP.');
			return;
		}
		if (archivo.size > BYTES_MAXIMOS_DE_IMAGEN) {
			this.avisoDeImagen.set('La imagen pesa más de 2 MB. Elige una más liviana.');
			return;
		}
		await this.cambiarImagen(() => this.eventos.subirImagen(evento.idEvento, archivo), 'Imagen guardada.');
	}

	protected async quitarImagen(): Promise<void> {
		const evento = this.evento();
		if (evento === undefined || this.procesandoImagen()) {
			return;
		}
		const confirmado = await this.dialogo().abrir({
			titulo: 'Quitar imagen',
			mensaje: `${evento.nombre} quedará sin imagen; en las pantallas se verá un fondo neutro.`,
			confirmar: 'Quitar',
		});
		if (confirmado) {
			await this.cambiarImagen(() => this.eventos.quitarImagen(evento.idEvento), 'Imagen quitada.');
		}
	}

	private escribir(formulario: FormularioDeEvento): Promise<Evento> {
		const evento = this.evento();
		return evento === undefined
			? this.eventos.registrar(aDatos(formulario))
			: this.eventos.modificar(evento.idEvento, aDatos(formulario));
	}

	private alGuardar(enviados: FormularioDeEvento, guardado: Evento): void {
		if (this.actual === null) {
			// El paso siguiente del flujo: agregar los tipos de entrada. Lo enviado
			// queda como guardado para que el guard no pregunte al salir
			this.guardados.set(enviados);
			void this.router.navigate(['/eventos', guardado.idEvento, 'tipos']);
			return;
		}
		this.actual.reemplazar(guardado);
		this.guardados.set(aFormulario(guardado));
	}

	private alFallar(enviados: FormularioDeEvento, problema: ErrorApi): void {
		if (problema.codigo !== undefined && CONFLICTOS_QUE_RECARGAN.includes(problema.codigo)) {
			this.avisoDeConflicto.set(mensajeDeError(problema));
			this.actual?.recargar();
			return;
		}
		this.enviados.set(enviados);
		this.error.set(problema);
		enfocarCampo(this.elemento.nativeElement, primerCampoRechazado(CAMPOS_DEL_EVENTO, problema.errores));
	}

	private async cambiarImagen(operacion: () => Promise<Evento>, exito: string): Promise<void> {
		this.procesandoImagen.set(true);
		this.avisoDeImagen.set('');
		this.exitoDeImagen.set('');
		try {
			this.actual?.imagenCambiada(await operacion());
			this.exitoDeImagen.set(exito);
		} catch (error: unknown) {
			const problema = comoError(error);
			this.avisoDeImagen.set(mensajeDeError(problema));
			if (problema.codigo === 'EVENTO_CANCELADO') {
				this.actual?.recargar();
			}
		} finally {
			this.procesandoImagen.set(false);
		}
	}
}
