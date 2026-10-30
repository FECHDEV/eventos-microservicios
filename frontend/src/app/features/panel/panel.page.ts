import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { SesionService } from '../../core/sesion/sesion.service';

// La bienvenida con los accesos de cada rol. Desde CU-05 la portada es el
// catálogo y esto vive en /panel, enlazado desde la navegación del
// Administrador y del Personal autorizado.
@Component({
	selector: 'app-panel',
	imports: [RouterLink],
	templateUrl: './panel.page.html',
	styleUrl: './panel.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PanelPage {
	private readonly sesion = inject(SesionService);

	protected readonly nombre = computed(() => this.sesion.usuario()?.nombre ?? '');
	protected readonly esAdministrador = computed(() => this.sesion.rol() === 'ADMINISTRADOR');
	protected readonly esPersonal = computed(() => this.sesion.rol() === 'PERSONAL');
}
