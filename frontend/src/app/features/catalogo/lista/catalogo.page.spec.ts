import { HttpErrorResponse } from '@angular/common/http';
import { Signal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { CatalogoService } from '../../../core/api/catalogo.service';
import { EventoDelCatalogoResumen, FiltrosDelCatalogo } from '../../../core/modelos/catalogo';
import { Pagina } from '../../../core/modelos/pagina';
import { CatalogoPage } from './catalogo.page';

// Como responde GET /api/catalogo (CatalogoControllerTest)
const JAZZ: EventoDelCatalogoResumen = {
  idEvento: 5,
  nombre: 'Noche de jazz',
  fecha: '2026-10-04',
  fechaOriginal: null,
  horaInicio: '22:00:00',
  horaFin: '03:00:00',
  lugar: 'Teatro Municipal',
  ventaAbierta: true,
  tieneImagen: true,
};

const FERIA: EventoDelCatalogoResumen = {
  idEvento: 6,
  nombre: 'Feria del libro',
  fecha: '2026-11-01',
  fechaOriginal: '2026-10-12',
  horaInicio: '10:00:00',
  horaFin: '18:00:00',
  lugar: 'Fexpocruz',
  ventaAbierta: false,
  tieneImagen: false,
};

function pagina(
  contenido: EventoDelCatalogoResumen[],
  cambios: Partial<Pagina<EventoDelCatalogoResumen>> = {},
): Pagina<EventoDelCatalogoResumen> {
  return { contenido, pagina: 0, tamano: 12, totalElementos: 13, totalPaginas: 2, ...cambios };
}

const VACIA = pagina([], { totalElementos: 0, totalPaginas: 0 });

// Lo que la pantalla usa del httpResource
class RecursoFalso<T> {
  readonly value = signal<T | undefined>(undefined);
  readonly error = signal<unknown>(undefined);
  readonly reload = vi.fn(() => true);
  hasValue(): boolean {
    return this.value() !== undefined;
  }
}

class CatalogoFalso {
  listaFalsa = new RecursoFalso<Pagina<EventoDelCatalogoResumen>>();
  filtros: Signal<FiltrosDelCatalogo> | null = null;

  listado(filtros: Signal<FiltrosDelCatalogo>) {
    this.filtros = filtros;
    return this.listaFalsa;
  }

  urlDeImagen(idEvento: number): string {
    return `/api/catalogo/${idEvento}/imagen`;
  }
}

describe('CatalogoPage', () => {
  let harness: RouterTestingHarness;
  let catalogo: CatalogoFalso;

  // null: la lista todavía no llegó (o falló)
  async function abrir(url = '/', contenido: Pagina<EventoDelCatalogoResumen> | null = pagina([JAZZ, FERIA])) {
    TestBed.resetTestingModule();
    catalogo = new CatalogoFalso();
    catalogo.listaFalsa.value.set(contenido ?? undefined);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: '', component: CatalogoPage },
          { path: 'catalogo/:id', children: [] },
        ]),
        { provide: CatalogoService, useValue: catalogo },
      ],
    });
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, CatalogoPage);
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

  function campo(id: string): HTMLInputElement {
    return pantalla().querySelector<HTMLInputElement>('#' + id)!;
  }

  function boton(textoDelBoton: string): HTMLButtonElement {
    const encontrado = Array.from(pantalla().querySelectorAll<HTMLButtonElement>('button')).find(
      (cada) => cada.textContent?.trim() === textoDelBoton,
    );
    if (encontrado === undefined) {
      throw new Error(`No hay botón «${textoDelBoton}»`);
    }
    return encontrado;
  }

  function tarjeta(nombre: string): HTMLElement {
    const encontrada = Array.from(pantalla().querySelectorAll<HTMLElement>('.tarjeta')).find((cada) =>
      cada.textContent?.includes(nombre),
    );
    if (encontrada === undefined) {
      throw new Error(`No hay tarjeta de «${nombre}»`);
    }
    return encontrada;
  }

  async function escribir(id: string, valor: string) {
    const control = campo(id);
    control.value = valor;
    control.dispatchEvent(new Event('input'));
    control.dispatchEvent(new Event('blur'));
    await harness.fixture.whenStable();
  }

  // La navegación se encadena como promesas: se espera un macrotask
  async function asentar() {
    await new Promise((seguir) => setTimeout(seguir, 0));
    await harness.fixture.whenStable();
  }

  async function buscar() {
    pantalla().querySelector<HTMLButtonElement>('form button[type="submit"]')!.click();
    await asentar();
  }

  // CU-05 RF-6
  it('muestra las tarjetas con sus datos', async () => {
    await abrir();

    const jazz = tarjeta('Noche de jazz').textContent ?? '';
    expect(jazz).toContain('04/10/2026');
    expect(jazz).toContain('22:00');
    expect(jazz).toContain('03:00');
    expect(jazz).toContain('termina al día siguiente');
    expect(jazz).toContain('Teatro Municipal');
    expect(texto()).toContain('Página 1 de 2');
    expect(texto()).toContain('13 eventos');
  });

  it('el nombre lleva al detalle del evento', async () => {
    await abrir();

    expect(tarjeta('Noche de jazz').querySelector('h2 a')?.getAttribute('href')).toBe('/catalogo/5');
  });

  // Solo se piden las imágenes que existen: sin imagen queda el fondo neutro
  it('muestra la imagen solo si el evento la tiene', async () => {
    await abrir();

    const imagen = tarjeta('Noche de jazz').querySelector('img')!;
    expect(imagen.getAttribute('src')).toBe('/api/catalogo/5/imagen');
    expect(imagen.getAttribute('alt')).toBe('');
    expect(imagen.getAttribute('width')).not.toBeNull();
    expect(imagen.getAttribute('height')).not.toBeNull();
    expect(imagen.getAttribute('loading')).toBe('lazy');
    expect(tarjeta('Feria del libro').querySelector('img')).toBeNull();
    expect(tarjeta('Feria del libro').querySelector('.sin-imagen')).not.toBeNull();
  });

  // RF-6: la fecha original si se postergó, y la venta cerrada, dichas con texto
  it('marca el postergado con su fecha original y la venta cerrada', async () => {
    await abrir();

    expect(tarjeta('Feria del libro').textContent).toContain('Postergado · antes el 12/10/2026');
    expect(tarjeta('Feria del libro').textContent).toContain('Venta cerrada');
    expect(tarjeta('Noche de jazz').textContent).not.toContain('Postergado');
    expect(tarjeta('Noche de jazz').textContent).not.toContain('Venta cerrada');
  });

  // RF-7, RF-8 y RF-19: los filtros van a la URL y la búsqueda vuelve a la primera página
  it('buscar escribe los filtros en la URL y vuelve a la primera página', async () => {
    await abrir('/?pagina=1');

    await escribir('texto', 'jazz');
    await escribir('desde', '2026-10-01');
    await buscar();

    expect(url()).toBe('/?texto=jazz&desde=2026-10-01');
  });

  it('lee los filtros de la URL, los muestra y los aplica', async () => {
    await abrir('/?texto=jazz&desde=2026-10-01&hasta=2026-10-31&pagina=2');

    expect(campo('texto').value).toBe('jazz');
    expect(campo('desde').value).toBe('2026-10-01');
    expect(campo('hasta').value).toBe('2026-10-31');
    expect(catalogo.filtros?.()).toEqual({ texto: 'jazz', desde: '2026-10-01', hasta: '2026-10-31', pagina: 2 });
  });

  // La URL la puede escribir cualquiera: lo que no es válido no llega al backend
  it('descarta una página o una fecha que no son válidas', async () => {
    await abrir('/?pagina=abc&desde=ayer&hasta=2026-13-45x');

    expect(catalogo.filtros?.()).toEqual({ texto: null, desde: null, hasta: null, pagina: 0 });
  });

  // RF-10: se avisa sin preguntarle al backend
  it('un rango invertido se avisa bajo «hasta» sin buscar', async () => {
    await abrir();

    await escribir('desde', '2026-10-10');
    await escribir('hasta', '2026-10-01');
    await buscar();

    expect(url()).toBe('/');
    expect(texto()).toContain('La fecha hasta no puede ser anterior a la fecha desde.');
    expect(campo('hasta').getAttribute('aria-invalid')).toBe('true');
  });

  // Si igual llega un 400 (una URL armada a mano), su mensaje va al campo
  it('el rechazo del backend aparece en su campo', async () => {
    await abrir('/?desde=2026-10-10&hasta=2026-10-01', null);
    catalogo.listaFalsa.error.set(
      new HttpErrorResponse({
        status: 400,
        error: {
          status: 400,
          detail: 'Hay campos a corregir.',
          codigo: 'DATOS_INVALIDOS',
          errores: [{ campo: 'hasta', mensaje: 'no puede ser anterior a la fecha desde' }],
        },
      }),
    );
    await harness.fixture.whenStable();

    expect(pantalla().querySelector('#error-hasta')?.textContent).toContain(
      'La fecha hasta no puede ser anterior a la fecha desde.',
    );
    expect(texto()).not.toContain('Reintentar');
  });

  it('«Limpiar» quita los filtros de la URL', async () => {
    await abrir('/?texto=jazz&pagina=1');

    boton('Limpiar').click();
    await asentar();

    expect(url()).toBe('/');
  });

  // Hallazgo de la revisión de código: sin filtros en la URL, navegar a «/» no
  // cambia nada y el formulario se quedaba con lo escrito
  it('«Limpiar» borra lo escrito aunque todavía no se haya buscado', async () => {
    await abrir();
    await escribir('texto', 'rock');
    await escribir('desde', '2026-10-01');

    boton('Limpiar').click();
    await asentar();

    expect(campo('texto').value).toBe('');
    expect(campo('desde').value).toBe('');
    expect(url()).toBe('/');
  });

  // web-design-guidelines: el foco va al campo a corregir
  it('al buscar con un error, el foco va a ese campo', async () => {
    await abrir();
    await escribir('desde', '2026-10-10');
    await escribir('hasta', '2026-10-01');

    await buscar();

    expect(document.activeElement).toBe(campo('hasta'));
  });

  // web-design-guidelines: el total cambia al buscar y se anuncia
  it('el total de resultados se anuncia al cambiar', async () => {
    await abrir();

    expect(pantalla().querySelector('.paginacion p')?.getAttribute('aria-live')).toBe('polite');
  });

  it('pasa a la página siguiente conservando los filtros', async () => {
    await abrir('/?texto=jazz');

    boton('Siguiente').click();
    await asentar();

    expect(url()).toBe('/?texto=jazz&pagina=1');
  });

  // FA-01
  it('sin eventos disponibles lo dice', async () => {
    await abrir('/', VACIA);

    expect(texto()).toContain('Todavía no hay eventos disponibles.');
  });

  // FA-02: vuelve al paso 3, cambiar los criterios
  it('sin resultados para la búsqueda lo dice y ofrece limpiarla', async () => {
    await abrir('/?texto=zzz', VACIA);

    expect(texto()).toContain('No encontramos eventos con esos criterios.');
    boton('Limpiar la búsqueda').click();
    await asentar();
    expect(url()).toBe('/');
  });

  it('si la carga falla, lo explica y ofrece reintentar', async () => {
    await abrir('/', null);
    // Una caída real de la red no trae cuerpo: llega como ProgressEvent
    catalogo.listaFalsa.error.set(new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') }));
    await harness.fixture.whenStable();

    expect(texto()).toContain('No pudimos conectar con el servidor');
    boton('Reintentar').click();
    expect(catalogo.listaFalsa.reload).toHaveBeenCalled();
  });

  it('mientras carga lo dice', async () => {
    await abrir('/', null);

    expect(texto()).toContain('Cargando eventos…');
  });
});
