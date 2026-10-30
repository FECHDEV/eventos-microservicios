import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { comoErrorApi } from '../../../core/api/autenticacion.service';
import { CatalogoService } from '../../../core/api/catalogo.service';
import { mensajeDeError } from '../../../core/api/mensajes-error';
import { terminaAlDiaSiguiente } from '../../../core/modelos/evento';
import { SesionService } from '../../../core/sesion/sesion.service';
import { fechaCorta, horaCorta } from '../../../shared/formato/fechas';
import { precio } from '../../../shared/formato/precios';
import { idDeLaUrl } from '../../../shared/navegacion/id-de-la-url';

// El detalle de un evento del catálogo (CU-05, pasos 5 y 6). Público, como la
// portada. Cada tipo disponible lleva a comprarlo (CU-06); sin sesión, la ruta de
// compra manda a iniciar sesión y vuelve.
@Component({
	selector: 'app-evento-del-catalogo',
	imports: [RouterLink],
	templateUrl: './evento-del-catalogo.page.html',
	styleUrl: './evento-del-catalogo.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EventoDelCatalogoPage {
	private readonly catalogo = inject(CatalogoService);
	private readonly sesion = inject(SesionService);
	private readonly parametros = toSignal(inject(ActivatedRoute).paramMap, { requireSync: true });

	// Basura en la URL no llega al backend
	private readonly id = computed(() => idDeLaUrl(this.parametros().get('id')));
	protected readonly recurso = this.catalogo.evento(this.id);
	protected readonly evento = computed(() => (this.recurso.hasValue() ? this.recurso.value() : undefined));

	// Un borrador, un cancelado o uno terminado responden 404 igual que uno que no
	// existe (RF-16): para quien consulta, es el mismo caso
	protected readonly noEncontrado = computed(() => {
		const error = this.recurso.error();
		return this.id() === null || (error instanceof HttpErrorResponse && error.status === 404);
	});
	protected readonly errorDeCarga = computed(() => {
		const error = this.recurso.error();
		return error === undefined || this.noEncontrado() ? '' : mensajeDeError(comoErrorApi(error));
	});

	// Cada línea es un párrafo; las vacías no dejan huecos
	protected readonly parrafos = computed(() =>
		(this.evento()?.descripcion ?? '')
			.split('\n')
			.map((linea) => linea.trim())
			.filter((linea) => linea !== ''),
	);

	// Comprar es del Cliente; sin sesión también se ofrece, para entrar y volver
	// (CU-06 RF-22 a RF-24). Ocultarlo a otros roles es usabilidad: el backend
	// responde 403
	protected readonly puedeComprar = computed(() => !this.sesion.autenticado() || this.sesion.rol() === 'CLIENTE');

	protected readonly fecha = fechaCorta;
	protected readonly hora = horaCorta;
	protected readonly precio = precio;
	protected readonly terminaAlDiaSiguiente = terminaAlDiaSiguiente;

	protected urlDeImagen(idEvento: number): string {
		return this.catalogo.urlDeImagen(idEvento);
	}
}
