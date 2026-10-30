import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, Signal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { EventosService } from '../../../core/api/eventos.service';
import { Evento } from '../../../core/modelos/evento';
import { EventoActual } from '../evento-actual';
import { EventoPage } from './evento.page';

// Como responde GET /api/eventos/5 (EventosControllerTest)
const JAZZ: Evento = {
  idEvento: 5,
  nombre: 'Noche de jazz',
  descripcion: 'Cuarteto',
  fecha: '2026-10-04',
  fechaOriginal: null,
  horaInicio: '22:00:00',
  horaFin: '03:00:00',
  lugar: 'Teatro Municipal',
  restriccionEdad: 18,
  limiteCompra: 4,
  estado: 'BORRADOR',
  ventaAbierta: false,
  tieneImagen: false,
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

class EventosFalso {
  eventoFalso = new RecursoFalso<Evento>();
  idPedido: Signal<number | null> | null = null;

  evento(idEvento: Signal<number | null>) {
    this.idPedido = idEvento;
    return this.eventoFalso;
  }

  imagen() {
    return new RecursoFalso<Blob>();
  }
}

// Una pestaña de prueba: lee el evento compartido y puede reemplazarlo, como
// harán Datos, Tipos y Estado
@Component({
  template: `<p class="pestana">Pestaña de {{ actual.evento()?.nombre }}</p>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
class PestanaDePrueba {
  readonly actual = inject(EventoActual);
}

function error(status: number, codigo?: string): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: { status, detail: 'detalle', codigo } });
}

describe('EventoPage', () => {
  let harness: RouterTestingHarness;
  let eventos: EventosFalso;

  // null: el evento todavía no llegó (o falló)
  async function abrir(url = '/eventos/5/datos', evento: Evento | null = JAZZ) {
    TestBed.resetTestingModule();
    eventos = new EventosFalso();
    eventos.eventoFalso.value.set(evento ?? undefined);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'eventos', children: [] },
          {
            path: 'eventos/:id',
            component: EventoPage,
            children: [
              { path: '', pathMatch: 'full', redirectTo: 'datos' },
              { path: 'datos', component: PestanaDePrueba },
              { path: 'tipos', component: PestanaDePrueba },
              { path: 'estado', component: PestanaDePrueba },
            ],
          },
        ]),
        { provide: EventosService, useValue: eventos },
      ],
    });
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    harness.fixture.autoDetectChanges();
    await harness.fixture.whenStable();
  }

  function pantalla(): HTMLElement {
    return harness.fixture.nativeElement as HTMLElement;
  }

  function texto(): string {
    return pantalla().textContent ?? '';
  }

  function enlace(textoDelEnlace: string): HTMLAnchorElement {
    const enlaces: HTMLAnchorElement[] = Array.from(pantalla().querySelectorAll('a'));
    const encontrado = enlaces.find((cada) => cada.textContent?.trim() === textoDelEnlace);
    if (encontrado === undefined) {
      throw new Error(`No hay enlace «${textoDelEnlace}»`);
    }
    return encontrado;
  }

  it('muestra el encabezado del evento', async () => {
    await abrir();

    expect(texto()).toContain('Noche de jazz');
    expect(texto()).toContain('Borrador');
    expect(texto()).toContain('Venta cerrada');
  });

  // Etiqueta calculada, igual que en la lista (decisiones.md → Catálogo de eventos)
  it('el encabezado de un activo que ya terminó dice «Terminado»', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 6, 9, 0));
    try {
      await abrir('/eventos/5/datos', { ...JAZZ, estado: 'ACTIVO', ventaAbierta: false });

      expect(pantalla().querySelector('app-estado-de-evento')?.textContent?.trim()).toBe('Activo · Terminado');
    } finally {
      vi.useRealTimers();
    }
  });

  it('pide el evento del id de la URL', async () => {
    await abrir();

    expect(eventos.idPedido?.()).toBe(5);
  });

  // Las pestañas son rutas hijas: cada una tiene su URL
  it('las pestañas llevan a sus rutas y marcan la actual', async () => {
    await abrir('/eventos/5/tipos');

    expect(enlace('Datos').getAttribute('href')).toBe('/eventos/5/datos');
    expect(enlace('Tipos de entrada').getAttribute('href')).toBe('/eventos/5/tipos');
    expect(enlace('Estado').getAttribute('href')).toBe('/eventos/5/estado');
    expect(enlace('Tipos de entrada').getAttribute('aria-current')).toBe('page');
    expect(enlace('Datos').getAttribute('aria-current')).toBeNull();
  });

  it('entrar al evento lleva a la pestaña Datos', async () => {
    await abrir('/eventos/5');

    expect(enlace('Datos').getAttribute('aria-current')).toBe('page');
  });

  // El evento se carga una vez y lo comparten las pestañas
  it('la pestaña recibe el evento de la ficha', async () => {
    await abrir();

    expect(pantalla().querySelector('.pestana')?.textContent).toContain('Pestaña de Noche de jazz');
  });

  // Cada escritura reemplaza el evento: el encabezado queda al día sin recargar
  it('lo que reemplaza una pestaña se ve en el encabezado', async () => {
    await abrir();
    const actual = harness.fixture.debugElement.query((nodo) => nodo.componentInstance instanceof PestanaDePrueba)
      .componentInstance.actual as EventoActual;

    actual.reemplazar({ ...JAZZ, estado: 'ACTIVO', ventaAbierta: true });
    await harness.fixture.whenStable();

    expect(texto()).toContain('Activo');
    expect(texto()).toContain('Venta abierta');
  });

  // Subir o quitar la imagen cambia la versión, para que se vuelva a pedir
  it('cambiar la imagen reemplaza el evento y sube la versión de la imagen', async () => {
    await abrir();
    const actual = harness.fixture.debugElement.query((nodo) => nodo.componentInstance instanceof PestanaDePrueba)
      .componentInstance.actual as EventoActual;

    actual.imagenCambiada({ ...JAZZ, tieneImagen: true });

    expect(actual.versionDeImagen()).toBe(1);
    expect(actual.evento()?.tieneImagen).toBe(true);
  });

  it('un evento postergado muestra su fecha original', async () => {
    await abrir('/eventos/5/datos', { ...JAZZ, estado: 'POSTERGADO', fecha: '2026-11-01', fechaOriginal: '2026-10-04' });

    expect(texto()).toContain('Fecha original: 04/10/2026');
  });

  it('un evento que no se postergó no muestra fecha original', async () => {
    await abrir();

    expect(texto()).not.toContain('Fecha original');
  });

  // Basura en la URL: no llega al backend
  it.each(['/eventos/abc/datos', '/eventos/0/datos'])('%s no pide nada y ofrece volver a la lista', async (url) => {
    await abrir(url, null);

    expect(eventos.idPedido?.()).toBeNull();
    expect(texto()).toContain('No encontramos ese evento.');
    expect(enlace('Volver a la lista de eventos').getAttribute('href')).toBe('/eventos');
  });

  it('un evento que no existe ofrece volver a la lista', async () => {
    await abrir('/eventos/999999/datos', null);
    eventos.eventoFalso.error.set(error(404, 'EVENTO_NO_ENCONTRADO'));
    await harness.fixture.whenStable();

    expect(texto()).toContain('No encontramos ese evento.');
    expect(pantalla().querySelector('.pestana')).toBeNull();
  });

  // La red caída o un 500: se explica y se ofrece reintentar
  it('si la carga falla por otro motivo, ofrece reintentar', async () => {
    await abrir('/eventos/5/datos', null);
    // Una caída real de la red no trae cuerpo: llega como ProgressEvent
    eventos.eventoFalso.error.set(new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') }));
    await harness.fixture.whenStable();

    expect(texto()).toContain('No pudimos conectar con el servidor');
    const reintentar = Array.from(pantalla().querySelectorAll('button')).find(
      (boton) => boton.textContent?.trim() === 'Reintentar',
    );
    reintentar?.click();
    expect(eventos.eventoFalso.reload).toHaveBeenCalled();
  });

  // Hallazgo de la revisión de código: después de un conflicto la pestaña pide
  // recargar el evento; si esa recarga falla, la pestaña y lo escrito en ella
  // no pueden desaparecer
  it('si falla una recarga, la pestaña sigue y se ofrece reintentar arriba', async () => {
    await abrir();

    eventos.eventoFalso.value.set(undefined);
    eventos.eventoFalso.error.set(new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') }));
    await harness.fixture.whenStable();

    expect(pantalla().querySelector('.pestana')?.textContent).toContain('Pestaña de Noche de jazz');
    expect(texto()).toContain('No pudimos conectar con el servidor');
    const reintentar = Array.from(pantalla().querySelectorAll('button')).find(
      (boton) => boton.textContent?.trim() === 'Reintentar',
    );
    expect(reintentar).toBeDefined();
  });

  // Mientras llega el evento no se muestra nada de otro
  it('mientras carga no muestra las pestañas', async () => {
    await abrir('/eventos/5/datos', null);

    expect(texto()).toContain('Cargando el evento…');
    expect(pantalla().querySelector('.pestana')).toBeNull();
  });
});
