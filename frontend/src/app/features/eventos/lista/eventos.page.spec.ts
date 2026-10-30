import { HttpErrorResponse } from '@angular/common/http';
import { Signal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { EventosService } from '../../../core/api/eventos.service';
import { EventoResumen, FiltrosDeEventos } from '../../../core/modelos/evento';
import { Pagina } from '../../../core/modelos/pagina';
import { EventosPage } from './eventos.page';

// Como responde GET /api/eventos (EventosControllerTest)
const JAZZ: EventoResumen = {
  idEvento: 5,
  nombre: 'Noche de jazz',
  fecha: '2026-10-04',
  horaInicio: '22:00:00',
  horaFin: '03:00:00',
  lugar: 'Teatro Municipal',
  estado: 'BORRADOR',
  ventaAbierta: false,
  tieneImagen: true,
};

const FERIA: EventoResumen = {
  idEvento: 6,
  nombre: 'Feria del libro',
  fecha: '2026-10-12',
  horaInicio: '10:00:00',
  horaFin: '18:00:00',
  lugar: 'Fexpocruz',
  estado: 'ACTIVO',
  ventaAbierta: true,
  tieneImagen: false,
};

function pagina(contenido: EventoResumen[], cambios: Partial<Pagina<EventoResumen>> = {}) {
  return { contenido, pagina: 0, tamano: 20, totalElementos: 21, totalPaginas: 2, ...cambios };
}

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
  listaFalsa = new RecursoFalso<Pagina<EventoResumen>>();
  filtros: Signal<FiltrosDeEventos> | null = null;
  // El id que pide cada miniatura: null es «no pedir»
  imagenesPedidas: Signal<number | null>[] = [];

  listado(filtros: Signal<FiltrosDeEventos>) {
    this.filtros = filtros;
    return this.listaFalsa;
  }

  imagen(idEvento: Signal<number | null>) {
    this.imagenesPedidas.push(idEvento);
    return new RecursoFalso<Blob>();
  }
}

describe('EventosPage', () => {
  let harness: RouterTestingHarness;
  let eventos: EventosFalso;

  // null: la lista todavía no llegó (o falló)
  async function abrir(url = '/eventos', contenido: Pagina<EventoResumen> | null = pagina([JAZZ, FERIA])) {
    TestBed.resetTestingModule();
    eventos = new EventosFalso();
    eventos.listaFalsa.value.set(contenido ?? undefined);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'eventos', component: EventosPage },
          { path: 'eventos/nuevo', children: [] },
          { path: 'eventos/:id', children: [] },
        ]),
        { provide: EventosService, useValue: eventos },
      ],
    });
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, EventosPage);
    harness.fixture.autoDetectChanges();
    await harness.fixture.whenStable();
  }

  function pantalla(): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  function texto(): string {
    return pantalla().textContent ?? '';
  }

  function url(): string {
    return TestBed.inject(Router).url;
  }

  function boton(texto: string): HTMLButtonElement {
    const botones: HTMLButtonElement[] = Array.from(pantalla().querySelectorAll('button'));
    const encontrado = botones.find((cada) => cada.textContent?.trim() === texto);
    if (encontrado === undefined) {
      throw new Error(`No hay botón «${texto}»`);
    }
    return encontrado;
  }

  function fila(nombre: string): HTMLTableRowElement {
    const filas: HTMLTableRowElement[] = Array.from(pantalla().querySelectorAll('tbody tr'));
    const encontrada = filas.find((cada) => cada.textContent?.includes(nombre));
    if (encontrada === undefined) {
      throw new Error(`No hay fila de «${nombre}»`);
    }
    return encontrada;
  }

  async function elegirEstado(valor: string) {
    const selector: HTMLSelectElement = pantalla().querySelector('#filtro-estado')!;
    selector.value = valor;
    selector.dispatchEvent(new Event('change'));
    await harness.fixture.whenStable();
  }

  // CU-10 RF-3: nombre, fecha, horario, lugar, estado y venta de cada evento
  it('muestra los eventos de la página', async () => {
    await abrir();

    const jazz = fila('Noche de jazz').textContent ?? '';
    expect(jazz).toContain('04/10/2026');
    expect(jazz).toContain('22:00');
    expect(jazz).toContain('03:00');
    expect(jazz).toContain('Teatro Municipal');
    expect(jazz).toContain('Borrador');
    expect(jazz).toContain('Cerrada');
    expect(fila('Feria del libro').textContent).toContain('Abierta');
    expect(texto()).toContain('Página 1 de 2');
    expect(texto()).toContain('21 eventos');
  });

  // decisiones.md → Fecha y horario: de 22:00 a 03:00 termina al día siguiente
  it('avisa cuando el evento termina al día siguiente', async () => {
    await abrir();

    expect(fila('Noche de jazz').textContent).toContain('termina al día siguiente');
    expect(fila('Feria del libro').textContent).not.toContain('termina al día siguiente');
  });

  // Etiqueta calculada: el catálogo ya no lo muestra y el estado sigue ACTIVO
  // (decisiones.md → Catálogo de eventos). «Hoy» se fija solo para la fecha
  describe('eventos terminados', () => {
    afterEach(() => vi.useRealTimers());

    it('marca «Terminado» a un activo cuya fecha ya pasó', async () => {
      vi.useFakeTimers({ toFake: ['Date'] });
      vi.setSystemTime(new Date(2026, 9, 13, 9, 0));
      await abrir();

      expect(fila('Feria del libro').textContent).toContain('Activo · Terminado');
      // Un borrador no está a la venta: no se marca aunque su fecha haya pasado
      expect(fila('Noche de jazz').textContent).not.toContain('Terminado');
    });

    it('un evento que todavía no terminó no se marca', async () => {
      vi.useFakeTimers({ toFake: ['Date'] });
      vi.setSystemTime(new Date(2026, 9, 12, 17, 59));
      await abrir();

      expect(fila('Feria del libro').textContent).not.toContain('Terminado');
    });
  });

  it('el nombre lleva a la ficha del evento', async () => {
    await abrir();

    const enlace = fila('Noche de jazz').querySelector<HTMLAnchorElement>('a');
    expect(enlace?.getAttribute('href')).toBe('/eventos/5');
  });

  // Pedido en la prueba manual: una acción explícita además del nombre
  it('cada fila tiene un enlace «Editar» que lleva a los datos del evento', async () => {
    await abrir();

    const editar = fila('Noche de jazz').querySelector<HTMLAnchorElement>('a.editar');
    expect(editar?.textContent?.trim()).toBe('Editar');
    expect(editar?.getAttribute('href')).toBe('/eventos/5/datos');
    expect(editar?.getAttribute('aria-label')).toBe('Editar Noche de jazz');
  });

  // Un evento cancelado es de solo lectura: el enlace no promete editarlo
  it('en un evento cancelado el enlace dice «Ver»', async () => {
    await abrir('/eventos', pagina([{ ...FERIA, estado: 'CANCELADO', ventaAbierta: false }]));

    const ver = fila('Feria del libro').querySelector<HTMLAnchorElement>('a.editar');
    expect(ver?.textContent?.trim()).toBe('Ver');
    expect(ver?.getAttribute('aria-label')).toBe('Ver Feria del libro');
  });

  it('ofrece registrar un evento nuevo', async () => {
    await abrir();

    const enlace = pantalla().querySelector<HTMLAnchorElement>('a[href="/eventos/nuevo"]');
    expect(enlace?.textContent).toContain('Nuevo evento');
  });

  // Solo se piden las imágenes que existen: sin imagen queda el fondo neutro
  it('solo pide la miniatura de los eventos con imagen', async () => {
    await abrir();

    expect(eventos.imagenesPedidas.map((pedido) => pedido())).toEqual([5, null]);
  });

  // CU-10 RF-5: el filtro va a la URL y vuelve a la primera página
  it('filtrar por estado cambia la URL y vuelve a la primera página', async () => {
    await abrir('/eventos?pagina=1');

    await elegirEstado('ACTIVO');

    expect(url()).toBe('/eventos?estado=ACTIVO');
    expect(eventos.filtros?.()).toEqual({ estado: 'ACTIVO', pagina: 0 });
  });

  it('elegir «Todos» quita el filtro de la URL', async () => {
    await abrir('/eventos?estado=ACTIVO');

    await elegirEstado('');

    expect(url()).toBe('/eventos');
  });

  // Recargar o compartir el enlace muestra la misma lista
  it('lee el filtro y la página de la URL', async () => {
    await abrir('/eventos?estado=CANCELADO&pagina=1');

    expect(eventos.filtros?.()).toEqual({ estado: 'CANCELADO', pagina: 1 });
    const selector: HTMLSelectElement = pantalla().querySelector('#filtro-estado')!;
    expect(selector.value).toBe('CANCELADO');
  });

  // La URL la puede escribir cualquiera
  it('descarta un estado o una página que no son válidos', async () => {
    await abrir('/eventos?estado=TERMINADO&pagina=1e20');

    expect(eventos.filtros?.()).toEqual({ estado: null, pagina: 0 });
  });

  it('pasa a la página siguiente', async () => {
    await abrir();

    boton('Siguiente').click();
    await harness.fixture.whenStable();

    expect(url()).toBe('/eventos?pagina=1');
  });

  it('una página que no existe ofrece ir a la primera', async () => {
    await abrir('/eventos?pagina=9', pagina([], { pagina: 9 }));

    expect(texto()).toContain('Esta página no existe.');
    boton('Ir a la primera página').click();
    await harness.fixture.whenStable();

    expect(url()).toBe('/eventos');
  });

  it('sin eventos invita a registrar el primero', async () => {
    await abrir('/eventos', pagina([], { totalElementos: 0, totalPaginas: 0 }));

    expect(texto()).toContain('Todavía no hay eventos.');
  });

  it('con un filtro sin resultados lo dice', async () => {
    await abrir('/eventos?estado=CANCELADO', pagina([], { totalElementos: 0, totalPaginas: 0 }));

    expect(texto()).toContain('No hay eventos en ese estado.');
  });

  // Un 403 (otro administrador le quitó el rol) o la red caída
  it('si la carga falla, lo explica y ofrece reintentar', async () => {
    await abrir('/eventos', null);
    eventos.listaFalsa.error.set(
      new HttpErrorResponse({
        status: 403,
        error: { status: 403, detail: 'Sin permiso', codigo: 'ACCESO_DENEGADO' },
      }),
    );
    await harness.fixture.whenStable();

    expect(texto()).toContain('Tu cuenta no tiene acceso a esta parte del sistema.');
    boton('Reintentar').click();
    expect(eventos.listaFalsa.reload).toHaveBeenCalled();
  });
});
