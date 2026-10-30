import { HttpErrorResponse } from '@angular/common/http';
import { Signal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { CatalogoService } from '../../../core/api/catalogo.service';
import { ReservasService } from '../../../core/api/reservas.service';
import { EventoDelCatalogo } from '../../../core/modelos/catalogo';
import { ErrorApi } from '../../../core/modelos/error-api';
import { Reserva } from '../../../core/modelos/reserva';
import { precio } from '../../../shared/formato/precios';
import { SeleccionDeEntradasPage } from './seleccion-de-entradas.page';

// Como responde GET /api/catalogo/5 (CatalogoControllerTest)
const JAZZ: EventoDelCatalogo = {
  idEvento: 5,
  nombre: 'Noche de jazz',
  descripcion: 'Cuarteto de jazz.',
  fecha: '2026-10-04',
  fechaOriginal: null,
  horaInicio: '22:00:00',
  horaFin: '03:00:00',
  lugar: 'Teatro Municipal',
  restriccionEdad: 18,
  limiteCompra: 4,
  ventaAbierta: true,
  tieneImagen: false,
  tiposEntrada: [
    { idTipoEntrada: 1, nombre: 'General', precio: 50, disponible: true },
    { idTipoEntrada: 2, nombre: 'VIP', precio: 150, disponible: false },
  ],
};

// Como responde POST /api/reservas (ReservasControllerTest)
const RESERVA: Reserva = {
  idReserva: 40,
  idEvento: 5,
  nombreEvento: 'Noche de jazz',
  fecha: '2026-10-04',
  horaInicio: '22:00:00',
  horaFin: '03:00:00',
  lugar: 'Teatro Municipal',
  idTipoEntrada: 1,
  tipoEntrada: 'General',
  cantidad: 3,
  precioUnitario: 50,
  total: 150,
  fechaExpiracion: '2026-09-21T20:10:00',
  segundosRestantes: 600,
  estado: 'VIGENTE',
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

class CatalogoFalso {
  eventoFalso = new RecursoFalso<EventoDelCatalogo>();
  idPedido: Signal<number | null> | null = null;

  evento(idEvento: Signal<number | null>) {
    this.idPedido = idEvento;
    return this.eventoFalso;
  }
}

function rechazo(codigo: string, extra: Partial<ErrorApi> = {}): ErrorApi {
  return { status: 409, detail: 'detalle técnico', codigo, ...extra };
}

describe('SeleccionDeEntradasPage', () => {
  let harness: RouterTestingHarness;
  let catalogo: CatalogoFalso;
  let reservar: ReturnType<typeof vi.fn>;

  // null: el evento todavía no llegó (o falló)
  async function abrir(url = '/catalogo/5/entradas/1', evento: EventoDelCatalogo | null = JAZZ) {
    TestBed.resetTestingModule();
    catalogo = new CatalogoFalso();
    catalogo.eventoFalso.value.set(evento ?? undefined);
    reservar = vi.fn(async () => RESERVA);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'catalogo/:id', children: [] },
          { path: 'catalogo/:id/entradas/:idTipo', component: SeleccionDeEntradasPage },
          { path: 'reservas/:id', children: [] },
          { path: '', children: [] },
        ]),
        { provide: CatalogoService, useValue: catalogo },
        { provide: ReservasService, useValue: { reservar } },
      ],
    });
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, SeleccionDeEntradasPage);
    harness.fixture.autoDetectChanges();
    await harness.fixture.whenStable();
  }

  function pantalla(): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  function texto(): string {
    return pantalla().textContent ?? '';
  }

  function cantidad(): HTMLInputElement {
    return pantalla().querySelector<HTMLInputElement>('#cantidad')!;
  }

  async function escribir(valor: string) {
    cantidad().value = valor;
    cantidad().dispatchEvent(new Event('input'));
    cantidad().dispatchEvent(new Event('blur'));
    await harness.fixture.whenStable();
  }

  async function asentar() {
    await new Promise((seguir) => setTimeout(seguir, 0));
    await harness.fixture.whenStable();
  }

  async function enviar() {
    pantalla().querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    await asentar();
  }

  function aviso(): string {
    return pantalla().querySelector('[role="alert"]')?.textContent?.trim() ?? '';
  }

  function errorDeLaCantidad(): string {
    return pantalla().querySelector('#error-cantidad')?.textContent?.trim() ?? '';
  }

  function enlace(textoDelEnlace: string): HTMLAnchorElement | undefined {
    return Array.from(pantalla().querySelectorAll<HTMLAnchorElement>('a')).find(
      (cada) => cada.textContent?.trim() === textoDelEnlace,
    );
  }

  // CU-06 paso 1 y RF-25
  it('muestra el evento, el tipo elegido con su precio, la edad mínima y el límite', async () => {
    await abrir();

    expect(catalogo.idPedido?.()).toBe(5);
    expect(texto()).toContain('Noche de jazz');
    expect(texto()).toContain('04/10/2026');
    expect(texto()).toContain('Teatro Municipal');
    expect(texto()).toContain('General');
    expect(texto()).toContain(precio(50));
    expect(texto()).toContain('Desde 18 años');
    expect(texto()).toContain('Hasta 4 entradas por cliente');
    expect(enlace('← Volver al evento')?.getAttribute('href')).toBe('/catalogo/5');
  });

  it('sin restricción ni límite no los menciona', async () => {
    await abrir('/catalogo/5/entradas/1', { ...JAZZ, restriccionEdad: null, limiteCompra: null });

    expect(texto()).not.toContain('Desde');
    expect(texto()).not.toContain('por cliente');
  });

  // RF-4 en la pantalla: se avisa antes de enviar, y el foco va al campo
  it.each(['', '0', '1,5', 'abc'])('la cantidad «%s» no se envía', async (valor) => {
    await abrir();

    await escribir(valor);
    await enviar();

    expect(errorDeLaCantidad()).not.toBe('');
    expect(cantidad().getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(cantidad());
    expect(reservar).not.toHaveBeenCalled();
  });

  it('con límite, una cantidad mayor se avisa sin enviar', async () => {
    await abrir();

    await escribir('5');
    await enviar();

    expect(errorDeLaCantidad()).toBe('Puedes comprar hasta 4 entradas en este evento.');
    expect(reservar).not.toHaveBeenCalled();
  });

  it('el total sigue a la cantidad y no aparece con una inválida', async () => {
    await abrir();

    await escribir('3');
    expect(pantalla().querySelector('.total')?.textContent).toContain(precio(150));

    await escribir('abc');
    expect(pantalla().querySelector('.total')?.textContent?.trim()).toBe('');
  });

  // Paso 5: reserva y muestra la reserva
  it('reserva el tipo con la cantidad y lleva a la reserva', async () => {
    await abrir();

    await escribir('3');
    await enviar();

    expect(reservar).toHaveBeenCalledWith({ idTipoEntrada: 1, cantidad: 3 });
    expect(TestBed.inject(Router).url).toBe('/reservas/40');
  });

  // RF-26: el motivo junto al formulario, sin perder la cantidad
  it('un límite excedido dice cuántas puede agregar y conserva la cantidad', async () => {
    await abrir();
    reservar.mockRejectedValue(rechazo('LIMITE_COMPRA_EXCEDIDO', { limite: 4, puedeAgregar: 1 }));

    await escribir('3');
    await enviar();

    expect(aviso()).toContain('superas el límite');
    expect(aviso()).toContain('Puedes agregar 1 más.');
    expect(cantidad().value).toBe('3');
    expect(TestBed.inject(Router).url).toBe('/catalogo/5/entradas/1');
  });

  it.each([
    [rechazo('EDAD_NO_PERMITIDA', { edadMinima: 18 }), 'Es para mayores de 18 años.'],
    [rechazo('ENTRADAS_NO_DISPONIBLES'), 'No quedan suficientes entradas de este tipo para esa cantidad. Prueba con una cantidad menor.'],
    [rechazo('VENTA_CERRADA'), 'La venta de este evento está cerrada.'],
    [{ status: 0, detail: 'sin red', codigo: 'SIN_CONEXION' }, 'No pudimos conectar con el servidor. Intenta de nuevo.'],
  ])('muestra el rechazo %#', async (problema, frase) => {
    await abrir();
    reservar.mockRejectedValue(problema);

    await escribir('2');
    await enviar();

    expect(aviso()).toContain(frase);
  });

  // El aviso es de lo que se envió: al cambiar la cantidad deja de mostrarse
  it('al cambiar la cantidad el aviso se va', async () => {
    await abrir();
    reservar.mockRejectedValue(rechazo('ENTRADAS_NO_DISPONIBLES'));
    await escribir('3');
    await enviar();

    await escribir('2');

    expect(aviso()).toBe('');
  });

  it('un 400 del backend se muestra bajo la cantidad', async () => {
    await abrir();
    reservar.mockRejectedValue({
      status: 400,
      detail: 'Hay campos a corregir.',
      codigo: 'DATOS_INVALIDOS',
      errores: [{ campo: 'cantidad', mensaje: 'debe ser de al menos 1' }],
    });

    await escribir('2');
    await enviar();

    expect(errorDeLaCantidad()).toBe('La cantidad debe ser de al menos 1.');
    expect(document.activeElement).toBe(cantidad());
  });

  it('un doble clic envía una sola vez', async () => {
    await abrir();
    let terminar: (reserva: Reserva) => void = () => undefined;
    reservar.mockReturnValue(new Promise<Reserva>((resolver) => (terminar = resolver)));
    await escribir('2');
    const boton = pantalla().querySelector<HTMLButtonElement>('button[type="submit"]')!;

    boton.click();
    await harness.fixture.whenStable();
    boton.click();
    await harness.fixture.whenStable();

    expect(reservar).toHaveBeenCalledTimes(1);
    expect(boton.textContent?.trim()).toBe('Reservando…');
    terminar(RESERVA);
    await asentar();
  });

  // Lo que no se puede comprar no ofrece formulario
  it.each([
    ['un tipo agotado', '/catalogo/5/entradas/2', JAZZ],
    ['la venta cerrada', '/catalogo/5/entradas/1', { ...JAZZ, ventaAbierta: false }],
    ['un tipo que no es del evento', '/catalogo/5/entradas/9', JAZZ],
  ])('con %s no hay formulario', async (_caso, url, evento) => {
    await abrir(url, evento);

    expect(cantidad()).toBeNull();
    expect(texto()).toContain('Este tipo de entrada no está a la venta.');
    expect(enlace('Volver al evento')?.getAttribute('href')).toBe('/catalogo/5');
  });

  it('basura en la URL no pide nada', async () => {
    await abrir('/catalogo/abc/entradas/1', null);

    expect(catalogo.idPedido?.()).toBeNull();
    expect(texto()).toContain('Este tipo de entrada no está a la venta.');
  });

  it('un evento que no se muestra (404) tampoco está a la venta', async () => {
    await abrir('/catalogo/5/entradas/1', null);
    catalogo.eventoFalso.error.set(new HttpErrorResponse({ status: 404, error: { status: 404, detail: 'x' } }));
    await harness.fixture.whenStable();

    expect(texto()).toContain('Este tipo de entrada no está a la venta.');
  });

  it('si la carga falla por otro motivo, ofrece reintentar', async () => {
    await abrir('/catalogo/5/entradas/1', null);
    catalogo.eventoFalso.error.set(new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') }));
    await harness.fixture.whenStable();

    expect(texto()).toContain('No pudimos conectar con el servidor');
    Array.from(pantalla().querySelectorAll('button'))
      .find((boton) => boton.textContent?.trim() === 'Reintentar')!
      .click();
    expect(catalogo.eventoFalso.reload).toHaveBeenCalled();
  });

  it('mientras carga lo dice', async () => {
    await abrir('/catalogo/5/entradas/1', null);

    expect(texto()).toContain('Cargando…');
  });
});
