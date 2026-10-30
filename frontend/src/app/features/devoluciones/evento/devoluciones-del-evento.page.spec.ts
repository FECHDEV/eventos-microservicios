import { HttpErrorResponse } from '@angular/common/http';
import { Signal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { DevolucionesService } from '../../../core/api/devoluciones.service';
import { CompraParaDevolucion, DevolucionesDelEvento, RegistroDevolucion } from '../../../core/modelos/devolucion';
import { ErrorApi } from '../../../core/modelos/error-api';
import { precio } from '../../../shared/formato/precios';
import { DevolucionesDelEventoPage } from './devoluciones-del-evento.page';

// Como responde GET /api/devoluciones/5 (DevolucionesControllerTest)
const DE_ANA: CompraParaDevolucion = {
  idReserva: 40,
  cliente: 'Ana Rojas',
  correo: 'ana@mail.com',
  telefono: '7777-1234',
  tipoEntrada: 'General',
  cantidad: 2,
  monto: 100,
  precioUnitario: 50,
  fechaPago: '2026-09-22T20:00:00',
  entradas: [
    { idEntrada: 81, numero: 1 },
    { idEntrada: 82, numero: 2 },
  ],
};

function conCompras(pendientes: CompraParaDevolucion[], devueltas: CompraParaDevolucion[] = []): DevolucionesDelEvento {
  return {
    idEvento: 5,
    nombre: 'Concierto',
    fecha: '2026-10-04',
    horaInicio: '22:00:00',
    horaFin: '03:00:00',
    lugar: 'Teatro Municipal',
    pendientes,
    devueltas,
  };
}

// Lo que la pantalla usa del httpResource
class RecursoFalso<T> {
  readonly value = signal<T | undefined>(undefined);
  readonly error = signal<unknown>(undefined);
  readonly isLoading = signal(false);
  readonly reload = vi.fn(() => true);
  hasValue(): boolean {
    return this.value() !== undefined;
  }
}

class DevolucionesFalso {
  recurso = new RecursoFalso<DevolucionesDelEvento>();
  idPedido: Signal<number | null> | null = null;
  registrarCompra = vi.fn<(id: number) => Promise<RegistroDevolucion>>(() => Promise.resolve({ registradas: 2 }));
  registrarEntrada = vi.fn<(id: number) => Promise<RegistroDevolucion>>(() => Promise.resolve({ registradas: 1 }));

  delEvento(idEvento: Signal<number | null>) {
    this.idPedido = idEvento;
    return this.recurso;
  }
}

describe('DevolucionesDelEventoPage', () => {
  let harness: RouterTestingHarness;
  let servicio: DevolucionesFalso;

  // null: todavía no llegó (o falló)
  async function abrir(devoluciones: DevolucionesDelEvento | null = conCompras([DE_ANA]), url = '/devoluciones/5') {
    TestBed.resetTestingModule();
    servicio = new DevolucionesFalso();
    servicio.recurso.value.set(devoluciones ?? undefined);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'devoluciones', children: [] },
          { path: 'devoluciones/:id', component: DevolucionesDelEventoPage },
        ]),
        { provide: DevolucionesService, useValue: servicio },
      ],
    });
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, DevolucionesDelEventoPage);
    harness.fixture.autoDetectChanges();
    await harness.fixture.whenStable();
  }

  function pantalla(): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  function texto(): string {
    return pantalla().textContent ?? '';
  }

  function seccion(titulo: string): HTMLElement | undefined {
    return Array.from(pantalla().querySelectorAll<HTMLElement>('section')).find(
      (cada) => cada.querySelector('h2')?.textContent?.trim() === titulo,
    );
  }

  function boton(textoDelBoton: string, dentro: HTMLElement = pantalla()): HTMLButtonElement | undefined {
    return Array.from(dentro.querySelectorAll<HTMLButtonElement>('button')).find(
      (cada) => cada.textContent?.trim() === textoDelBoton,
    );
  }

  function dialogo(): HTMLDialogElement {
    return pantalla().querySelector('dialog')!;
  }

  // La cadena diálogo → promesa → backend termina un turno después
  async function asentar() {
    await new Promise((seguir) => setTimeout(seguir, 0));
    await harness.fixture.whenStable();
  }

  async function responderDialogo(confirmar: boolean) {
    dialogo().querySelector<HTMLButtonElement>(confirmar ? 'button.principal' : 'button.secundario')!.click();
    await asentar();
  }

  it('pide el evento del id de la URL', async () => {
    await abrir();

    expect(servicio.idPedido?.()).toBe(5);
  });

  it('muestra el evento y ofrece volver a la lista', async () => {
    await abrir();

    expect(pantalla().querySelector('h1')?.textContent?.trim()).toBe('Concierto');
    expect(texto()).toContain('04/10/2026');
    expect(texto()).toContain('22:00–03:00');
    const volver = Array.from(pantalla().querySelectorAll('a')).find(
      (enlace) => enlace.textContent?.trim() === 'Volver a devoluciones',
    );
    expect(volver?.getAttribute('href')).toBe('/devoluciones');
  });

  // RF-4
  it('una compra pendiente muestra al Cliente, la compra y sus entradas', async () => {
    await abrir();

    const pendientes = seccion('Pendientes de devolución')!;
    expect(pendientes.textContent).toContain('Ana Rojas');
    expect(pendientes.querySelector('a[href="mailto:ana@mail.com"]')?.textContent?.trim()).toBe('ana@mail.com');
    expect(pendientes.querySelector('a[href="tel:7777-1234"]')?.textContent?.trim()).toBe('7777-1234');
    expect(pendientes.textContent).toContain('General × 2');
    expect(pendientes.textContent).toContain(precio(100));
    // Lo que se devuelve por cada entrada
    expect(pendientes.textContent).toContain(`${precio(50)} por entrada`);
    expect(pendientes.textContent).toContain('22/09/2026');
    expect(pendientes.textContent).toContain('Entrada 1');
    expect(pendientes.textContent).toContain('Entrada 2');
    expect(boton('Registrar devolución de la compra (2)', pendientes)?.getAttribute('aria-label')).toBe(
      'Registrar devolución de la compra de Ana Rojas (2 entradas)',
    );
  });

  it('sin teléfono no muestra esa línea', async () => {
    await abrir(conCompras([{ ...DE_ANA, telefono: null }]));

    expect(pantalla().querySelector('.telefono')).toBeNull();
  });

  // RF-9 y RF-14
  it('registrar la compra pide confirmación y desistir no registra nada', async () => {
    await abrir();

    boton('Registrar devolución de la compra (2)')!.click();
    await asentar();
    expect(dialogo().textContent).toContain('Ana Rojas');
    expect(dialogo().textContent).toContain('No se deshace');
    await responderDialogo(false);

    expect(servicio.registrarCompra).not.toHaveBeenCalled();
  });

  it('al confirmar registra la compra, lo dice y recarga', async () => {
    await abrir();

    boton('Registrar devolución de la compra (2)')!.click();
    await asentar();
    await responderDialogo(true);

    expect(servicio.registrarCompra).toHaveBeenCalledWith(40);
    expect(pantalla().querySelector('[role="status"]')?.textContent).toContain(
      'Se registró la devolución de 2 entradas.',
    );
    expect(servicio.recurso.reload).toHaveBeenCalled();
    // La compra deja de estar pendiente al recargar: el foco no se pierde con su botón
    expect(document.activeElement?.id).toBe('titulo-pendientes');
  });

  // RF-10
  it('registrar una entrada la registra sola y lo dice en singular', async () => {
    await abrir();

    boton('Registrar devolución', pantalla().querySelector<HTMLElement>('.entradas li')!)!.click();
    await asentar();
    expect(dialogo().textContent).toContain('entrada 1');
    await responderDialogo(true);

    expect(servicio.registrarEntrada).toHaveBeenCalledWith(81);
    expect(texto()).toContain('Se registró la devolución de 1 entrada.');
  });

  // FA-02: otro Administrador se adelantó
  it('si ya estaba registrada lo dice y recarga', async () => {
    await abrir();
    const yaDevuelta: ErrorApi = { status: 409, detail: 'detalle', codigo: 'ENTRADA_YA_DEVUELTA' };
    servicio.registrarCompra.mockRejectedValue(yaDevuelta);

    boton('Registrar devolución de la compra (2)')!.click();
    await asentar();
    await responderDialogo(true);

    expect(pantalla().querySelector('[role="alert"]')?.textContent).toContain('Esa devolución ya estaba registrada.');
    expect(servicio.recurso.reload).toHaveBeenCalled();
    // La compra puede desaparecer al recargar: el foco tampoco se pierde en el error
    expect(document.activeElement?.id).toBe('titulo-pendientes');
  });

  // Mientras recarga, los botones se ven sobre datos viejos: no se puede registrar sobre ellos
  it('mientras recarga no deja registrar', async () => {
    await abrir();
    servicio.recurso.isLoading.set(true);
    await harness.fixture.whenStable();

    expect(boton('Registrar devolución de la compra (2)')?.disabled).toBe(true);
    expect(boton('Registrar devolución', pantalla().querySelector<HTMLElement>('.entradas li')!)?.disabled).toBe(true);
  });

  it('doble clic en registrar abre un solo diálogo y registra una vez', async () => {
    await abrir();
    let terminar!: (registro: RegistroDevolucion) => void;
    servicio.registrarCompra.mockReturnValue(new Promise((resolver) => (terminar = resolver)));

    boton('Registrar devolución de la compra (2)')!.click();
    await asentar();
    await responderDialogo(true);
    boton('Registrar devolución de la compra (2)')!.click();
    await asentar();
    terminar({ registradas: 2 });
    await asentar();

    expect(servicio.registrarCompra).toHaveBeenCalledTimes(1);
  });

  // FA-01 y RF-5
  it('sin pendientes lo dice, y las devueltas se ven aparte y sin botones', async () => {
    await abrir(conCompras([], [DE_ANA]));

    expect(texto()).toContain('No hay entradas pendientes de devolución en este evento.');
    const devueltas = seccion('Devueltas')!;
    expect(devueltas.textContent).toContain('Ana Rojas');
    expect(devueltas.querySelector('button')).toBeNull();
  });

  it('sin devueltas no muestra esa sección', async () => {
    await abrir();

    expect(seccion('Devueltas')).toBeUndefined();
  });

  // Basura en la URL no llega al backend
  it('un id que no es un número no se pide', async () => {
    await abrir(null, '/devoluciones/abc');

    expect(servicio.idPedido?.()).toBeNull();
    expect(texto()).toContain('No encontramos ese evento.');
  });

  it('un 404 dice que no lo encontró', async () => {
    await abrir(null, '/devoluciones/99');
    servicio.recurso.error.set(
      new HttpErrorResponse({
        status: 404,
        error: { status: 404, detail: 'No existe el evento indicado.', codigo: 'EVENTO_NO_ENCONTRADO' },
      }),
    );
    await harness.fixture.whenStable();

    expect(texto()).toContain('No encontramos ese evento.');
  });

  // RF-8
  it('un evento no cancelado lo explica', async () => {
    await abrir(null);
    servicio.recurso.error.set(
      new HttpErrorResponse({
        status: 409,
        error: { status: 409, detail: 'El evento no está cancelado.', codigo: 'EVENTO_NO_CANCELADO' },
      }),
    );
    await harness.fixture.whenStable();

    expect(pantalla().querySelector('[role="alert"]')?.textContent).toContain('Ese evento no está cancelado.');
    // Reintentar no cambiaría nada: ofrece volver a la lista
    expect(boton('Reintentar')).toBeUndefined();
    const volver = Array.from(pantalla().querySelectorAll('a')).find(
      (enlace) => enlace.textContent?.trim() === 'Volver a devoluciones',
    );
    expect(volver?.getAttribute('href')).toBe('/devoluciones');
  });

  it('si la carga falla por otro motivo, ofrece reintentar', async () => {
    await abrir(null);
    servicio.recurso.error.set(new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') }));
    await harness.fixture.whenStable();

    expect(texto()).toContain('No pudimos conectar con el servidor');
    boton('Reintentar')!.click();
    expect(servicio.recurso.reload).toHaveBeenCalled();
  });
});
