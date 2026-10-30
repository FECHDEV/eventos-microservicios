import { Signal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { EventosService } from '../../../core/api/eventos.service';
import { ErrorApi } from '../../../core/modelos/error-api';
import { Evento } from '../../../core/modelos/evento';
import { DatosDeTipoEntrada, TipoEntrada } from '../../../core/modelos/tipo-entrada';
import { EventoActual } from '../evento-actual';
import { TiposEntradaPage } from './tipos-entrada.page';

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
  estado: 'ACTIVO',
  ventaAbierta: true,
  tieneImagen: false,
};

// Como responde GET /api/eventos/5/tipos-entrada (TiposEntradaControllerTest):
// 30 vendidas o reservadas
const GENERAL: TipoEntrada = {
  idTipoEntrada: 3,
  nombre: 'General',
  precio: 50,
  cantidadTotal: 100,
  cantidadDisponible: 70,
};

function rechazo(codigo: string, errores?: { campo: string; mensaje: string }[]): ErrorApi {
  return { status: codigo === 'DATOS_INVALIDOS' ? 400 : 409, detail: 'detalle', codigo, errores };
}

class RecursoFalso<T> {
  readonly value = signal<T | undefined>(undefined);
  readonly error = signal<unknown>(undefined);
  readonly reload = vi.fn(() => true);
  hasValue(): boolean {
    return this.value() !== undefined;
  }
}

class EventosFalso {
  tiposFalsos = new RecursoFalso<TipoEntrada[]>();
  idPedido: Signal<number | null> | null = null;
  registrados: DatosDeTipoEntrada[] = [];
  modificados: { idTipo: number; datos: DatosDeTipoEntrada }[] = [];
  respuesta: () => Promise<TipoEntrada> = () =>
    Promise.resolve({ idTipoEntrada: 4, nombre: 'VIP', precio: 120.5, cantidadTotal: 40, cantidadDisponible: 40 });

  tipos(idEvento: Signal<number | null>) {
    this.idPedido = idEvento;
    return this.tiposFalsos;
  }

  registrarTipo(_idEvento: number, datos: DatosDeTipoEntrada): Promise<TipoEntrada> {
    this.registrados.push(datos);
    return this.respuesta();
  }

  modificarTipo(_idEvento: number, idTipo: number, datos: DatosDeTipoEntrada): Promise<TipoEntrada> {
    this.modificados.push({ idTipo, datos });
    return this.respuesta();
  }
}

class EventoActualFalso {
  readonly evento = signal<Evento | undefined>(JAZZ);
  readonly id = signal<number | null>(5);
  readonly recargar = vi.fn();
}

describe('TiposEntradaPage', () => {
  let harness: RouterTestingHarness;
  let eventos: EventosFalso;
  let actual: EventoActualFalso;

  async function abrir(tipos: TipoEntrada[] = [GENERAL], evento: Evento = JAZZ) {
    TestBed.resetTestingModule();
    eventos = new EventosFalso();
    eventos.tiposFalsos.value.set(tipos);
    actual = new EventoActualFalso();
    actual.evento.set(evento);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          {
            path: 'eventos/:id',
            providers: [{ provide: EventoActual, useValue: actual }],
            children: [{ path: 'tipos', component: TiposEntradaPage }],
          },
        ]),
        { provide: EventosService, useValue: eventos },
      ],
    });
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/eventos/5/tipos', TiposEntradaPage);
    harness.fixture.autoDetectChanges();
    await harness.fixture.whenStable();
  }

  function pantalla(): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  function texto(): string {
    return pantalla().textContent ?? '';
  }

  function campo(id: string): HTMLInputElement {
    return pantalla().querySelector<HTMLInputElement>('#' + id)!;
  }

  function pagina(): TiposEntradaPage {
    return harness.routeDebugElement!.componentInstance as TiposEntradaPage;
  }

  function fila(nombre: string): HTMLTableRowElement {
    const filas: HTMLTableRowElement[] = Array.from(pantalla().querySelectorAll('tbody tr'));
    const encontrada = filas.find((cada) => cada.querySelector('th')?.textContent?.trim() === nombre);
    if (encontrada === undefined) {
      throw new Error(`No hay fila de «${nombre}»`);
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

  async function asentar() {
    await new Promise((seguir) => setTimeout(seguir, 0));
    await harness.fixture.whenStable();
  }

  async function enviar() {
    pantalla().querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    await asentar();
  }

  async function llenar(nombre: string, precio: string, cantidad: string) {
    await escribir('nombre', nombre);
    await escribir('precio', precio);
    await escribir('cantidadTotal', cantidad);
  }

  async function editar(nombre: string) {
    fila(nombre).querySelector<HTMLButtonElement>('button')!.click();
    await harness.fixture.whenStable();
  }

  it('pide los tipos del evento de la ficha', async () => {
    await abrir();

    expect(eventos.idPedido?.()).toBe(5);
  });

  // CU-11 RF-3: precio, total, disponibles y lo vendido o reservado
  it('muestra cada tipo con su precio y sus cantidades', async () => {
    await abrir();

    const general = fila('General').textContent ?? '';
    expect(general).toContain('Bs 50,00');
    expect(general).toContain('100');
    expect(general).toContain('70');
    expect(general).toContain('30');
  });

  // RF-4 y el paso siguiente del flujo: sin tipos no se puede activar
  it('sin tipos invita a agregar el primero', async () => {
    await abrir([]);

    expect(texto()).toContain('todavía no tiene tipos de entrada');
    expect(pantalla().querySelector('table')).toBeNull();
  });

  describe('agregar', () => {
    // RF-7, con el precio escrito con coma
    it('agrega un tipo válido y lo suma a la tabla', async () => {
      await abrir();
      await llenar('VIP', '120,5', '40');

      await enviar();

      expect(eventos.registrados).toEqual([{ nombre: 'VIP', precio: 120.5, cantidadTotal: 40 }]);
      expect(fila('VIP').textContent).toContain('Bs 120,50');
      expect(texto()).toContain('Tipo de entrada agregado.');
      expect(campo('nombre').value).toBe('');
    });

    // RF-8, RF-10 y RF-11: se avisan antes de enviar, con el foco en el primero
    it('avisa lo que no es válido y no envía', async () => {
      await abrir();
      await llenar(' ', '10,999', '0');

      await enviar();

      expect(eventos.registrados).toEqual([]);
      expect(texto()).toContain('Escribe el nombre del tipo de entrada.');
      expect(texto()).toContain('El precio tiene que ser mayor que 0, con hasta 2 decimales.');
      expect(texto()).toContain('La cantidad es de 1 entrada o más.');
      expect(document.activeElement).toBe(campo('nombre'));
    });

    it('un precio 0 no es válido', async () => {
      await abrir();
      await llenar('Cortesía', '0', '10');

      await enviar();

      expect(eventos.registrados).toEqual([]);
    });

    // RF-12: el backend compara sin mayúsculas ni espacios en los extremos
    it('un nombre repetido se marca en el campo nombre', async () => {
      await abrir();
      eventos.respuesta = () => Promise.reject(rechazo('TIPO_ENTRADA_DUPLICADO'));
      await llenar(' general ', '80', '5');

      await enviar();

      expect(pantalla().querySelector('#error-nombre')?.textContent).toContain('ya tiene un tipo de entrada con ese nombre');
      expect(document.activeElement).toBe(campo('nombre'));
    });

    it('doble clic en agregar manda una sola petición', async () => {
      await abrir();
      let terminar!: (tipo: TipoEntrada) => void;
      eventos.respuesta = () => new Promise((resolver) => (terminar = resolver));
      await llenar('VIP', '120', '40');

      const boton = pantalla().querySelector<HTMLButtonElement>('button[type="submit"]')!;
      boton.click();
      boton.click();
      await asentar();
      terminar({ idTipoEntrada: 4, nombre: 'VIP', precio: 120, cantidadTotal: 40, cantidadDisponible: 40 });
      await asentar();

      expect(eventos.registrados.length).toBe(1);
    });
  });

  describe('editar', () => {
    it('elegir un tipo carga sus datos en el formulario', async () => {
      await abrir();

      await editar('General');

      expect(campo('nombre').value).toBe('General');
      expect(campo('precio').value).toBe('50,00');
      expect(campo('cantidadTotal').value).toBe('100');
      expect(texto()).toContain('Editar General');
    });

    // RF-15: 30 vendidas o reservadas es el mínimo
    it('no deja bajar la cantidad de lo vendido y reservado', async () => {
      await abrir();
      await editar('General');
      await escribir('cantidadTotal', '29');

      await enviar();

      expect(eventos.modificados).toEqual([]);
      expect(texto()).toContain('No puede ser menor que 30, lo ya vendido y reservado.');
    });

    // RF-13 y RF-14: la fila se actualiza con lo que devuelve el backend
    it('guarda el cambio y actualiza la fila', async () => {
      await abrir();
      eventos.respuesta = () =>
        Promise.resolve({ ...GENERAL, precio: 65, cantidadTotal: 120, cantidadDisponible: 90 });
      await editar('General');
      await escribir('precio', '65');
      await escribir('cantidadTotal', '120');

      await enviar();

      expect(eventos.modificados).toEqual([{ idTipo: 3, datos: { nombre: 'General', precio: 65, cantidadTotal: 120 } }]);
      expect(fila('General').textContent).toContain('Bs 65,00');
      expect(fila('General').textContent).toContain('90');
      expect(texto()).toContain('Tipo de entrada guardado.');
    });

    // Otra venta entró mientras tanto: el mínimo del backend va al campo
    it('el mínimo que rechaza el backend aparece en la cantidad', async () => {
      await abrir();
      eventos.respuesta = () =>
        Promise.reject(
          rechazo('DATOS_INVALIDOS', [
            { campo: 'cantidadTotal', mensaje: 'no puede ser menor que 35, lo ya vendido y reservado' },
          ]),
        );
      await editar('General');
      await escribir('cantidadTotal', '32');

      await enviar();

      expect(texto()).toContain('La cantidad no puede ser menor que 35, lo ya vendido y reservado.');
    });

    it('cancelar la edición vuelve a agregar', async () => {
      await abrir();
      await editar('General');

      Array.from(pantalla().querySelectorAll<HTMLButtonElement>('button'))
        .find((boton) => boton.textContent?.trim() === 'Cancelar edición')!
        .click();
      await harness.fixture.whenStable();

      expect(campo('nombre').value).toBe('');
      expect(texto()).toContain('Agregar tipo de entrada');
    });
  });

  describe('estado del evento', () => {
    // CU-11 RF-20: un cancelado no se toca, pero la tabla se sigue viendo (RF-5)
    it('en un evento cancelado la tabla se ve pero no se edita', async () => {
      await abrir([GENERAL], { ...JAZZ, estado: 'CANCELADO', ventaAbierta: false });

      expect(fila('General')).toBeTruthy();
      expect(pantalla().querySelector('form')).toBeNull();
      expect(fila('General').querySelector('button')).toBeNull();
      expect(texto()).toContain('cancelado');
    });

    // Otro administrador lo canceló mientras tanto
    it('si el evento se canceló al guardar, lo explica y recarga el evento', async () => {
      await abrir();
      eventos.respuesta = () => Promise.reject(rechazo('EVENTO_CANCELADO'));
      await llenar('VIP', '120', '40');

      await enviar();

      expect(texto()).toContain('Este evento está cancelado y ya no se puede modificar.');
      expect(actual.recargar).toHaveBeenCalled();
    });
  });

  describe('cambios sin guardar', () => {
    it('solo hay cambios sin guardar si el formulario tiene algo distinto', async () => {
      await abrir();
      expect(pagina().tieneCambiosSinGuardar()).toBe(false);

      await escribir('nombre', 'VIP');
      expect(pagina().tieneCambiosSinGuardar()).toBe(true);

      await escribir('precio', '120');
      await escribir('cantidadTotal', '40');
      await enviar();
      expect(pagina().tieneCambiosSinGuardar()).toBe(false);
    });
  });
});
