import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { DESCRIPCIONES_DE_ROL, NOMBRES_DE_ROL, ROLES_POR_AREA } from '../../core/modelos/usuario';
import { SesionService } from '../../core/sesion/sesion.service';

type Area = keyof typeof ROLES_POR_AREA;

// La bienvenida del personal: su rol, qué hace, y los accesos que ese rol abre.
// Desde CU-05 la portada es el catálogo y esto vive en /panel.
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
	protected readonly nombreDelRol = computed(() => {
		const rol = this.sesion.rol();
		return rol === null ? '' : NOMBRES_DE_ROL[rol];
	});
	protected readonly descripcionDelRol = computed(() => {
		const rol = this.sesion.rol();
		return rol === null ? '' : DESCRIPCIONES_DE_ROL[rol];
	});

	protected puede(area: Area): boolean {
		const rol = this.sesion.rol();
		return rol !== null && (ROLES_POR_AREA[area] as readonly string[]).includes(rol);
	}
}
