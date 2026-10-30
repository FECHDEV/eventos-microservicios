import {
	afterNextRender,
	ChangeDetectionStrategy,
	Component,
	computed,
	ElementRef,
	inject,
	Injector,
	linkedSignal,
	signal,
} from '@angular/core';
import { disabled, form, FormField, schema, submit, validate } from '@angular/forms/signals';

import { mensajeDeError, mensajeDelCampo } from '../../core/api/mensajes-error';
import { ReportesService } from '../../core/api/reportes.service';
import { comoError, ErrorApi } from '../../core/modelos/error-api';
import {
	Criterio,
	CRITERIOS_POR_TIPO,
	FormatoDeArchivo,
	FormatoDeEnvio,
	NOMBRES_DE_REPORTE,
	Reporte,
	SolicitudDeEnvio,
	SolicitudDeReporte,
	TIPOS_DE_REPORTE,
	TipoReporte,
} from '../../core/modelos/reporte';
import { descargarArchivo } from '../../shared/descarga/descargar-archivo';
import { fechaCorta, horaCorta } from '../../shared/formato/fechas';
import { enfocarCampo } from '../../shared/formularios/primer-campo-con-error';
import { cantidad } from '../../shared/formato/precios';
import { TablaDeReporte } from './tabla/tabla-de-reporte';

// Lo que se elige en la pantalla; vacío es «Todos» o «sin fecha»
interface Filtros {
	tipo: TipoReporte;
	desde: string;
	hasta: string;
	idEvento: string;
	idTipoEntrada: string;
	estado: string;
	idPersonal: string;
}

const INICIALES: Filtros = {
	tipo: 'VENTAS',
	desde: '',
	hasta: '',
	idEvento: '',
	idTipoEntrada: '',
	estado: '',
	idPersonal: '',
};

const NUMERICOS: readonly Criterio[] = ['idEvento', 'idTipoEntrada', 'idPersonal'];

// Lo que se escribe para mandar el reporte por correo (CU-14 RF-19)
interface EnvioDeCorreo {
	destinatarios: string;
	formato: FormatoDeEnvio;
}

const ENVIO_INICIAL: EnvioDeCorreo = { destinatarios: '', formato: 'PDF' };

// Lo que devolvió la última generación, junto con la solicitud que la pidió
interface Resultado {
	clave: string;
	reporte?: Reporte;
	error?: ErrorApi;
}

// CU-14: el Administrador elige el tipo, los filtros de ese tipo, genera el
// reporte y lo descarga en PDF o Excel. Lo que se ve corresponde siempre a los
// filtros que se ven: cambiar uno oculta el reporte anterior.
@Component({
	selector: 'app-reportes',
	imports: [FormField, TablaDeReporte],
	templateUrl: './reportes.page.html',
	styleUrl: './reportes.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReportesPage {
	private readonly reportes = inject(ReportesService);
	private readonly elemento = inject<ElementRef<HTMLElement>>(ElementRef);
	private readonly injector = inject(Injector);

	protected readonly tipos = TIPOS_DE_REPORTE;
	protected readonly nombresDeReporte = NOMBRES_DE_REPORTE;

	protected readonly opciones = this.reportes.opciones();
	protected readonly eventos = computed(() => (this.opciones.hasValue() ? this.opciones.value()!.eventos : []));
	protected readonly personal = computed(() => (this.opciones.hasValue() ? this.opciones.value()!.personal : []));
	protected readonly opcionesCargando = computed(
		() => !this.opciones.hasValue() && this.opciones.error() === undefined,
	);
	protected readonly errorDeOpciones = computed(() => this.opciones.error() !== undefined);

	private readonly filtros = signal<Filtros>(INICIALES);
	protected readonly formulario = form(
		this.filtros,
		schema<Filtros>((ruta) => {
			disabled(ruta.idTipoEntrada, { when: ({ valueOf }) => valueOf(ruta.idEvento) === '' });
			// Solo cuenta si el tipo elegido tiene rango de fechas
			validate(ruta.hasta, ({ value, valueOf }) => {
				const desde = valueOf(ruta.desde);
				const conRango = CRITERIOS_POR_TIPO[valueOf(ruta.tipo)].includes('hasta');
				return conRango && value() !== '' && desde !== '' && value() < desde
					? { kind: 'rangoInvertido', message: 'La fecha hasta no puede ser anterior a la fecha desde.' }
					: null;
			});
		}),
	);

	protected readonly criterios = computed(() => CRITERIOS_POR_TIPO[this.filtros().tipo]);
	protected readonly tiposDelEvento = computed(
		() => this.eventos().find((evento) => String(evento.idEvento) === this.filtros().idEvento)?.tipos ?? [],
	);

	// Lo que viajaría ahora: el tipo y solo sus criterios con valor
	private readonly solicitud = computed(() => aSolicitud(this.filtros()));
	private readonly clave = computed(() => JSON.stringify(this.solicitud()));

	private readonly resultado = signal<Resultado | null>(null);
	private readonly vigente = computed(() => {
		const resultado = this.resultado();
		return resultado !== null && resultado.clave === this.clave() ? resultado : null;
	});
	protected readonly reporte = computed(() => this.vigente()?.reporte ?? null);
	private readonly error = computed(() => this.vigente()?.error ?? null);
	// Un 400 señala un filtro: su mensaje va bajo el campo
	private readonly rechazo = computed(() => (this.error()?.codigo === 'DATOS_INVALIDOS' ? this.error() : null));
	protected readonly sinResultados = computed(() =>
		this.error()?.codigo === 'REPORTE_SIN_RESULTADOS' ? mensajeDeError(this.error()) : '',
	);
	protected readonly aviso = computed(() => {
		const error = this.error();
		return error === null || this.rechazo() !== null || this.sinResultados() !== '' ? '' : mensajeDeError(error);
	});

	protected readonly generando = signal(false);
	private pedidos = 0;

	protected readonly descargando = signal<FormatoDeArchivo | null>(null);
	protected readonly errorDeDescarga = signal('');

	protected readonly generado = computed(() => {
		const reporte = this.reporte();
		return reporte === null
			? ''
			: `Generado el ${fechaCorta(reporte.fechaGeneracion)} ${horaCorta(reporte.fechaGeneracion.slice(11))}`;
	});
	protected readonly criteriosAplicados = computed(() => {
		const criterios = this.reporte()?.criterios ?? [];
		return criterios.length === 0
			? 'Sin filtros'
			: criterios.map((criterio) => `${criterio.nombre}: ${criterio.valor}`).join(' · ');
	});
	protected readonly filasDelDetalle = computed(() => {
		const filas = this.reporte()?.detalle.filas.length ?? 0;
		return `${cantidad(filas)} ${filas === 1 ? 'fila' : 'filas'}`;
	});

	protected readonly fecha = fechaCorta;

	protected muestra(criterio: Criterio): boolean {
		return this.criterios().includes(criterio);
	}

	protected mensaje(campo: Criterio): string {
		return mensajeDelCampo(this.formulario[campo](), campo, this.rechazo());
	}

	// El tipo de entrada es del evento: al cambiar de evento vuelve a «Todos»
	protected elegirEvento(): void {
		this.filtros.update((filtros) => ({ ...filtros, idTipoEntrada: '' }));
	}

	protected reintentarOpciones(): void {
		this.opciones.reload();
	}

	protected async generar(evento: Event): Promise<void> {
		evento.preventDefault();
		if (this.generando()) {
			return;
		}
		await submit(this.formulario, {
			action: async () => {
				const solicitud = this.solicitud();
				const clave = this.clave();
				const pedido = ++this.pedidos;
				this.generando.set(true);
				this.errorDeDescarga.set('');
				try {
					const reporte = await this.reportes.generar(solicitud);
					this.recibir(pedido, { clave, reporte });
					if (pedido === this.pedidos && clave === this.clave()) {
						afterNextRender(
							() => this.elemento.nativeElement.querySelector<HTMLElement>('#titulo-reporte')?.focus(),
							{ injector: this.injector },
						);
					}
				} catch (error) {
					this.recibir(pedido, { clave, error: comoError(error) });
				} finally {
					if (pedido === this.pedidos) {
						this.generando.set(false);
					}
				}
				return undefined;
			},
		});
		// Foco al campo a corregir, para no dejar a quien usa teclado buscando qué falló
		enfocarCampo(
			this.elemento.nativeElement,
			this.criterios().find((criterio) => this.formulario[criterio]().invalid()) ?? null,
		);
	}

	protected async descargar(formato: FormatoDeArchivo): Promise<void> {
		const reporte = this.reporte();
		if (reporte === null || this.descargando() !== null) {
			return;
		}
		this.descargando.set(formato);
		this.errorDeDescarga.set('');
		try {
			const archivo = await this.reportes.exportar(reporte.idReporte, formato);
			descargarArchivo(archivo, nombreDelArchivo(reporte, formato));
		} catch {
			this.errorDeDescarga.set('No se pudo descargar el archivo. Vuelve a intentar.');
		} finally {
			this.descargando.set(null);
		}
	}

	// Una respuesta vieja (llegó después de otra generación) no pisa a la nueva
	private recibir(pedido: number, resultado: Resultado): void {
		if (pedido === this.pedidos) {
			this.resultado.set(resultado);
		}
	}

	// CU-14 RF-19 a RF-24: el panel de envío del reporte que está en pantalla.
	// La pantalla revisa la forma, el dominio y el tope de 10 para avisar antes;
	// quien decide es el backend, y su mensaje sale bajo el mismo campo
	protected readonly dominio = computed(() =>
		this.opciones.hasValue() ? this.opciones.value()!.dominioDeCorreo : '',
	);
	private readonly envio = signal<EnvioDeCorreo>(ENVIO_INICIAL);
	protected readonly formularioEnvio = form(
		this.envio,
		schema<EnvioDeCorreo>((ruta) => {
			validate(ruta.destinatarios, ({ value }) => {
				const mensaje = problemaDeDestinatarios(aDestinatarios(value()), this.dominio());
				return mensaje === null ? null : { kind: 'destinatarios', message: mensaje };
			});
		}),
	);

	protected readonly enviando = signal(false);
	// El aviso y el rechazo son del reporte y de las direcciones con que se
	// envió: al cambiar cualquiera de los dos, se borran
	private readonly envioEnPantalla = computed(
		() => `${this.reporte()?.idReporte ?? ''}|${this.envio().destinatarios}`,
	);
	protected readonly confirmacion = linkedSignal({ source: this.envioEnPantalla, computation: () => '' });
	private readonly errorDeEnvio = linkedSignal<string, ErrorApi | null>({
		source: this.envioEnPantalla,
		computation: () => null,
	});
	private readonly rechazoEnvio = computed(() =>
		this.errorDeEnvio()?.codigo === 'DATOS_INVALIDOS' ? this.errorDeEnvio() : null,
	);
	protected readonly avisoDeEnvio = computed(() => {
		const error = this.errorDeEnvio();
		return error === null || this.rechazoEnvio() !== null ? '' : mensajeDeError(error);
	});

	protected mensajeDeDestinatarios(): string {
		return mensajeDelCampo(this.formularioEnvio.destinatarios(), 'destinatarios', this.rechazoEnvio());
	}

	protected async enviarPorCorreo(evento: Event): Promise<void> {
		evento.preventDefault();
		const reporte = this.reporte();
		if (reporte === null || this.enviando()) {
			return;
		}
		await submit(this.formularioEnvio, {
			action: async () => {
				const envio = this.envio();
				const solicitud: SolicitudDeEnvio = {
					destinatarios: aDestinatarios(envio.destinatarios),
					formato: envio.formato,
				};
				this.enviando.set(true);
				this.confirmacion.set('');
				try {
					const respuesta = await this.reportes.enviarPorCorreo(reporte.idReporte, solicitud);
					// Si mientras tanto se generó otro reporte, el aviso no es de este
					if (this.reporte()?.idReporte !== reporte.idReporte) {
						return undefined;
					}
					this.errorDeEnvio.set(null);
					const cuantos = respuesta.destinatarios;
					this.confirmacion.set(`Enviado a ${cuantos} ${cuantos === 1 ? 'destinatario' : 'destinatarios'}.`);
				} catch (error) {
					if (this.reporte()?.idReporte !== reporte.idReporte) {
						return undefined;
					}
					this.confirmacion.set('');
					this.errorDeEnvio.set(comoError(error));
				} finally {
					this.enviando.set(false);
				}
				return undefined;
			},
		});
		// Foco al campo a corregir, como en el formulario de filtros
		if (this.formularioEnvio.destinatarios().invalid()) {
			enfocarCampo(this.elemento.nativeElement, 'destinatarios');
		}
	}
}

function aSolicitud(filtros: Filtros): SolicitudDeReporte {
	const solicitud: SolicitudDeReporte = { tipo: filtros.tipo };
	for (const criterio of CRITERIOS_POR_TIPO[filtros.tipo]) {
		const valor = filtros[criterio];
		// El tipo de entrada va solo con su evento
		if (valor === '' || (criterio === 'idTipoEntrada' && filtros.idEvento === '')) {
			continue;
		}
		solicitud[criterio] = NUMERICOS.includes(criterio) ? Number(valor) : valor;
	}
	return solicitud;
}

// reporte-ventas-12.pdf, como lo nombra el backend
function nombreDelArchivo(reporte: Reporte, formato: FormatoDeArchivo): string {
	return `reporte-${reporte.tipo.toLowerCase()}-${reporte.idReporte}.${formato === 'pdf' ? 'pdf' : 'xlsx'}`;
}

// Lo que se escribe, separado por comas, como el input type="email" multiple
function aDestinatarios(texto: string): string[] {
	return texto
		.split(',')
		.map((cada) => cada.trim())
		.filter((cada) => cada !== '');
}

// Como el backend: la parte local sin comas, «<» ni «;», que romperían el correo
const CORREO = /^[a-z0-9!#$%&'*+/=?^_`{|}~.-]+@[a-z0-9-]+(\.[a-z0-9-]+)+$/i;
const MAXIMO_DESTINATARIOS = 10;

// El primer problema de la lista, nombrando la dirección que falla. Sin el
// dominio (las opciones no cargaron) lo revisa solo el backend
function problemaDeDestinatarios(lista: string[], dominio: string): string | null {
	if (lista.length === 0) {
		return 'Escribe al menos un destinatario.';
	}
	if (lista.length > MAXIMO_DESTINATARIOS) {
		return `Escribe hasta ${MAXIMO_DESTINATARIOS} destinatarios.`;
	}
	const malEscrita = lista.find((cada) => !CORREO.test(cada));
	if (malEscrita !== undefined) {
		return `«${malEscrita}» no es una dirección de correo válida.`;
	}
	const ajena = lista.find((cada) => dominio !== '' && !cada.toLowerCase().endsWith(`@${dominio}`));
	return ajena === undefined ? null : `«${ajena}» no es del dominio ${dominio}.`;
}
