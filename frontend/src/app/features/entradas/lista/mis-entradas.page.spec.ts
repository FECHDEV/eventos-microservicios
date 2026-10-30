import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { EntradasService } from '../../../core/api/entradas.service';
import { CompraDelCliente, EntradasDelCliente } from '../../../core/modelos/entrada';
import { MisEntradasPage } from './mis-entradas.page';

// Como responde GET /api/entradas (EntradasControllerTest)
const JAZZ: CompraDelCliente = {
  idReserva: 40,
  idEvento: 5,
  nombreEvento: 'Noche de jazz',
  fecha: '2026-10-04',
  horaInicio: '22:00:00',
  horaFin: '03:00:00',
  lugar: 'Teatro Municipal',
  estadoEvento: 'ACTIVO',
  tipoEntrada: 'General',
  cantidad: 2,
  entradas: [
    { idEntrada: 81, numero: 1, estado: 'ACTIVA' },
    { idEntrada: 82, numero: 2, estado: 'UTILIZADA' },
  ],
};

const FERIA: CompraDelCliente = {
  idReserva: 31,
  idEvento: 3,
  nombreEvento: 'Feria del libro',
  fecha: '2026-08-15',
  horaInicio: '10:00:00',
  horaFin: '18:00:00',
  lugar: 'Campo ferial',
  estadoEvento: 'ACTIVO',
  tipoEntrada: 'VIP',
  cantidad: 1,
  entradas: [{ idEntrada: 60, numero: 1, estado: 'DEVUELTA' }],
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

class EntradasFalso {
  recurso = new RecursoFalso<EntradasDelCliente>();

  entradas() {
    return this.recurso;
  }
}

describe('MisEntradasPage', () => {
  let harness: RouterTestingHarness;
  let servicio: EntradasFalso;

  // null: todavía no llegaron (o fallaron)
  async function abrir(entradas: EntradasDelCliente | null) {
    TestBed.resetTestingModule();
    servicio = new EntradasFalso();
    servicio.recurso.value.set(entradas ?? undefined);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: '', children: [] },
          { path: 'mis-entradas', component: MisEntradasPage },
          { path: 'mis-entradas/:id', children: [] },
        ]),
        { provide: EntradasService, useValue: servicio },
      ],
    });
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/mis-entradas', MisEntradasPage);
    harness.fixture.autoDetectChanges();
    await harness.fixture.whenStable();
  }

  function pantalla(): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  function texto(): string {
    return pantalla().textContent ?? '';
  }

  function titulos(nivel: string): string[] {
    return Array.from(pantalla().querySelectorAll(nivel)).map((titulo) => titulo.textContent?.trim() ?? '');
  }

  function tarjeta(nombre: string): HTMLElement {
    const encontrada = Array.from(pantalla().querySelectorAll<HTMLElement>('article')).find(
      (cada) => cada.querySelector('h3')?.textContent?.trim() === nombre,
    );
    if (encontrada === undefined) {
      throw new Error(`No hay tarjeta de «${nombre}»`);
    }
    return encontrada;
  }

  function seccion(titulo: string): HTMLElement | undefined {
    return Array.from(pantalla().querySelectorAll<HTMLElement>('section')).find(
      (cada) => cada.querySelector('h2')?.textContent?.trim() === titulo,
    );
  }

  it('se titula «Mis entradas»', async () => {
    await abrir({ proximas: [JAZZ], pasadas: [], canceladas: [] });

    expect(titulos('h1')).toEqual(['Mis entradas']);
  });

  // RF-5: próximos primero, pasados después, cada compra en su sección
  it('separa los próximos de los pasados', async () => {
    await abrir({ proximas: [JAZZ], pasadas: [FERIA], canceladas: [] });

    expect(titulos('h2')).toEqual(['Próximos', 'Pasados']);
    expect(seccion('Próximos')?.textContent).toContain('Noche de jazz');
    expect(seccion('Próximos')?.textContent).not.toContain('Feria del libro');
    expect(seccion('Pasados')?.textContent).toContain('Feria del libro');
  });

  // Pedido en la prueba manual: una compra pasada se distingue de una próxima sin leer el título de la sección
  it('una compra pasada lleva «Terminado» y se ve apagada; una próxima no', async () => {
    await abrir({ proximas: [JAZZ], pasadas: [FERIA], canceladas: [] });

    expect(tarjeta('Feria del libro').querySelector('.terminado')?.textContent?.trim()).toBe('Terminado');
    expect(tarjeta('Feria del libro').classList).toContain('pasada');
    expect(tarjeta('Noche de jazz').querySelector('.terminado')).toBeNull();
    expect(tarjeta('Noche de jazz').classList).not.toContain('pasada');
  });

  // Pedido en la prueba manual: los cancelados al final, en su sección, con contorno rojo
  it('los cancelados van al final, en su sección y marcados', async () => {
    const concierto = { ...JAZZ, idReserva: 42, nombreEvento: 'Concierto', estadoEvento: 'CANCELADO' as const };
    await abrir({ proximas: [JAZZ], pasadas: [FERIA], canceladas: [concierto] });

    expect(titulos('h2')).toEqual(['Próximos', 'Pasados', 'Cancelados']);
    expect(seccion('Cancelados')?.textContent).toContain('Concierto');
    expect(tarjeta('Concierto').classList).toContain('cancelada');
    expect(tarjeta('Concierto').querySelector('.etiqueta')?.textContent?.trim()).toBe('Cancelado');
    expect(tarjeta('Concierto').querySelector('.terminado')).toBeNull();
    expect(tarjeta('Noche de jazz').classList).not.toContain('cancelada');
  });

  it('con solo cancelados no hay otras secciones ni el mensaje de vacío', async () => {
    const concierto = { ...JAZZ, estadoEvento: 'CANCELADO' as const };
    await abrir({ proximas: [], pasadas: [], canceladas: [concierto] });

    expect(titulos('h2')).toEqual(['Cancelados']);
    expect(texto()).not.toContain('No tienes entradas para mostrar.');
  });

  it('una sección sin compras no aparece', async () => {
    await abrir({ proximas: [], pasadas: [FERIA], canceladas: [] });

    expect(titulos('h2')).toEqual(['Pasados']);
  });

  // La pantalla no reordena: el orden lo decide el backend
  it('muestra las compras en el orden en que llegan', async () => {
    const segunda = { ...JAZZ, idReserva: 41, nombreEvento: 'Obra de teatro' };
    await abrir({ proximas: [segunda, JAZZ], pasadas: [], canceladas: [] });

    expect(titulos('h3')).toEqual(['Obra de teatro', 'Noche de jazz']);
  });

  // RF-3
  it('la tarjeta muestra el evento, el tipo, la cantidad y las entradas con su estado', async () => {
    await abrir({ proximas: [JAZZ], pasadas: [], canceladas: [] });

    const jazz = tarjeta('Noche de jazz');
    expect(jazz.textContent).toContain('04/10/2026');
    expect(jazz.textContent).toContain('22:00–03:00');
    expect(jazz.textContent).toContain('termina al día siguiente');
    expect(jazz.textContent).toContain('Teatro Municipal');
    expect(jazz.textContent).toContain('General × 2');
    const filas = Array.from(jazz.querySelectorAll('li')).map((fila) => [
      fila.querySelector('a')?.textContent?.trim(),
      fila.querySelector('.estado')?.textContent?.trim(),
    ]);
    expect(filas).toEqual([
      ['Entrada 1', 'Activa'],
      ['Entrada 2', 'Utilizada'],
    ]);
  });

  // RF-8: cada entrada lleva a su detalle
  it('cada entrada es un enlace a su detalle', async () => {
    await abrir({ proximas: [JAZZ], pasadas: [], canceladas: [] });

    const enlaces = Array.from(tarjeta('Noche de jazz').querySelectorAll('a'));
    expect(enlaces.map((enlace) => enlace.getAttribute('href'))).toEqual(['/mis-entradas/81', '/mis-entradas/82']);
    expect(enlaces[0].getAttribute('aria-label')).toBe('Entrada 1 de Noche de jazz');
  });

  it('un evento que termina el mismo día no lo aclara', async () => {
    await abrir({ proximas: [], pasadas: [FERIA], canceladas: [] });

    expect(tarjeta('Feria del libro').textContent).not.toContain('termina al día siguiente');
  });

  // RF-6: la etiqueta es un dato, sin frases sobre la devolución
  it('un evento postergado o cancelado lleva su etiqueta, y uno activo ninguna', async () => {
    await abrir({
      proximas: [
        JAZZ,
        { ...JAZZ, idReserva: 41, nombreEvento: 'Obra de teatro', estadoEvento: 'POSTERGADO' },
        { ...JAZZ, idReserva: 42, nombreEvento: 'Concierto', estadoEvento: 'CANCELADO' },
      ],
      pasadas: [],
      canceladas: [],
    });

    expect(tarjeta('Noche de jazz').querySelector('.etiqueta')).toBeNull();
    expect(tarjeta('Obra de teatro').querySelector('.etiqueta')?.textContent?.trim()).toBe('Postergado');
    expect(tarjeta('Concierto').querySelector('.etiqueta')?.textContent?.trim()).toBe('Cancelado');
    expect(texto()).not.toMatch(/devoluci|reembols/i);
  });

  // RF-10: el listado no muestra códigos ni QR
  it('no muestra ningún código ni QR', async () => {
    await abrir({ proximas: [JAZZ], pasadas: [FERIA], canceladas: [] });

    expect(pantalla().querySelector('svg[role="img"]')).toBeNull();
    expect(pantalla().querySelector('app-codigo-qr')).toBeNull();
  });

  // FA-01
  it('sin compras dice que no hay entradas y ofrece el catálogo', async () => {
    await abrir({ proximas: [], pasadas: [], canceladas: [] });

    expect(texto()).toContain('No tienes entradas para mostrar.');
    const catalogo = Array.from(pantalla().querySelectorAll('a')).find(
      (enlace) => enlace.textContent?.trim() === 'Ver el catálogo',
    );
    expect(catalogo?.getAttribute('href')).toBe('/');
    expect(titulos('h2')).toEqual([]);
  });

  it('mientras carga lo dice', async () => {
    await abrir(null);

    expect(pantalla().querySelector('[role="status"]')?.textContent).toContain('Cargando tus entradas…');
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
