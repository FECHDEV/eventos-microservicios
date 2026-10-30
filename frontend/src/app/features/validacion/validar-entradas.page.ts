import {
	ChangeDetectionStrategy,
	Component,
	computed,
	DestroyRef,
	ElementRef,
	inject,
	linkedSignal,
	NgZone,
	signal,
	viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { form, FormField, schema, submit, validate } from '@angular/forms/signals';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AccesosService } from '../../core/api/accesos.service';
import { comoErrorApi } from '../../core/api/autenticacion.service';
import { mensajeDeError, mensajeDelCampo } from '../../core/api/mensajes-error';
import { esValida, ResultadoValidacion, textoDelResultado } from '../../core/modelos/acceso';
import { comoError } from '../../core/modelos/error-api';
import { terminaAlDiaSiguiente } from '../../core/modelos/evento';
import { fechaCorta, horaCorta } from '../../shared/formato/fechas';
import { LectorQr } from '../../shared/lector-qr/lector-qr';
import { idDeLaUrl } from '../../shared/navegacion/id-de-la-url';

// RF-15: el resultado queda en pantalla hasta que la cámara pasa este tiempo sin
// ver su código; mientras tanto ese código no se reenvía
const TIEMPO_EN_PANTALLA_MS = 5000;

// El largo de la columna del código: ninguna entrada tiene uno más largo
const LARGO_MAXIMO_DEL_CODIGO = 255;

// Sacan de un evento que ya no admite validar (RF-4): se vuelve a elegir
const EVENTO_PERDIDO = ['EVENTO_NO_DISPONIBLE', 'EVENTO_NO_ENCONTRADO'];

interface CodigoEscrito {
	codigo: string;
}

// Cada resultado lleva su número: dos «Entrada válida» seguidas se dibujan de
// nuevo, para que la puerta note que la segunda también se validó. Guarda su
// código para no reenviarlo mientras está en pantalla
interface ResultadoMostrado extends ResultadoValidacion {
	numero: number;
	codigo: string;
}

// Validar entradas en la puerta (CU-15). Primero se elige el evento, que queda
// en la URL (?evento=5); después cada entrada se valida con la cámara o
// escribiendo su código. Solo el Personal autorizado llega (rolGuard); quien
// autoriza es el backend.
@Component({
	selector: 'app-validar-entradas',
	imports: [FormField, LectorQr, RouterLink],
	templateUrl: './validar-entradas.page.html',
	styleUrl: './validar-entradas.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ValidarEntradasPage {
	private readonly accesos = inject(AccesosService);
	private readonly router = inject(Router);
	private readonly ruta = inject(ActivatedRoute);
	private readonly parametros = toSignal(this.ruta.queryParamMap, { requireSync: true });
	private readonly campo = viewChild<ElementRef<HTMLInputElement>>('campoCodigo');
	private readonly zona = inject(NgZone);

	// --- Elegir el evento (RF-2 y RF-3)
	protected readonly recurso = this.accesos.eventosParaValidar();
	protected readonly eventos = computed(() => (this.recurso.hasValue() ? this.recurso.value() : undefined));
	protected readonly errorDeCarga = computed(() => {
		const error = this.recurso.error();
		return error === undefined ? '' : mensajeDeError(comoErrorApi(error));
	});
	// Un id que no está en la lista (otro día, basura, un evento que ya terminó)
	// vuelve a la elección
	protected readonly elegido = computed(() => {
		const id = idDeLaUrl(this.parametros().get('evento'));
		return this.eventos()?.find((evento) => evento.idEvento === id);
	});
	private readonly idElegido = computed(() => this.elegido()?.idEvento);

	// --- Validar: lo que se muestra es del evento elegido; al cambiarlo se limpia
	protected readonly resultado = linkedSignal<number | undefined, ResultadoMostrado | null>({
		source: this.idElegido,
		computation: () => null,
	});
	protected readonly resultados = computed(() => {
		const actual = this.resultado();
		return actual === null ? [] : [actual];
	});
	protected readonly aviso = linkedSignal<number | undefined, string>({
		source: this.idElegido,
		computation: () => '',
	});
	protected readonly procesando = signal(false);
	private numero = 0;
	private retiro: ReturnType<typeof setTimeout> | undefined;

	private readonly escrito = signal<CodigoEscrito>({ codigo: '' });
	protected readonly formulario = form(
		this.escrito,
		schema<CodigoEscrito>((ruta) => {
			// Solo espacios cuenta como vacío: el código se envía sin ellos (RF-5)
			validate(ruta.codigo, ({ value }) =>
				value().trim() === '' ? { kind: 'required', message: 'Escribe el código de la entrada.' } : null,
			);
		}),
	);

	protected readonly fecha = fechaCorta;
	protected readonly hora = horaCorta;
	protected readonly terminaAlDiaSiguiente = terminaAlDiaSiguiente;
	protected readonly textoDelResultado = textoDelResultado;
	protected readonly esValida = esValida;

	constructor() {
		inject(DestroyRef).onDestroy(() => clearTimeout(this.retiro));
	}

	protected mensajeDelCodigo(): string {
		return mensajeDelCampo(this.formulario.codigo(), 'codigo', null);
	}

	// RF-13 y RF-15: la cámara emite en cada cuadro que ve el QR. El código cuyo
	// resultado está en pantalla no se reenvía: solo alarga el tiempo del
	// resultado. Mientras hay una petición, validar descarta lo que llega; un
	// error no deja resultado, así que el mismo código se reintenta
	protected async leer(codigo: string): Promise<void> {
		if (codigo === this.resultado()?.codigo) {
			this.retirarDespues();
			return;
		}
		await this.validar(codigo);
	}

	// RF-5: el código escrito a mano, cuando la cámara no puede leerlo
	protected async enviar(evento: Event): Promise<void> {
		evento.preventDefault();
		await submit(this.formulario, {
			action: async (formulario) => {
				if (await this.validar(formulario().value().codigo.trim())) {
					// Listo para la siguiente entrada
					this.formulario().reset({ codigo: '' });
					this.campo()?.nativeElement.focus();
				}
				return undefined;
			},
		});
		// Vacío: el aviso queda junto al campo y el foco va ahí
		if (this.formulario().invalid()) {
			this.campo()?.nativeElement.focus();
		}
	}

	// Devuelve si hubo resultado. Una petición a la vez: lo que llegue mientras
	// tanto se descarta
	private async validar(codigo: string): Promise<boolean> {
		const evento = this.elegido();
		if (evento === undefined || this.procesando()) {
			return false;
		}
		this.aviso.set('');
		// RF-7: un QR ajeno más largo no es de ninguna entrada; el backend lo
		// rechazaría como dato inválido en vez de decir que no corresponde
		if (codigo.length > LARGO_MAXIMO_DEL_CODIGO) {
			this.mostrar({ resultado: 'NO_CORRESPONDE', tipoEntrada: null }, codigo);
			return true;
		}
		this.procesando.set(true);
		try {
			this.mostrar(await this.accesos.validar(evento.idEvento, codigo), codigo);
			return true;
		} catch (error: unknown) {
			const problema = comoError(error);
			this.resultado.set(null);
			// RF-4: el evento dejó de admitir validaciones o ya no existe; se vuelve a la lista, al día
			if (problema.codigo !== undefined && EVENTO_PERDIDO.includes(problema.codigo)) {
				this.recurso.reload();
				await this.router.navigate([], { relativeTo: this.ruta });
			}
			this.aviso.set(mensajeDeError(problema));
			return false;
		} finally {
			this.procesando.set(false);
		}
	}

	private mostrar(respuesta: ResultadoValidacion, codigo: string): void {
		this.resultado.set({ ...respuesta, codigo, numero: ++this.numero });
		this.retirarDespues();
	}

	// Fuera de la zona: la pantalla no queda «ocupada» mientras el resultado se
	// ve; la signal igual avisa que hay que volver a dibujar
	private retirarDespues(): void {
		clearTimeout(this.retiro);
		this.retiro = this.zona.runOutsideAngular(() =>
			setTimeout(() => this.resultado.set(null), TIEMPO_EN_PANTALLA_MS),
		);
	}
}
