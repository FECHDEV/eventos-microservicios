import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { EventosService } from '../../../core/api/eventos.service';
import { ErrorApi } from '../../../core/modelos/error-api';
import { EstadoEvento, Evento, EventoActivado } from '../../../core/modelos/evento';
import { EventoActual } from '../evento-actual';
import { EstadoEventoPage } from './estado-evento.page';

const JAZZ: Evento = {
  idEvento: 5,
  nombre: 'Noche de jazz',
  descripcion: null,
  fecha: '2026-10-04',
  fechaOriginal: null,
  horaInicio: '22:00:00',
  horaFin: '03:00:00',
  lugar: 'Teatro Municipal',
  restriccionEdad: null,
  limiteCompra: null,
  estado: 'BORRADOR',
  ventaAbierta: false,
  tieneImagen: false,
};

function enEstado(estado: EstadoEvento, ventaAbierta = estado === 'ACTIVO' || estado === 'POSTERGADO'): Evento {
  return { ...JAZZ, estado, ventaAbierta };
}

function rechazo(codigo: string, errores?: { campo: string; mensaje: string }[]): ErrorApi {
  return { status: codigo === 'DATOS_INVALIDOS' ? 400 : 409, detail: 'detalle', codigo, errores };
}

class EventosFalso {
  llamadas: string[] = [];
  postergaciones: string[] = [];
  respuesta: () => Promise<Evento> = () => Promise.resolve(enEstado('ACTIVO'));
  // CU-12 FA-02: cuántas entradas usadas reactiva la activación
  reactivadas = 0;

  activar(): Promise<EventoActivado> {
    this.llamadas.push('activar');
    return this.respuesta().then((evento) => ({ ...evento, entradasReactivadas: this.reactivadas }));
  }

  postergar(_id: number, fecha: string): Promise<Evento> {
    this.llamadas.push('postergar');
    this.postergaciones.push(fecha);
    return this.respuesta();
  }

  cancelar(): Promise<Evento> {
    this.llamadas.push('cancelar');
    return this.respuesta();
  }

  cerrarVenta(): Promise<Evento> {
    this.llamadas.push('cerrarVenta');
    return this.respuesta();
  }
}

class EventoActualFalso {
  readonly evento = signal<Evento | undefined>(JAZZ);
  readonly reemplazar = vi.fn((evento: Evento) => this.evento.set(evento));
  readonly recargar = vi.fn();
}

describe('EstadoEventoPage', () => {
  let harness: RouterTestingHarness;
  let eventos: EventosFalso;
  let actual: EventoActualFalso;

  // «Hoy» es el 18/09/2026: solo se falsea la fecha, no los temporizadores
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 8, 18, 12, 0));
  });

  afterEach(() => vi.useRealTimers());

  async function abrir(evento: Evento) {
    TestBed.resetTestingModule();
    eventos = new EventosFalso();
    actual = new EventoActualFalso();
    actual.evento.set(evento);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          {
            path: 'eventos/:id',
            providers: [{ provide: EventoActual, useValue: actual }],
            children: [
              { path: 'estado', component: EstadoEventoPage },
              { path: 'tipos', children: [] },
            ],
          },
        ]),
        { provide: EventosService, useValue: eventos },
      ],
    });
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/eventos/5/estado', EstadoEventoPage);
    harness.fixture.autoDetectChanges();
    await harness.fixture.whenStable();
  }

  function pantalla(): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  function texto(): string {
    return pantalla().textContent ?? '';
  }

  function botones(): string[] {
    return Array.from(pantalla().querySelectorAll<HTMLButtonElement>('button:not(dialog button)')).map(
      (boton) => boton.textContent?.trim() ?? '',
    );
  }

  function boton(textoDelBoton: string): HTMLButtonElement {
    const encontrado = Array.from(pantalla().querySelectorAll<HTMLButtonElement>('button:not(dialog button)')).find(
      (cada) => cada.textContent?.trim() === textoDelBoton,
    );
    if (encontrado === undefined) {
      throw new Error(`No hay botón «${textoDelBoton}»`);
    }
    return encontrado;
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

  async function elegirFecha(valor: string) {
    const campo = pantalla().querySelector<HTMLInputElement>('#fecha-nueva')!;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
    campo.dispatchEvent(new Event('blur'));
    await harness.fixture.whenStable();
  }

  describe('acciones según el estado', () => {
    // decisiones.md → Transiciones de estado del evento
    it('un borrador se activa o se cancela', async () => {
      await abrir(enEstado('BORRADOR'));

      expect(botones()).toEqual(['Activar', 'Cancelar evento']);
    });

    it('un activo con la venta abierta se posterga, cierra la venta o se cancela', async () => {
      await abrir(enEstado('ACTIVO'));

      expect(botones()).toEqual(['Postergar', 'Cerrar la venta', 'Cancelar evento']);
    });

    it('un activo con la venta cerrada no ofrece cerrarla de nuevo', async () => {
      await abrir(enEstado('ACTIVO', false));

      expect(botones()).not.toContain('Cerrar la venta');
    });

    it('un postergado vuelve a activarse, se posterga otra vez o se cancela', async () => {
      await abrir(enEstado('POSTERGADO'));

      expect(botones()).toEqual(['Activar', 'Postergar', 'Cerrar la venta', 'Cancelar evento']);
    });

    // Cancelado es definitivo
    it('un cancelado no ofrece ninguna acción', async () => {
      await abrir(enEstado('CANCELADO', false));

      expect(botones()).toEqual([]);
      expect(texto()).toContain('definitivo');
    });
  });

  describe('activar', () => {
    // CU-12 RF-5: sin confirmación, porque se deshace cerrando la venta
    it('activa sin preguntar y deja el evento al día en la ficha', async () => {
      await abrir(enEstado('BORRADOR'));

      boton('Activar').click();
      await asentar();

      expect(eventos.llamadas).toEqual(['activar']);
      expect(actual.reemplazar).toHaveBeenCalledWith(expect.objectContaining({ estado: 'ACTIVO' }));
      expect(texto()).toContain('El evento quedó activo y con la venta abierta.');
      expect(texto()).not.toMatch(/reactiv/i);
    });

    // CU-12 FA-02: activar un postergado reactiva las entradas usadas, y eso no se deshace cerrando la venta
    it('activar un postergado avisa de la reactivación y pide confirmación', async () => {
      await abrir(enEstado('POSTERGADO'));

      expect(texto()).toContain('Las entradas usadas antes de la postergación vuelven a estar activas.');
      boton('Activar').click();
      await asentar();
      expect(dialogo().textContent).toContain('Las entradas usadas antes de la postergación vuelven a estar activas');
      await responderDialogo(false);

      expect(eventos.llamadas).toEqual([]);
    });

    // RF-31: la confirmación dice cuántas entradas usadas volvieron a estar activas
    it('un postergado con una entrada reactivada lo dice en singular', async () => {
      await abrir(enEstado('POSTERGADO'));
      eventos.reactivadas = 1;

      boton('Activar').click();
      await asentar();
      await responderDialogo(true);

      expect(texto()).toContain('El evento quedó activo y con la venta abierta. Se reactivó 1 entrada utilizada.');
    });

    it('un postergado con varias entradas reactivadas lo dice en plural', async () => {
      await abrir(enEstado('POSTERGADO'));
      eventos.reactivadas = 3;

      boton('Activar').click();
      await asentar();
      await responderDialogo(true);

      expect(texto()).toContain('El evento quedó activo y con la venta abierta. Se reactivaron 3 entradas utilizadas.');
      // La ficha guarda el evento, sin la cantidad
      expect(actual.reemplazar).toHaveBeenCalledWith(expect.objectContaining({ estado: 'ACTIVO' }));
      expect(actual.reemplazar.mock.calls[0][0]).not.toHaveProperty('entradasReactivadas');
    });

    it('si la respuesta no trae la cantidad, no menciona entradas', async () => {
      await abrir(enEstado('POSTERGADO'));
      eventos.reactivadas = undefined as unknown as number;

      boton('Activar').click();
      await asentar();
      await responderDialogo(true);

      expect(texto()).toContain('El evento quedó activo y con la venta abierta.');
      expect(texto()).not.toMatch(/reactiv|undefined/i);
    });

    // RF-6: sin tipos de entrada no se activa
    it('sin tipos de entrada lo explica y ofrece ir a agregarlos', async () => {
      await abrir(enEstado('BORRADOR'));
      eventos.respuesta = () => Promise.reject(rechazo('EVENTO_SIN_TIPOS_DE_ENTRADA'));

      boton('Activar').click();
      await asentar();

      expect(texto()).toContain('Agrega al menos un tipo de entrada antes de activar el evento.');
      expect(pantalla().querySelector('a[href="/eventos/5/tipos"]')).not.toBeNull();
      expect(actual.recargar).not.toHaveBeenCalled();
    });

    it('doble clic en activar manda una sola petición', async () => {
      await abrir(enEstado('BORRADOR'));
      let terminar!: (evento: Evento) => void;
      eventos.respuesta = () => new Promise((resolver) => (terminar = resolver));

      boton('Activar').click();
      boton('Activar').click();
      await asentar();
      terminar(enEstado('ACTIVO'));
      await asentar();

      expect(eventos.llamadas).toEqual(['activar']);
    });

    // Otro administrador se adelantó
    it.each(['CAMBIO_DE_ESTADO_NO_PERMITIDO', 'EVENTO_CANCELADO'])('un 409 %s se explica y recarga el evento', async (codigo) => {
      await abrir(enEstado('BORRADOR'));
      eventos.respuesta = () => Promise.reject(rechazo(codigo));

      boton('Activar').click();
      await asentar();

      expect(pantalla().querySelector('[role="alert"] .aviso')).not.toBeNull();
      expect(actual.recargar).toHaveBeenCalled();
    });
  });

  describe('postergar', () => {
    // RF-11: la fecha nueva no puede ser anterior a hoy ni la misma
    it('no acepta la misma fecha', async () => {
      await abrir(enEstado('ACTIVO'));
      await elegirFecha('2026-10-04');

      boton('Postergar').click();
      await asentar();

      expect(texto()).toContain('Elige una fecha distinta de la actual.');
      expect(dialogo().hasAttribute('open')).toBe(false);
      expect(eventos.llamadas).toEqual([]);
    });

    it('no acepta una fecha de ayer', async () => {
      await abrir(enEstado('ACTIVO'));
      await elegirFecha('2026-09-17');

      boton('Postergar').click();
      await asentar();

      expect(texto()).toContain('La fecha no puede ser anterior a hoy.');
      expect(eventos.llamadas).toEqual([]);
    });

    it('sin fecha no posterga', async () => {
      await abrir(enEstado('ACTIVO'));

      boton('Postergar').click();
      await asentar();

      expect(texto()).toContain('Elige la fecha nueva.');
      expect(eventos.llamadas).toEqual([]);
    });

    it('pide confirmación y cancelar no posterga', async () => {
      await abrir(enEstado('ACTIVO'));
      await elegirFecha('2026-11-01');

      boton('Postergar').click();
      await asentar();
      expect(dialogo().hasAttribute('open')).toBe(true);
      expect(dialogo().textContent).toContain('01/11/2026');
      await responderDialogo(false);

      expect(eventos.llamadas).toEqual([]);
    });

    it('al confirmar posterga y deja el evento al día', async () => {
      await abrir(enEstado('ACTIVO'));
      eventos.respuesta = () =>
        Promise.resolve({ ...enEstado('POSTERGADO'), fecha: '2026-11-01', fechaOriginal: '2026-10-04' });
      await elegirFecha('2026-11-01');

      boton('Postergar').click();
      await asentar();
      await responderDialogo(true);

      expect(eventos.postergaciones).toEqual(['2026-11-01']);
      expect(actual.reemplazar).toHaveBeenCalledWith(expect.objectContaining({ estado: 'POSTERGADO' }));
      expect(texto()).toContain('Evento postergado al 01/11/2026.');
    });

    // El 400 del backend, si llega, va al campo
    it('lo que rechaza el backend aparece en la fecha', async () => {
      await abrir(enEstado('ACTIVO'));
      eventos.respuesta = () =>
        Promise.reject(rechazo('DATOS_INVALIDOS', [{ campo: 'fecha', mensaje: 'no puede ser anterior a hoy' }]));
      await elegirFecha('2026-11-01');

      boton('Postergar').click();
      await asentar();
      await responderDialogo(true);

      expect(texto()).toContain('La fecha no puede ser anterior a hoy.');
    });
  });

  describe('cerrar la venta y cancelar', () => {
    it('cerrar la venta pide confirmación y al confirmar la cierra', async () => {
      await abrir(enEstado('ACTIVO'));
      eventos.respuesta = () => Promise.resolve(enEstado('ACTIVO', false));

      boton('Cerrar la venta').click();
      await asentar();
      expect(dialogo().textContent).toContain('Nadie más podrá comprar entradas');
      await responderDialogo(true);

      expect(eventos.llamadas).toEqual(['cerrarVenta']);
      expect(texto()).toContain('La venta quedó cerrada.');
    });

    it('cancelar pide confirmación y cancelar el diálogo no cambia nada', async () => {
      await abrir(enEstado('ACTIVO'));

      boton('Cancelar evento').click();
      await asentar();
      expect(dialogo().textContent).toContain('definitivo');
      await responderDialogo(false);

      expect(eventos.llamadas).toEqual([]);
    });

    it('al confirmar cancela el evento', async () => {
      await abrir(enEstado('ACTIVO'));
      eventos.respuesta = () => Promise.resolve(enEstado('CANCELADO', false));

      boton('Cancelar evento').click();
      await asentar();
      await responderDialogo(true);

      expect(eventos.llamadas).toEqual(['cancelar']);
      expect(actual.reemplazar).toHaveBeenCalledWith(expect.objectContaining({ estado: 'CANCELADO' }));
      expect(texto()).toContain('El evento quedó cancelado.');
    });

    it('una venta que otro ya cerró se explica y recarga el evento', async () => {
      await abrir(enEstado('ACTIVO'));
      eventos.respuesta = () => Promise.reject(rechazo('VENTA_YA_CERRADA'));

      boton('Cerrar la venta').click();
      await asentar();
      await responderDialogo(true);

      expect(texto()).toContain('La venta de este evento ya está cerrada.');
      expect(actual.recargar).toHaveBeenCalled();
    });
  });
});
