import { HttpErrorResponse } from '@angular/common/http';
import { Signal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { ReservasService } from '../../../core/api/reservas.service';
import { ErrorApi } from '../../../core/modelos/error-api';
import { Pago } from '../../../core/modelos/pago';
import { Reserva } from '../../../core/modelos/reserva';
import { precio } from '../../../shared/formato/precios';
import { CodigoQr } from '../../../shared/qr/codigo-qr';
import { ReservaPage } from './reserva.page';

// Como responde GET /api/reservas/40 (ReservasControllerTest)
const RESERVA: Reserva = {
  idReserva: 40,
  idEvento: 5,
  nombreEvento: 'Noche de jazz',
  fecha: '2026-10-04',
  horaInicio: '22:00:00',
  horaFin: '03:00:00',
  lugar: 'Teatro Municipal',
  idTipoEntrada: 3,
  tipoEntrada: 'General',
  cantidad: 3,
  precioUnitario: 50,
  total: 150,
  fechaExpiracion: '2026-09-21T20:09:41',
  segundosRestantes: 581,
  estado: 'VIGENTE',
};

// Como responde POST /api/reservas/40/pago (PagosControllerTest)
const COBRO: Pago = {
  idReserva: 40,
  estado: 'PENDIENTE',
  estadoReserva: 'VIGENTE',
  codigoQr: 'SIMULADOR|SIM-1|150.00',
  urlPasarela: null,
  segundosRestantes: 581,
  requiereDevolucion: false,
  entradas: 0,
  simulador: true,
};

function rechazo(status: number, codigo: string): ErrorApi {
  return { status, detail: 'detalle técnico', codigo };
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

class ReservasFalso {
  reservaFalsa = new RecursoFalso<Reserva>();
  idPedido: Signal<number | null> | null = null;

  // Sin cobro todavía, salvo que el test diga otra cosa
  consultarPago = vi.fn<(id: number) => Promise<Pago>>(() => Promise.reject(rechazo(404, 'PAGO_NO_ENCONTRADO')));
  pagar = vi.fn<(id: number) => Promise<Pago>>(() => Promise.resolve(COBRO));
  simularPago = vi.fn<(id: number, resultado: string) => Promise<void>>(() => Promise.resolve());

  reserva(idReserva: Signal<number | null>) {
    this.idPedido = idReserva;
    return this.reservaFalsa;
  }
}

describe('ReservaPage', () => {
  let harness: RouterTestingHarness;
  let reservas: ReservasFalso;

  // Solo el intervalo y el reloj son falsos: el resto de los timers sigue real para que Angular se estabilice. El reloj
  // del navegador va media hora atrasado respecto del servidor: la cuenta no puede depender de él
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] });
    vi.setSystemTime(new Date('2026-09-21T19:30:00'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  // null: la reserva todavía no llegó (o falló)
  async function abrir(
    url = '/reservas/40',
    reserva: Reserva | null = RESERVA,
    preparar: (falso: ReservasFalso) => void = () => undefined,
  ) {
    TestBed.resetTestingModule();
    reservas = new ReservasFalso();
    reservas.reservaFalsa.value.set(reserva ?? undefined);
    preparar(reservas);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: '', children: [] },
          { path: 'catalogo/:id', children: [] },
          { path: 'reservas/:id', component: ReservaPage },
        ]),
        { provide: ReservasService, useValue: reservas },
      ],
    });
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, ReservaPage);
    harness.fixture.autoDetectChanges();
    await harness.fixture.whenStable();
  }

  function pantalla(): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  function qrDelCobro(): SVGSVGElement | null {
    return pantalla().querySelector('svg[role="img"][aria-label="Código QR del cobro"]');
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

  function cuenta(): string {
    return pantalla().querySelector('[role="timer"]')?.textContent?.trim() ?? '';
  }

  async function pasar(segundos: number) {
    vi.advanceTimersByTime(segundos * 1000);
    await harness.fixture.whenStable();
  }

  function boton(textoDelBoton: string): HTMLButtonElement | undefined {
    return Array.from(pantalla().querySelectorAll<HTMLButtonElement>('button')).find(
      (cada) => cada.textContent?.trim() === textoDelBoton,
    );
  }

  function estado(): string {
    return pantalla().querySelector('[role="status"]')?.textContent?.trim() ?? '';
  }

  // Deja que se resuelvan las promesas del servicio y que la pantalla se actualice
  async function asentar() {
    await Promise.resolve();
    await harness.fixture.whenStable();
  }

  async function abrirConCobro(cobro: Partial<Pago> = {}) {
    await abrir('/reservas/40', RESERVA, (falso) => falso.consultarPago.mockResolvedValue({ ...COBRO, ...cobro }));
    await asentar();
  }

  function enlace(textoDelEnlace: string): HTMLAnchorElement | undefined {
    return Array.from(pantalla().querySelectorAll<HTMLAnchorElement>('a')).find(
      (cada) => cada.textContent?.trim() === textoDelEnlace,
    );
  }

  it('pide la reserva del id de la URL', async () => {
    await abrir();

    expect(reservas.idPedido?.()).toBe(40);
  });

  // CU-06 RF-27
  it('muestra el evento, el tipo, la cantidad y el total', async () => {
    await abrir();

    expect(pantalla().querySelector('h1')?.textContent).toContain('Tu reserva');
    expect(texto()).toContain('Noche de jazz');
    expect(texto()).toContain('04/10/2026');
    expect(texto()).toContain('22:00–03:00');
    expect(texto()).toContain('Teatro Municipal');
    expect(texto()).toContain('General');
    expect(texto()).toContain('3');
    expect(texto()).toContain(precio(50));
    expect(texto()).toContain(precio(150));
    expect(texto()).toContain('Te guardamos estas entradas durante 10 minutos.');
  });

  // La cuenta sale de los segundos del servidor, no del reloj del navegador
  it('cuenta los segundos que le quedan', async () => {
    await abrir();

    expect(cuenta()).toBe('Vence en 09:41');
    await pasar(1);
    expect(cuenta()).toBe('Vence en 09:40');
    await pasar(60);
    expect(cuenta()).toBe('Vence en 08:40');
  });

  it('al llegar a cero dice que venció y ofrece volver al evento', async () => {
    await abrir();

    await pasar(581);

    expect(cuenta()).toBe('');
    expect(texto()).toContain('La reserva venció y las entradas se liberaron.');
    expect(texto()).not.toContain('Te guardamos');
    expect(enlace('Volver al evento')?.getAttribute('href')).toBe('/catalogo/5');
  });

  it('una reserva que ya llega vencida lo dice de entrada', async () => {
    await abrir('/reservas/40', { ...RESERVA, estado: 'EXPIRADA', segundosRestantes: 0 });

    expect(cuenta()).toBe('');
    expect(texto()).toContain('La reserva venció y las entradas se liberaron.');
  });

  // Una reserva pagada (CU-07) llega con 0 segundos pero no venció: ni cuenta ni aviso de vencida
  it('una reserva confirmada no se muestra como vencida', async () => {
    await abrir('/reservas/40', { ...RESERVA, estado: 'CONFIRMADA', segundosRestantes: 0 });

    expect(cuenta()).toBe('');
    expect(texto()).not.toContain('venció');
  });

  it('al salir de la pantalla deja de contar', async () => {
    await abrir();
    expect(vi.getTimerCount()).toBeGreaterThan(0);

    harness.fixture.destroy();

    expect(vi.getTimerCount()).toBe(0);
  });

  it('basura en la URL no pide nada', async () => {
    await abrir('/reservas/abc', null);

    expect(reservas.idPedido?.()).toBeNull();
    expect(texto()).toContain('No encontramos esa reserva.');
  });

  // RF-20: la de otro cliente responde 404 igual que una inexistente
  it('un 404 dice que no la encontró', async () => {
    await abrir('/reservas/41', null);
    reservas.reservaFalsa.error.set(
      new HttpErrorResponse({
        status: 404,
        error: { status: 404, detail: 'No existe la reserva indicada.', codigo: 'RESERVA_NO_ENCONTRADA' },
      }),
    );
    await harness.fixture.whenStable();

    expect(texto()).toContain('No encontramos esa reserva.');
    expect(enlace('Ver los eventos')?.getAttribute('href')).toBe('/');
    expect(texto()).not.toContain('Reintentar');
  });

  it('si la carga falla por otro motivo, ofrece reintentar', async () => {
    await abrir('/reservas/40', null);
    reservas.reservaFalsa.error.set(new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') }));
    await harness.fixture.whenStable();

    expect(texto()).toContain('No pudimos conectar con el servidor');
    Array.from(pantalla().querySelectorAll('button'))
      .find((boton) => boton.textContent?.trim() === 'Reintentar')!
      .click();
    expect(reservas.reservaFalsa.reload).toHaveBeenCalled();
  });

  it('mientras carga lo dice', async () => {
    await abrir('/reservas/40', null);

    expect(texto()).toContain('Cargando la reserva…');
  });

  // --- Pago (CU-07) ---

  describe('pago', () => {
    it('sin cobro y con la reserva vigente ofrece pagar', async () => {
      await abrir();
      await asentar();

      expect(reservas.consultarPago).toHaveBeenCalledWith(40);
      expect(boton('Pagar')).toBeDefined();
    });

    it('con la reserva vencida no ofrece pagar', async () => {
      await abrir('/reservas/40', { ...RESERVA, estado: 'EXPIRADA', segundosRestantes: 0 });
      await asentar();

      expect(boton('Pagar')).toBeUndefined();
    });

    // CU-08 RF-12: el QR del cobro como imagen, no como texto
    it('pagar genera el cobro y muestra su QR como imagen', async () => {
      await abrir();
      await asentar();

      boton('Pagar')!.click();
      await asentar();

      expect(reservas.pagar).toHaveBeenCalledWith(40);
      expect(texto()).toContain('Escanea este código con la app de tu banco');
      expect(qrDelCobro()?.querySelector('path')?.getAttribute('d')).toBe(await trazoDe('SIMULADOR|SIM-1|150.00'));
      expect(texto()).not.toContain('SIMULADOR|SIM-1');
      expect(boton('Pagar')).toBeUndefined();
      expect(cuenta()).toBe('Vence en 09:41');
    });

    // «Pagar» desaparece al llegar el cobro: el foco pasa al título del cobro y no se pierde en la página
    it('al llegar el cobro el foco pasa a su título', async () => {
      await abrir();
      await asentar();

      boton('Pagar')!.click();
      await asentar();

      expect(document.activeElement?.id).toBe('titulo-cobro');
    });

    // RF-4: un doble clic pide un solo cobro
    it('un doble clic pide un solo cobro', async () => {
      await abrir();
      await asentar();
      let resolver: (pago: Pago) => void = () => undefined;
      reservas.pagar.mockReturnValue(new Promise((listo) => (resolver = listo)));

      boton('Pagar')!.click();
      await harness.fixture.whenStable();
      expect(boton('Generando el cobro…')?.disabled).toBe(true);
      boton('Generando el cobro…')!.click();
      resolver(COBRO);
      await asentar();

      expect(reservas.pagar).toHaveBeenCalledTimes(1);
    });

    it('con página de pago muestra el enlace; sin ella, no', async () => {
      await abrirConCobro({ urlPasarela: 'https://pago.example/1' });
      expect(enlace('Abrir la página de pago')?.getAttribute('href')).toBe('https://pago.example/1');

      await abrirConCobro({ urlPasarela: null });
      expect(enlace('Abrir la página de pago')).toBeUndefined();
    });

    // RF-25 y RF-26: consulta cada 3 segundos hasta el estado final, y ahí se corta
    it('consulta cada 3 segundos y al confirmarse lo dice y deja de consultar', async () => {
      await abrirConCobro();
      const timersMientrasEspera = vi.getTimerCount();
      expect(reservas.consultarPago).toHaveBeenCalledTimes(1);

      await pasar(3);
      await asentar();
      expect(reservas.consultarPago).toHaveBeenCalledTimes(2);

      reservas.consultarPago.mockResolvedValue({ ...COBRO, estado: 'PAGADO', estadoReserva: 'CONFIRMADA', entradas: 2 });
      await pasar(3);
      await asentar();

      expect(estado()).toContain('Compra confirmada. Te enviamos tus 2 entradas por correo.');
      expect(cuenta()).toBe('');
      expect(vi.getTimerCount()).toBeLessThan(timersMientrasEspera);
      await pasar(9);
      await asentar();
      expect(reservas.consultarPago).toHaveBeenCalledTimes(3);
    });

    it('un error de red en una consulta no corta el ciclo', async () => {
      await abrirConCobro();
      reservas.consultarPago.mockRejectedValueOnce(rechazo(0, 'SIN_CONEXION'));

      await pasar(3);
      await asentar();
      await pasar(3);
      await asentar();

      expect(reservas.consultarPago).toHaveBeenCalledTimes(3);
    });

    it('al vencer la reserva deja de consultar', async () => {
      await abrirConCobro();

      await pasar(581);
      await asentar();
      const consultas = reservas.consultarPago.mock.calls.length;
      await pasar(9);
      await asentar();

      expect(reservas.consultarPago).toHaveBeenCalledTimes(consultas);
      expect(estado()).toContain('La reserva venció');
    });

    // Un pago que llega en el último segundo, después de la última vuelta: al vencer se pregunta una vez más
    it('al vencer pregunta una última vez y muestra un pago que llegó justo', async () => {
      await abrirConCobro();
      await pasar(580);
      await asentar();
      reservas.consultarPago.mockResolvedValue({ ...COBRO, estado: 'PAGADO', estadoReserva: 'CONFIRMADA', entradas: 3 });

      await pasar(1);
      await asentar();

      expect(estado()).toContain('Compra confirmada. Te enviamos tus 3 entradas por correo.');
      expect(estado()).not.toContain('La reserva venció');
    });

    it('un pago que llegó tarde y sin cupo explica la devolución', async () => {
      await abrirConCobro({ estado: 'PAGADO', estadoReserva: 'EXPIRADA', requiereDevolucion: true });

      expect(estado()).toContain('Tu pago llegó cuando la reserva ya había vencido y no quedaban entradas.');
      expect(estado()).toContain('Te devolveremos el dinero.');
      expect(estado()).not.toContain('Compra confirmada');
      expect(estado()).not.toContain('La reserva venció y las entradas se liberaron.');
    });

    it('un error del pago lo dice y ofrece volver al evento', async () => {
      await abrirConCobro({ estado: 'ERROR', estadoReserva: 'EXPIRADA' });

      expect(estado()).toContain('El pago no se pudo completar y liberamos tus entradas.');
      expect(enlace('Volver al evento')?.getAttribute('href')).toBe('/catalogo/5');
      expect(cuenta()).toBe('');
    });

    it('al abrir una reserva ya pagada muestra la confirmación y no ofrece pagar', async () => {
      await abrir('/reservas/40', { ...RESERVA, estado: 'CONFIRMADA', segundosRestantes: 0 }, (falso) =>
        falso.consultarPago.mockResolvedValue({ ...COBRO, estado: 'PAGADO', estadoReserva: 'CONFIRMADA', entradas: 3 }),
      );
      await asentar();

      expect(estado()).toContain('Compra confirmada. Te enviamos tus 3 entradas por correo.');
      expect(boton('Pagar')).toBeUndefined();
      expect(qrDelCobro()).toBeNull();
    });

    // RF-27: los botones del simulador, solo con el simulador
    it('con el simulador, simular el pago lo fuerza y vuelve a consultar', async () => {
      await abrirConCobro();
      expect(texto()).toContain('Herramienta de pruebas');

      boton('Simular pago')!.click();
      await asentar();
      await asentar();

      expect(reservas.simularPago).toHaveBeenCalledWith(40, 'PAGADO');
      expect(reservas.consultarPago).toHaveBeenCalledTimes(2);
      expect(boton('Simular error')).toBeDefined();
    });

    it('sin el simulador no hay botones de prueba', async () => {
      await abrirConCobro({ simulador: false });

      expect(texto()).not.toContain('Herramienta de pruebas');
      expect(boton('Simular pago')).toBeUndefined();
    });

    // FA-01: el cobro no se pudo generar y las entradas se liberaron
    it('si el cobro no se puede generar lo dice y deja de ofrecer pagar', async () => {
      await abrir('/reservas/40', RESERVA, (falso) => falso.pagar.mockRejectedValue(rechazo(502, 'ERROR_SISTEMA_PAGO')));
      await asentar();

      boton('Pagar')!.click();
      await asentar();

      expect(texto()).toContain('No pudimos generar el cobro y liberamos tus entradas.');
      expect(boton('Pagar')).toBeUndefined();
      expect(cuenta()).toBe('');
      expect(enlace('Volver al evento')?.getAttribute('href')).toBe('/catalogo/5');
    });

    it('si la primera consulta falla por otro motivo, ofrece reintentar', async () => {
      await abrir('/reservas/40', RESERVA, (falso) =>
        falso.consultarPago.mockRejectedValueOnce(rechazo(0, 'SIN_CONEXION')),
      );
      await asentar();
      expect(texto()).toContain('No pudimos conectar con el servidor');

      boton('Reintentar')!.click();
      await asentar();

      expect(reservas.consultarPago).toHaveBeenCalledTimes(2);
      expect(boton('Pagar')).toBeDefined();
    });

    it('al salir de la pantalla no quedan consultas pendientes', async () => {
      await abrirConCobro();

      harness.fixture.destroy();

      expect(vi.getTimerCount()).toBe(0);
    });
  });
});
