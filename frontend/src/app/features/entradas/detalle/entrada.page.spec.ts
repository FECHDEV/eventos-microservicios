import { HttpErrorResponse } from '@angular/common/http';
import { Signal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { EntradasService } from '../../../core/api/entradas.service';
import { Entrada } from '../../../core/modelos/entrada';
import { CodigoQr } from '../../../shared/qr/codigo-qr';
import { EntradaPage } from './entrada.page';

// Como responde GET /api/entradas/81 (EntradasControllerTest)
const ACTIVA: Entrada = {
  idEntrada: 81,
  numero: 1,
  cantidad: 2,
  estado: 'ACTIVA',
  fechaEmision: '2026-09-22T20:00:00',
  codigoQr: 'codigo-81',
  idEvento: 5,
  nombreEvento: 'Noche de jazz',
  fecha: '2026-10-04',
  horaInicio: '22:00:00',
  horaFin: '03:00:00',
  lugar: 'Teatro Municipal',
  estadoEvento: 'ACTIVO',
  tipoEntrada: 'General',
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
  recurso = new RecursoFalso<Entrada>();
  idPedido: Signal<number | null> | null = null;

  entrada(idEntrada: Signal<number | null>) {
    this.idPedido = idEntrada;
    return this.recurso;
  }
}

describe('EntradaPage', () => {
  let harness: RouterTestingHarness;
  let servicio: EntradasFalso;

  // null: todavía no llegó (o falló)
  async function abrir(entrada: Entrada | null = ACTIVA, url = '/mis-entradas/81') {
    TestBed.resetTestingModule();
    servicio = new EntradasFalso();
    servicio.recurso.value.set(entrada ?? undefined);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'mis-entradas', children: [] },
          { path: 'mis-entradas/:id', component: EntradaPage },
        ]),
        { provide: EntradasService, useValue: servicio },
      ],
    });
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, EntradaPage);
    harness.fixture.autoDetectChanges();
    await harness.fixture.whenStable();
  }

  function pantalla(): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  // Lo que CodigoQr dibuja para un texto: el QR de la pantalla tiene que ser el de ese texto
  async function trazoDe(valor: string): Promise<string | null | undefined> {
    const fijo = TestBed.createComponent(CodigoQr);
    fijo.componentRef.setInput('valor', valor);
    fijo.componentRef.setInput('etiqueta', 'referencia');
    fijo.detectChanges();
    return fijo.nativeElement.querySelector('path')?.getAttribute('d');
  }

  function texto(): string {
    return pantalla().textContent ?? '';
  }

  function qr(): SVGSVGElement | null {
    return pantalla().querySelector('svg[role="img"]');
  }

  function enlace(textoDelEnlace: string): HTMLAnchorElement | undefined {
    return Array.from(pantalla().querySelectorAll<HTMLAnchorElement>('a')).find(
      (cada) => cada.textContent?.trim() === textoDelEnlace,
    );
  }

  it('pide la entrada del id de la URL', async () => {
    await abrir();

    expect(servicio.idPedido?.()).toBe(81);
  });

  // RF-8
  it('muestra el número, el evento y los datos de la entrada', async () => {
    await abrir();

    expect(pantalla().querySelector('h1')?.textContent?.trim()).toBe('Entrada 1 de 2');
    expect(texto()).toContain('Noche de jazz');
    expect(texto()).toContain('04/10/2026');
    expect(texto()).toContain('22:00–03:00');
    expect(texto()).toContain('termina al día siguiente');
    expect(texto()).toContain('Teatro Municipal');
    expect(texto()).toContain('General');
    expect(texto()).toContain('Activa');
    expect(texto()).toContain('22/09/2026 20:00');
    expect(pantalla().querySelector('.etiqueta')).toBeNull();
  });

  // RF-9 y RF-13: el QR de una activa, con nombre accesible
  it('una entrada activa muestra su QR', async () => {
    await abrir();

    expect(qr()?.getAttribute('aria-label')).toBe('Código QR de la entrada 1 de Noche de jazz');
    expect(qr()?.querySelector('path')?.getAttribute('d')).toBe(await trazoDe('codigo-81'));
    expect(texto()).toContain('Muéstralo en la entrada del evento.');
  });

  // El código viaja solo dentro de la imagen
  it('el texto del código no aparece en la pantalla', async () => {
    await abrir();

    expect(texto()).not.toContain('codigo-81');
  });

  it('una utilizada no muestra el QR y dice por qué', async () => {
    await abrir({ ...ACTIVA, estado: 'UTILIZADA', codigoQr: null });

    expect(qr()).toBeNull();
    expect(texto()).toContain('Esta entrada ya se usó.');
    expect(texto()).not.toContain('Muéstralo');
  });

  it('una devuelta no muestra el QR y dice por qué', async () => {
    await abrir({ ...ACTIVA, estado: 'DEVUELTA', codigoQr: null });

    expect(qr()).toBeNull();
    expect(texto()).toContain('Esta entrada fue devuelta.');
  });

  // RF-6 y RF-9
  it('la de un evento cancelado lleva la etiqueta y no muestra el QR', async () => {
    await abrir({ ...ACTIVA, estadoEvento: 'CANCELADO', codigoQr: null });

    expect(pantalla().querySelector('.etiqueta')?.textContent?.trim()).toBe('Cancelado');
    expect(qr()).toBeNull();
    expect(texto()).toContain('El evento se canceló.');
    expect(texto()).not.toMatch(/devoluci|reembols/i);
  });

  // CLAUDE.md → Postergación: las entradas conservan su validez con la fecha nueva
  it('la de un evento postergado lleva la etiqueta, la fecha nueva y el QR', async () => {
    await abrir({ ...ACTIVA, estadoEvento: 'POSTERGADO', fecha: '2026-11-08' });

    expect(pantalla().querySelector('.etiqueta')?.textContent?.trim()).toBe('Postergado');
    expect(texto()).toContain('08/11/2026');
    expect(qr()).not.toBeNull();
  });

  it('ofrece volver a mis entradas', async () => {
    await abrir();

    expect(enlace('Volver a mis entradas')?.getAttribute('href')).toBe('/mis-entradas');
  });

  // Basura en la URL no llega al backend
  it('un id que no es un número no se pide y dice que no la encontró', async () => {
    await abrir(null, '/mis-entradas/abc');

    expect(servicio.idPedido?.()).toBeNull();
    expect(texto()).toContain('No encontramos esa entrada.');
    expect(enlace('Volver a mis entradas')?.getAttribute('href')).toBe('/mis-entradas');
  });

  // RF-2: la de otro Cliente responde 404 igual que una inexistente
  it('un 404 dice que no la encontró', async () => {
    await abrir(null, '/mis-entradas/99');
    servicio.recurso.error.set(
      new HttpErrorResponse({
        status: 404,
        error: { status: 404, detail: 'No existe la entrada indicada.', codigo: 'ENTRADA_NO_ENCONTRADA' },
      }),
    );
    await harness.fixture.whenStable();

    expect(texto()).toContain('No encontramos esa entrada.');
    expect(texto()).not.toContain('Reintentar');
  });

  it('si la carga falla por otro motivo, ofrece reintentar', async () => {
    await abrir(null);
    servicio.recurso.error.set(new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') }));
    await harness.fixture.whenStable();

    expect(pantalla().querySelector('[role="alert"]')?.textContent).toContain('No pudimos conectar con el servidor');
    Array.from(pantalla().querySelectorAll('button'))
      .find((boton) => boton.textContent?.trim() === 'Reintentar')!
      .click();
    expect(servicio.recurso.reload).toHaveBeenCalled();
  });

  it('mientras carga lo dice', async () => {
    await abrir(null);

    expect(pantalla().querySelector('[role="status"]')?.textContent).toContain('Cargando la entrada…');
  });

  it('al cambiar el id de la URL pide la otra entrada', async () => {
    await abrir();

    await harness.navigateByUrl('/mis-entradas/82', EntradaPage);

    expect(servicio.idPedido?.()).toBe(82);
  });
});
