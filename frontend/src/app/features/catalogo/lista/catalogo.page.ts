import { ChangeDetectionStrategy, Component, computed, ElementRef, inject, linkedSignal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { form, FormField, maxLength, schema, submit, validate } from '@angular/forms/signals';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { comoErrorApi } from '../../../core/api/autenticacion.service';
import { CatalogoService } from '../../../core/api/catalogo.service';
import { mensajeDeError, mensajeDelCampo } from '../../../core/api/mensajes-error';
import { FiltrosDelCatalogo } from '../../../core/modelos/catalogo';
import { aConsulta, LARGO_MAXIMO_DEL_TEXTO, leerFiltros } from '../../../core/modelos/filtros-en-la-url';
import { terminaAlDiaSiguiente } from '../../../core/modelos/evento';
import { fechaCorta, horaCorta } from '../../../shared/formato/fechas';
import { cantidad } from '../../../shared/formato/precios';
import { enfocarCampo, primerCampoConError } from '../../../shared/formularios/primer-campo-con-error';

// Lo que se escribe en el formulario; vacío es «no filtra»
interface Busqueda {
	texto: string;
	desde: string;
	hasta: string;
}

type CampoDeBusqueda = keyof Busqueda;

const CAMPOS: readonly CampoDeBusqueda[] = ['texto', 'desde', 'hasta'];

const VACIA: Busqueda = { texto: '', desde: '', hasta: '' };

// La portada (CU-05): los eventos disponibles, con o sin sesión. Los filtros y la
// página viven en la URL, así recargar o compartir el enlace muestra lo mismo.
@Component({
	selector: 'app-catalogo',
	imports: [FormField, RouterLink],
	templateUrl: './catalogo.page.html',
	styleUrl: './catalogo.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CatalogoPage {
	private readonly catalogo = inject(CatalogoService);
	private readonly ruta = inject(ActivatedRoute);
	private readonly router = inject(Router);
	private readonly elemento = inject(ElementRef<HTMLElement>);

	private readonly consulta = toSignal(this.ruta.queryParamMap, { requireSync: true });
	protected readonly filtros = computed(() => leerFiltros(this.consulta()), { equal: mismosFiltros });
	protected readonly hayFiltros = computed(() => {
		const { texto, desde, hasta } = this.filtros();
		return texto !== null || desde !== null || hasta !== null;
	});

	protected readonly listado = this.catalogo.listado(this.filtros);
	protected readonly pagina = computed(() => (this.listado.hasValue() ? this.listado.value() : undefined));
	private readonly error = computed(() => {
		const error = this.listado.error();
		return error === undefined ? null : comoErrorApi(error);
	});
	// Un 400 señala un filtro: su mensaje va bajo el campo, no en la tarjeta de error
	private readonly rechazo = computed(() => {
		const error = this.error();
		return error?.codigo === 'DATOS_INVALIDOS' ? error : null;
	});
	protected readonly errorDeCarga = computed(() => {
		const error = this.error();
		return error === null || this.rechazo() !== null ? '' : mensajeDeError(error);
	});

	// El formulario arranca con lo que dice la URL y se vuelve a llenar si cambia
	private readonly busqueda = linkedSignal<Busqueda>(() => {
		const { texto, desde, hasta } = this.filtros();
		return { texto: texto ?? '', desde: desde ?? '', hasta: hasta ?? '' };
	});
	protected readonly formulario = form(
		this.busqueda,
		schema<Busqueda>((ruta) => {
			maxLength(ruta.texto, LARGO_MAXIMO_DEL_TEXTO, { message: 'La búsqueda admite hasta 200 caracteres.' });
			validate(ruta.hasta, ({ value, valueOf }) => {
				const desde = valueOf(ruta.desde);
				return value() !== '' && desde !== '' && value() < desde
					? { kind: 'rangoInvertido', message: 'La fecha hasta no puede ser anterior a la fecha desde.' }
					: null;
			});
		}),
	);

	protected readonly fecha = fechaCorta;
	protected readonly hora = horaCorta;
	protected readonly cantidad = cantidad;
	protected readonly terminaAlDiaSiguiente = terminaAlDiaSiguiente;

	protected urlDeImagen(idEvento: number): string {
		return this.catalogo.urlDeImagen(idEvento);
	}

	protected mensaje(campo: CampoDeBusqueda): string {
		return mensajeDelCampo(this.formulario[campo](), campo, this.rechazo());
	}

	// Buscar cambia la lista entera: se vuelve a la primera página
	protected async buscar(evento: Event): Promise<void> {
		evento.preventDefault();
		await submit(this.formulario, {
			action: async (formulario) => {
				const { texto, desde, hasta } = formulario().value();
				this.navegar({ texto: texto.trim() || null, desde: desde || null, hasta: hasta || null, pagina: 0 });
				return undefined;
			},
		});
		// Foco al campo a corregir, para no dejar a quien usa teclado buscando qué falló
		if (this.formulario().invalid()) {
			const invalidos = Object.fromEntries(
				CAMPOS.map((campo) => [campo, this.formulario[campo]().invalid()]),
			) as Record<CampoDeBusqueda, boolean>;
			enfocarCampo(this.elemento.nativeElement, primerCampoConError(CAMPOS, invalidos));
		}
	}

	// Sin filtros en la URL, navegar a / no cambia nada: el formulario se vacía acá
	protected limpiar(): void {
		this.busqueda.set(VACIA);
		this.formulario().reset();
		this.navegar({ texto: null, desde: null, hasta: null, pagina: 0 });
	}

	protected irAPagina(pagina: number): void {
		this.navegar({ ...this.filtros(), pagina });
	}

	private navegar(filtros: FiltrosDelCatalogo): void {
		void this.router.navigate([], { relativeTo: this.ruta, queryParams: aConsulta(filtros) });
	}
}

// Otra navegación con la misma consulta no vuelve a pedir la lista
function mismosFiltros(a: FiltrosDelCatalogo, b: FiltrosDelCatalogo): boolean {
	return a.texto === b.texto && a.desde === b.desde && a.hasta === b.hasta && a.pagina === b.pagina;
}
