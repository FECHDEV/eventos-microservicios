import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { DevolucionesService } from '../../../core/api/devoluciones.service';
import { EventoCancelado } from '../../../core/modelos/devolucion';
import { DevolucionesPage } from './devoluciones.page';

// Como responde GET /api/devoluciones (DevolucionesControllerTest)
const CONCIERTO: EventoCancelado = {
  idEvento: 5,
  nombre: 'Concierto',
  fecha: '2026-10-04',
  horaInicio: '22:00:00',
  horaFin: '03:00:00',
  lugar: 'Teatro Municipal',
  pendientes: 2,
};

const OBRA: EventoCancelado = {
  idEvento: 3,
  nombre: 'Obra de teatro',
  fecha: '2026-08-15',
  horaInicio: '20:00:00',
  horaFin: '22:00:00',
  lugar: 'Sala B',
  pendientes: 0,
};

// Lo que la pantalla usa del httpResource
class RecursoFalso<T> {
  readonly value = signal<T | undefined>(undefined);
  readonly error = signal<unknown>(undefined);
  readonly reload = vi.fn(() => true);
  hasValue(): boolean {
    return this.value() !== undefined;
  }
}

class DevolucionesFalso {
  recurso = new RecursoFalso<EventoCancelado[]>();

  eventosCancelados() {
    return this.recurso;
  }
}

describe('DevolucionesPage', () => {
  let harness: RouterTestingHarness;
  let servicio: DevolucionesFalso;

  // null: todavía no llegaron (o fallaron)
  async function abrir(cancelados: EventoCancelado[] | null) {
    TestBed.resetTestingModule();
    servicio = new DevolucionesFalso();
    servicio.recurso.value.set(cancelados ?? undefined);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'devoluciones', component: DevolucionesPage },
          { path: 'devoluciones/:id', children: [] },
        ]),
        { provide: DevolucionesService, useValue: servicio },
      ],
    });
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/devoluciones', DevolucionesPage);
    harness.fixture.autoDetectChanges();
    await harness.fixture.whenStable();
  }

  function pantalla(): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  function texto(): string {
    return pantalla().textContent ?? '';
  }

  function filas(): HTMLTableRowElement[] {
    return Array.from(pantalla().querySelectorAll<HTMLTableRowElement>('tbody tr'));
  }

  it('se titula «Devoluciones» y explica que la devolución se hace fuera del sistema', async () => {
    await abrir([CONCIERTO]);

    expect(pantalla().querySelector('h1')?.textContent?.trim()).toBe('Devoluciones');
    expect(texto()).toContain('La devolución del dinero se hace fuera del sistema');
  });

  // RF-2: en el orden en que llegan, con sus pendientes; un 0 se muestra
  it('lista los eventos cancelados con sus pendientes', async () => {
    await abrir([CONCIERTO, OBRA]);

    expect(filas()).toHaveLength(2);
    const [concierto, obra] = filas();
    expect(concierto.textContent).toContain('Concierto');
    expect(concierto.textContent).toContain('04/10/2026');
    expect(concierto.textContent).toContain('22:00–03:00');
    expect(concierto.textContent).toContain('Teatro Municipal');
    expect(concierto.querySelector('[data-etiqueta="Pendientes"]')?.textContent?.trim()).toBe('2');
    expect(obra.textContent).toContain('Obra de teatro');
    expect(obra.querySelector('[data-etiqueta="Pendientes"]')?.textContent?.trim()).toBe('0');
  });

  it('cada evento lleva a sus devoluciones', async () => {
    await abrir([CONCIERTO]);

    expect(filas()[0].querySelector('a')?.getAttribute('href')).toBe('/devoluciones/5');
  });

  // RF-3
  it('sin eventos cancelados lo dice y no muestra la tabla', async () => {
    await abrir([]);

    expect(texto()).toContain('No hay eventos cancelados.');
    expect(pantalla().querySelector('table')).toBeNull();
  });

  it('mientras carga lo dice', async () => {
    await abrir(null);

    expect(pantalla().querySelector('[role="status"]')?.textContent).toContain('Cargando los eventos cancelados…');
  });

  it('si la carga falla, lo dice y ofrece reintentar', async () => {
    await abrir(null);
    servicio.recurso.error.set(new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') }));
    await harness.fixture.whenStable();

    expect(pantalla().querySelector('[role="alert"]')?.textContent).toContain('No pudimos conectar con el servidor');
    Array.from(pantalla().querySelectorAll('button'))
      .find((boton) => boton.textContent?.trim() === 'Reintentar')!
      .click();
    expect(servicio.recurso.reload).toHaveBeenCalled();
  });
});
