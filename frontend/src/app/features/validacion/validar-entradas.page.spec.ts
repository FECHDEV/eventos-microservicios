import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { AccesosService } from '../../core/api/accesos.service';
import { EventoParaValidar, ResultadoValidacion } from '../../core/modelos/acceso';
import { ErrorApi } from '../../core/modelos/error-api';
import { LectorQr } from '../../shared/lector-qr/lector-qr';
import { ValidarEntradasPage } from './validar-entradas.page';

// Como responde GET /api/accesos/eventos (AccesosControllerTest)
const JAZZ: EventoParaValidar = {
  idEvento: 5,
  nombre: 'Noche de jazz',
  fecha: '2026-10-04',
  horaInicio: '22:00:00',
  horaFin: '03:00:00',
  lugar: 'Teatro Municipal',
};

const VALIDA: ResultadoValidacion = { resultado: 'VALIDA', tipoEntrada: 'General' };

// Lo que la pantalla usa del httpResource
class RecursoFalso<T> {
  readonly value = signal<T | undefined>(undefined);
  readonly error = signal<unknown>(undefined);
  readonly reload = vi.fn(() => true);
  hasValue(): boolean {
    return this.value() !== undefined;
  }
}

class AccesosFalso {
  recurso = new RecursoFalso<EventoParaValidar[]>();
  validar = vi.fn<(idEvento: number, codigo: string) => Promise<ResultadoValidacion>>(() => Promise.resolve(VALIDA));

  eventosParaValidar() {
    return this.recurso;
  }
}

describe('ValidarEntradasPage', () => {
  let harness: RouterTestingHarness;
  let accesos: AccesosFalso;

  // Solo el reloj es falso, para la ventana de no repetir; sin cámara en jsdom
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 4, 23, 0, 0));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  // Para lo que depende del tiempo en pantalla: también los temporizadores
  function relojFalso() {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] });
    vi.setSystemTime(new Date(2026, 9, 4, 23, 0, 0));
  }

  // null: la lista todavía no llegó (o falló)
  async function abrir(url = '/validar', eventos: EventoParaValidar[] | null = [JAZZ]) {
    TestBed.resetTestingModule();
    accesos = new AccesosFalso();
    accesos.recurso.value.set(eventos ?? undefined);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'validar', component: ValidarEntradasPage }]),
        { provide: AccesosService, useValue: accesos },
      ],
    });
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, ValidarEntradasPage);
    harness.fixture.autoDetectChanges();
    await harness.fixture.whenStable();
  }

  function pantalla(): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  function texto(): string {
    return pantalla().textContent ?? '';
  }

  function boton(textoDelBoton: string): HTMLButtonElement | undefined {
    return Array.from(pantalla().querySelectorAll<HTMLButtonElement>('button')).find((cada) =>
      cada.textContent?.includes(textoDelBoton),
    );
  }

  // Elegir y cambiar de evento son enlaces: cambian la URL
  function enlace(textoDelEnlace: string): HTMLAnchorElement | undefined {
    return Array.from(pantalla().querySelectorAll<HTMLAnchorElement>('a')).find((cada) =>
      cada.textContent?.includes(textoDelEnlace),
    );
  }

  // Deja que se resuelvan las promesas del servicio y que la pantalla se actualice
  async function asentar() {
    await Promise.resolve();
    await Promise.resolve();
    await harness.fixture.whenStable();
  }

  async function escribirYValidar(codigo: string) {
    const campo = pantalla().querySelector<HTMLInputElement>('#codigo-entrada')!;
    campo.value = codigo;
    campo.dispatchEvent(new Event('input'));
    await harness.fixture.whenStable();
    pantalla().querySelector('form')!.dispatchEvent(new Event('submit'));
    await asentar();
  }

  async function leerConLaCamara(codigo: string) {
    harness.routeDebugElement!.query(By.directive(LectorQr)).componentInstance.leido.emit(codigo);
    await asentar();
  }

  function resultado(): HTMLElement | null {
    return pantalla().querySelector('[role="status"] .resultado');
  }

  it('se titula «Validar entradas»', async () => {
    await abrir();

    expect(pantalla().querySelector('h1')?.textContent?.trim()).toBe('Validar entradas');
  });

  // RF-2
  it('muestra los eventos de hoy para elegir, y elegir uno lo deja en la URL', async () => {
    await abrir();

    expect(texto()).toContain('Noche de jazz');
    expect(texto()).toContain('22:00–03:00');
    expect(texto()).toContain('Teatro Municipal');
    enlace('Noche de jazz')!.click();
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/validar?evento=5');
    expect(pantalla().querySelector('app-lector-qr')).not.toBeNull();
    expect(pantalla().querySelector('#codigo-entrada')).not.toBeNull();
  });

  // RF-3
  it('sin eventos de hoy lo dice', async () => {
    await abrir('/validar', []);

    expect(texto()).toContain('Hoy no hay eventos para validar.');
  });

  it('un evento de la URL que no está en la lista vuelve a la elección', async () => {
    await abrir('/validar?evento=99');

    expect(pantalla().querySelector('app-lector-qr')).toBeNull();
    expect(enlace('Noche de jazz')).toBeDefined();
  });

  it('con el evento elegido muestra su nombre y ofrece cambiarlo', async () => {
    await abrir('/validar?evento=5');

    expect(texto()).toContain('Noche de jazz');
    enlace('Cambiar de evento')!.click();
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/validar');
  });

  // RF-5 y RF-14: el código escrito, sin los espacios de alrededor
  it('valida el código escrito y muestra el resultado con el tipo', async () => {
    await abrir('/validar?evento=5');

    await escribirYValidar('  codigo-81  ');

    expect(accesos.validar).toHaveBeenCalledWith(5, 'codigo-81');
    expect(resultado()?.textContent).toContain('Entrada válida');
    expect(resultado()?.textContent).toContain('General');
    expect(resultado()?.classList).toContain('valida');
    // Listo para la siguiente: el campo queda vacío
    expect(pantalla().querySelector<HTMLInputElement>('#codigo-entrada')!.value).toBe('');
  });

  // RF-7, RF-9 y RF-10
  it.each([
    ['NO_CORRESPONDE', 'No es válida para este evento'],
    ['NO_VIGENTE', 'No es válida'],
    ['YA_UTILIZADA', 'Ya fue utilizada'],
  ] as const)('un rechazo %s dice «%s» en rojo', async (codigo, frase) => {
    await abrir('/validar?evento=5');
    accesos.validar.mockResolvedValue({ resultado: codigo, tipoEntrada: null });

    await escribirYValidar('codigo-81');

    expect(resultado()?.textContent).toContain(frase);
    expect(resultado()?.classList).toContain('rechazo');
    expect(resultado()?.classList).not.toContain('valida');
  });

  it('un código vacío no se envía: lo dice junto al campo y lleva el foco ahí', async () => {
    await abrir('/validar?evento=5');

    await escribirYValidar('   ');

    expect(accesos.validar).not.toHaveBeenCalled();
    const campo = pantalla().querySelector<HTMLInputElement>('#codigo-entrada')!;
    expect(pantalla().querySelector('#error-codigo-entrada')?.textContent).toContain('Escribe el código de la entrada.');
    // submit() de Signal Forms termina después: el foco llega un momento más tarde
    await vi.waitFor(() => expect(document.activeElement).toBe(campo));
  });

  it('el nombre del evento elegido titula su sección', async () => {
    await abrir('/validar?evento=5');

    expect(pantalla().querySelector('h2')?.textContent).toContain('Noche de jazz');
  });

  it('mientras valida, el botón lo dice', async () => {
    await abrir('/validar?evento=5');
    let terminar!: (resultado: ResultadoValidacion) => void;
    accesos.validar.mockReturnValue(new Promise((resolver) => (terminar = resolver)));

    await leerConLaCamara('codigo-81');
    const botonDeValidar = pantalla().querySelector<HTMLButtonElement>('button[type="submit"]')!;
    expect(botonDeValidar.textContent?.trim()).toBe('Validando…');
    expect(botonDeValidar.disabled).toBe(true);

    terminar(VALIDA);
    await asentar();
    expect(botonDeValidar.textContent?.trim()).toBe('Validar');
  });

  // RF-7: ninguna entrada tiene un código más largo que su columna (255); un QR
  // ajeno así no se envía, que el backend lo rechazaría como dato inválido
  it('un código de más de 255 caracteres no es válido para este evento, sin enviarlo', async () => {
    await abrir('/validar?evento=5');

    await leerConLaCamara('x'.repeat(256));

    expect(accesos.validar).not.toHaveBeenCalled();
    expect(resultado()?.textContent).toContain('No es válida para este evento');
    expect(resultado()?.classList).toContain('rechazo');
  });

  // RF-13: lo que lee la cámara
  it('valida lo que lee la cámara', async () => {
    await abrir('/validar?evento=5');

    await leerConLaCamara('codigo-81');

    expect(accesos.validar).toHaveBeenCalledWith(5, 'codigo-81');
    expect(resultado()?.textContent).toContain('Entrada válida');
  });

  // RF-15: mientras el resultado está en pantalla la cámara no reenvía su código;
  // el resultado sale 5 segundos después de que la cámara deja de verlo
  it('no reenvía el código del resultado en pantalla mientras la cámara lo sigue viendo', async () => {
    await abrir('/validar?evento=5');
    relojFalso();

    await leerConLaCamara('codigo-81');
    vi.advanceTimersByTime(4000);
    await leerConLaCamara('codigo-81');
    vi.advanceTimersByTime(4000);
    await leerConLaCamara('codigo-81');
    await asentar();

    expect(resultado()?.textContent).toContain('Entrada válida');
    await leerConLaCamara('codigo-82');
    expect(accesos.validar.mock.calls.map(([, codigo]) => codigo)).toEqual(['codigo-81', 'codigo-82']);
  });

  it('5 segundos sin ver el código, el resultado sale de pantalla y el código vuelve a enviarse', async () => {
    await abrir('/validar?evento=5');
    relojFalso();

    await leerConLaCamara('codigo-81');
    vi.advanceTimersByTime(5000);
    await asentar();

    expect(resultado()).toBeNull();
    await leerConLaCamara('codigo-81');
    expect(accesos.validar).toHaveBeenCalledTimes(2);
  });

  it('una petición lenta no hace reenviar el código que la cámara siguió viendo', async () => {
    await abrir('/validar?evento=5');
    relojFalso();
    let terminar!: (resultado: ResultadoValidacion) => void;
    accesos.validar.mockReturnValueOnce(new Promise((resolver) => (terminar = resolver)));

    await leerConLaCamara('codigo-81');
    vi.advanceTimersByTime(6000);
    await leerConLaCamara('codigo-81');
    terminar(VALIDA);
    await asentar();
    await leerConLaCamara('codigo-81');

    expect(accesos.validar).toHaveBeenCalledTimes(1);
    expect(resultado()?.textContent).toContain('Entrada válida');
  });

  it('tras un error de conexión, la cámara reintenta el mismo código', async () => {
    await abrir('/validar?evento=5');
    accesos.validar.mockRejectedValueOnce({ status: 0, detail: 'sin red', codigo: 'SIN_CONEXION' } as ErrorApi);

    await leerConLaCamara('codigo-81');
    await leerConLaCamara('codigo-81');

    expect(accesos.validar).toHaveBeenCalledTimes(2);
    expect(resultado()?.textContent).toContain('Entrada válida');
  });

  it('mientras valida no manda otro código', async () => {
    await abrir('/validar?evento=5');
    let terminar!: (resultado: ResultadoValidacion) => void;
    accesos.validar.mockReturnValue(new Promise((resolver) => (terminar = resolver)));

    await leerConLaCamara('codigo-81');
    await leerConLaCamara('codigo-82');
    terminar(VALIDA);
    await asentar();

    expect(accesos.validar).toHaveBeenCalledTimes(1);
  });

  // RF-4: el evento dejó de admitir validaciones mientras se trabajaba
  it('si el evento ya no admite validar, lo dice y vuelve a la elección', async () => {
    await abrir('/validar?evento=5');
    const noDisponible: ErrorApi = { status: 409, detail: 'detalle', codigo: 'EVENTO_NO_DISPONIBLE' };
    accesos.validar.mockRejectedValue(noDisponible);

    await escribirYValidar('codigo-81');
    await harness.fixture.whenStable();

    expect(pantalla().querySelector('[role="alert"]')?.textContent).toContain(
      'Este evento ya no admite validar entradas. Elige otro de la lista.',
    );
    expect(TestBed.inject(Router).url).toBe('/validar');
    expect(accesos.recurso.reload).toHaveBeenCalled();
  });

  it('si el evento ya no existe, lo dice y vuelve a la elección', async () => {
    await abrir('/validar?evento=5');
    accesos.validar.mockRejectedValue({ status: 404, detail: 'detalle', codigo: 'EVENTO_NO_ENCONTRADO' } as ErrorApi);

    await escribirYValidar('codigo-81');
    await harness.fixture.whenStable();

    expect(pantalla().querySelector('[role="alert"]')?.textContent).toContain('No encontramos ese evento.');
    expect(TestBed.inject(Router).url).toBe('/validar');
    expect(accesos.recurso.reload).toHaveBeenCalled();
  });

  it('sin conexión lo dice y se queda en el evento', async () => {
    await abrir('/validar?evento=5');
    accesos.validar.mockRejectedValue({ status: 0, detail: 'sin red', codigo: 'SIN_CONEXION' } as ErrorApi);

    await escribirYValidar('codigo-81');

    expect(pantalla().querySelector('[role="alert"]')?.textContent).toContain('No pudimos conectar con el servidor');
    expect(TestBed.inject(Router).url).toBe('/validar?evento=5');
  });

  it('si la lista no carga, lo dice y ofrece reintentar', async () => {
    await abrir('/validar', null);
    accesos.recurso.error.set(new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') }));
    await harness.fixture.whenStable();

    expect(texto()).toContain('No pudimos conectar con el servidor');
    boton('Reintentar')!.click();
    expect(accesos.recurso.reload).toHaveBeenCalled();
  });
});
