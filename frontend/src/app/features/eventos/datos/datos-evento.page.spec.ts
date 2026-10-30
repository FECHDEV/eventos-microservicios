import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { EventosService } from '../../../core/api/eventos.service';
import { ErrorApi } from '../../../core/modelos/error-api';
import { DatosDeEvento, Evento } from '../../../core/modelos/evento';
import { EventoActual } from '../evento-actual';
import { DatosEventoPage } from './datos-evento.page';

// Como responde GET /api/eventos/5 (EventosControllerTest)
const JAZZ: Evento = {
  idEvento: 5,
  nombre: 'Noche de jazz',
  descripcion: 'Cuarteto',
  fecha: '2026-10-04',
  fechaOriginal: null,
  horaInicio: '22:00:00',
  horaFin: '03:00:00',
  lugar: 'Teatro Municipal',
  restriccionEdad: 18,
  limiteCompra: 4,
  estado: 'BORRADOR',
  ventaAbierta: false,
  tieneImagen: false,
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
  registrados: DatosDeEvento[] = [];
  modificados: DatosDeEvento[] = [];
  subidas: File[] = [];
  quitadas = 0;
  respuesta: () => Promise<Evento> = () => Promise.resolve({ ...JAZZ, idEvento: 7 });

  registrar(datos: DatosDeEvento): Promise<Evento> {
    this.registrados.push(datos);
    return this.respuesta();
  }

  modificar(_id: number, datos: DatosDeEvento): Promise<Evento> {
    this.modificados.push(datos);
    return this.respuesta();
  }

  subirImagen(_id: number, archivo: File): Promise<Evento> {
    this.subidas.push(archivo);
    return this.respuesta();
  }

  quitarImagen(): Promise<Evento> {
    this.quitadas++;
    return this.respuesta();
  }

  imagen() {
    return new RecursoFalso<Blob>();
  }
}

// Lo que la pestaña usa de la ficha
class EventoActualFalso {
  readonly evento = signal<Evento | undefined>(JAZZ);
  readonly versionDeImagen = signal(0);
  readonly reemplazar = vi.fn((evento: Evento) => this.evento.set(evento));
  readonly imagenCambiada = vi.fn((evento: Evento) => {
    this.evento.set(evento);
    this.versionDeImagen.update((version) => version + 1);
  });
  readonly recargar = vi.fn();
}

describe('DatosEventoPage', () => {
  let harness: RouterTestingHarness;
  let eventos: EventosFalso;
  let actual: EventoActualFalso;

  // «Hoy» es el 18/09/2026 para todas las pruebas: solo se falsea la fecha,
  // no los temporizadores
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 8, 18, 12, 0));
  });

  afterEach(() => vi.useRealTimers());

  async function abrir(url: string, evento: Evento = JAZZ) {
    TestBed.resetTestingModule();
    eventos = new EventosFalso();
    actual = new EventoActualFalso();
    actual.evento.set(evento);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'eventos', children: [] },
          { path: 'eventos/nuevo', component: DatosEventoPage },
          {
            path: 'eventos/:id',
            providers: [{ provide: EventoActual, useValue: actual }],
            children: [
              { path: 'datos', component: DatosEventoPage },
              { path: 'tipos', children: [] },
              { path: 'estado', children: [] },
            ],
          },
        ]),
        { provide: EventosService, useValue: eventos },
      ],
    });
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, DatosEventoPage);
    harness.fixture.autoDetectChanges();
    await harness.fixture.whenStable();
  }

  function pantalla(): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  function texto(): string {
    return pantalla().textContent ?? '';
  }

  function campo<T extends HTMLElement = HTMLInputElement>(id: string): T {
    return pantalla().querySelector<T>('#' + id)!;
  }

  function pagina(): DatosEventoPage {
    return harness.routeDebugElement!.componentInstance as DatosEventoPage;
  }

  async function escribir(id: string, valor: string) {
    const control = campo<HTMLInputElement | HTMLTextAreaElement>(id);
    control.value = valor;
    control.dispatchEvent(new Event('input'));
    control.dispatchEvent(new Event('blur'));
    await harness.fixture.whenStable();
  }

  // El envío y la respuesta se encadenan como promesas: se espera un macrotask
  async function asentar() {
    await new Promise((seguir) => setTimeout(seguir, 0));
    await harness.fixture.whenStable();
  }

  async function enviar() {
    pantalla().querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    await asentar();
  }

  async function llenarNuevo() {
    await escribir('nombre', 'Feria del libro');
    await escribir('fecha', '2026-10-12');
    await escribir('horaInicio', '10:00');
    await escribir('horaFin', '18:00');
    await escribir('lugar', 'Fexpocruz');
  }

  describe('registrar', () => {
    // CU-10 RF-7: el paso siguiente del flujo es agregar los tipos de entrada
    it('registra un evento válido y lleva a sus tipos de entrada', async () => {
      await abrir('/eventos/nuevo');
      await llenarNuevo();

      await enviar();

      expect(eventos.registrados).toEqual([
        {
          nombre: 'Feria del libro',
          descripcion: null,
          fecha: '2026-10-12',
          horaInicio: '10:00',
          horaFin: '18:00',
          lugar: 'Fexpocruz',
          restriccionEdad: null,
          limiteCompra: null,
        },
      ]);
      expect(TestBed.inject(Router).url).toBe('/eventos/7/tipos');
    });

    // RF-8: se avisa antes de enviar, con el foco en el primero a corregir
    it('sin los obligatorios no envía y lleva el foco al primero', async () => {
      await abrir('/eventos/nuevo');
      await escribir('nombre', '   ');

      await enviar();

      expect(eventos.registrados).toEqual([]);
      expect(texto()).toContain('Escribe el nombre del evento.');
      expect(texto()).toContain('Elige la fecha del evento.');
      expect(texto()).toContain('Escribe el lugar.');
      expect(document.activeElement).toBe(campo('nombre'));
    });

    // RF-11 y RF-13
    it('avisa horas iguales, edad fuera de rango y límite 0', async () => {
      await abrir('/eventos/nuevo');
      await llenarNuevo();
      await escribir('horaFin', '10:00');
      await escribir('restriccionEdad', '100');
      await escribir('limiteCompra', '0');

      await enviar();

      expect(eventos.registrados).toEqual([]);
      expect(texto()).toContain('La hora de fin tiene que ser distinta de la de inicio.');
      expect(texto()).toContain('La edad mínima va de 1 a 99 años.');
      expect(texto()).toContain('El límite por cliente es de 1 entrada o más.');
    });

    // RF-10: la fecha que se elige no puede ser anterior a hoy
    it('no acepta una fecha de ayer', async () => {
      await abrir('/eventos/nuevo');
      await llenarNuevo();
      await escribir('fecha', '2026-09-17');

      await enviar();

      expect(eventos.registrados).toEqual([]);
      expect(texto()).toContain('La fecha no puede ser anterior a hoy.');
    });

    it('acepta la fecha de hoy', async () => {
      await abrir('/eventos/nuevo');
      await llenarNuevo();
      await escribir('fecha', '2026-09-18');

      await enviar();

      expect(eventos.registrados.length).toBe(1);
    });

    // decisiones.md → Fecha y horario: de 22:00 a 03:00 termina al día siguiente
    it('avisa cuando el evento termina al día siguiente', async () => {
      await abrir('/eventos/nuevo');
      await escribir('horaInicio', '22:00');
      await escribir('horaFin', '03:00');

      expect(texto()).toContain('Termina al día siguiente');
    });

    // La imagen se sube a un evento que ya existe
    it('al registrar no ofrece subir la imagen', async () => {
      await abrir('/eventos/nuevo');

      expect(pantalla().querySelector('input[type="file"]')).toBeNull();
    });

    it('después de registrar no pregunta por cambios sin guardar', async () => {
      await abrir('/eventos/nuevo');
      await llenarNuevo();
      // Se guarda antes de enviar: al registrar se navega y la pantalla se va
      const alta = pagina();
      expect(alta.tieneCambiosSinGuardar()).toBe(true);

      await enviar();

      expect(alta.tieneCambiosSinGuardar()).toBe(false);
    });
  });

  // Pedido en la prueba manual: salir del alta sin usar «← Eventos»
  describe('cancelar el alta', () => {
    function botonCancelar(): HTMLButtonElement | undefined {
      return Array.from(pantalla().querySelectorAll<HTMLButtonElement>('form button')).find(
        (boton) => boton.textContent?.trim() === 'Cancelar',
      );
    }

    function url(): string {
      return TestBed.inject(Router).url;
    }

    it('sin nada escrito vuelve a la lista sin preguntar', async () => {
      await abrir('/eventos/nuevo');

      botonCancelar()!.click();
      await asentar();

      // Si hubiera abierto el diálogo, seguiría esperando la respuesta en el alta
      expect(url()).toBe('/eventos');
    });

    it('con datos escritos pregunta, y «Seguir editando» los conserva', async () => {
      await abrir('/eventos/nuevo');
      await escribir('nombre', 'Feria del libro');

      botonCancelar()!.click();
      await asentar();
      const dialogo = pantalla().querySelector('dialog')!;
      expect(dialogo.hasAttribute('open')).toBe(true);
      expect(dialogo.textContent).toContain('Seguir editando');
      dialogo.querySelector<HTMLButtonElement>('button.secundario')!.click();
      await asentar();

      expect(url()).toBe('/eventos/nuevo');
      expect(campo('nombre').value).toBe('Feria del libro');
    });

    it('al confirmar descarta lo escrito y vuelve a la lista sin volver a preguntar', async () => {
      await abrir('/eventos/nuevo');
      await escribir('nombre', 'Feria del libro');
      const alta = pagina();
      const confirmar = vi.spyOn(window, 'confirm');

      botonCancelar()!.click();
      await asentar();
      pantalla().querySelector('dialog')!.querySelector<HTMLButtonElement>('button.principal')!.click();
      await asentar();

      expect(url()).toBe('/eventos');
      expect(alta.tieneCambiosSinGuardar()).toBe(false);
      expect(confirmar).not.toHaveBeenCalled();
    });

    it('en la ficha no hay «Cancelar»', async () => {
      await abrir('/eventos/5/datos');

      expect(botonCancelar()).toBeUndefined();
    });
  });

  describe('editar', () => {
    it('carga los datos del evento', async () => {
      await abrir('/eventos/5/datos');

      expect(campo('nombre').value).toBe('Noche de jazz');
      expect(campo('horaInicio').value).toBe('22:00');
      expect(campo('restriccionEdad').value).toBe('18');
    });

    // RF-15 y RF-21: un borrador cuya fecha ya pasó guarda otros cambios
    it('guarda un cambio y deja el evento al día en la ficha', async () => {
      await abrir('/eventos/5/datos', { ...JAZZ, fecha: '2026-09-01' });
      eventos.respuesta = () => Promise.resolve({ ...JAZZ, fecha: '2026-09-01', descripcion: 'Quinteto' });
      await escribir('descripcion', 'Quinteto');

      await enviar();

      expect(eventos.modificados[0]).toMatchObject({ descripcion: 'Quinteto', fecha: '2026-09-01' });
      expect(actual.reemplazar).toHaveBeenCalledWith(expect.objectContaining({ descripcion: 'Quinteto' }));
      expect(texto()).toContain('Datos guardados.');
      expect(pagina().tieneCambiosSinGuardar()).toBe(false);
    });

    it('editar sin guardar cuenta como cambios sin guardar', async () => {
      await abrir('/eventos/5/datos');
      expect(pagina().tieneCambiosSinGuardar()).toBe(false);

      await escribir('lugar', 'Otro lugar');

      expect(pagina().tieneCambiosSinGuardar()).toBe(true);
    });

    // decisiones.md → Edición según su estado: cambiar la fecha es postergar
    it('en un evento activo bloquea la fecha y las horas y ofrece ir a Estado', async () => {
      await abrir('/eventos/5/datos', { ...JAZZ, estado: 'ACTIVO', ventaAbierta: true });

      expect(campo('fecha').disabled).toBe(true);
      expect(campo('horaInicio').disabled).toBe(true);
      expect(campo('horaFin').disabled).toBe(true);
      expect(campo('nombre').disabled).toBe(false);
      const enlace = pantalla().querySelector<HTMLAnchorElement>('a[href="/eventos/5/estado"]');
      expect(enlace).not.toBeNull();
    });

    it('en un evento activo guarda los demás datos con la misma fecha', async () => {
      await abrir('/eventos/5/datos', { ...JAZZ, estado: 'ACTIVO', ventaAbierta: true });
      await escribir('lugar', 'Teatro Mayor');

      await enviar();

      expect(eventos.modificados[0]).toMatchObject({ lugar: 'Teatro Mayor', fecha: '2026-10-04', horaInicio: '22:00' });
    });

    // RF-18: un cancelado no se edita
    it('en un evento cancelado todo es de solo lectura', async () => {
      await abrir('/eventos/5/datos', { ...JAZZ, estado: 'CANCELADO' });

      expect(campo('nombre').disabled).toBe(true);
      expect(pantalla().querySelector('button[type="submit"]')).toBeNull();
      expect(pantalla().querySelector('input[type="file"]')).toBeNull();
      expect(texto()).toContain('cancelado');
    });

    // Otro administrador lo activó o lo canceló mientras tanto
    it.each(['FECHA_NO_MODIFICABLE', 'EVENTO_CANCELADO'])('un 409 %s se explica y recarga el evento', async (codigo) => {
      await abrir('/eventos/5/datos');
      eventos.respuesta = () => Promise.reject(rechazo(codigo));
      await escribir('lugar', 'Teatro Mayor');

      await enviar();

      expect(pantalla().querySelector('[role="alert"]')?.textContent).not.toBe('');
      expect(actual.recargar).toHaveBeenCalled();
    });

    // RF-8 con el formato de CU-01: el error del backend va a su campo
    it('lo que rechaza el backend aparece en su campo, con el foco ahí', async () => {
      await abrir('/eventos/5/datos');
      eventos.respuesta = () =>
        Promise.reject(rechazo('DATOS_INVALIDOS', [{ campo: 'lugar', mensaje: 'admite hasta 200 caracteres' }]));
      await escribir('lugar', 'Teatro Mayor');

      await enviar();

      expect(texto()).toContain('El lugar admite hasta 200 caracteres.');
      expect(document.activeElement).toBe(campo('lugar'));
    });

    it('doble clic en guardar manda una sola petición', async () => {
      await abrir('/eventos/5/datos');
      let terminar!: (evento: Evento) => void;
      eventos.respuesta = () => new Promise((resolver) => (terminar = resolver));
      await escribir('lugar', 'Teatro Mayor');

      const boton = pantalla().querySelector<HTMLButtonElement>('button[type="submit"]')!;
      boton.click();
      boton.click();
      await asentar();
      terminar(JAZZ);
      await asentar();

      expect(eventos.modificados.length).toBe(1);
    });
  });

  describe('imagen', () => {
    function elegirArchivo(archivo: File) {
      const control = campo('imagen');
      Object.defineProperty(control, 'files', { value: [archivo], configurable: true });
      control.dispatchEvent(new Event('change'));
    }

    function archivo(tipo: string, bytes = 10): File {
      return new File([new Uint8Array(bytes)], 'foto', { type: tipo });
    }

    it('sube una imagen válida y avisa a la ficha', async () => {
      await abrir('/eventos/5/datos');
      eventos.respuesta = () => Promise.resolve({ ...JAZZ, tieneImagen: true });

      elegirArchivo(archivo('image/jpeg'));
      await asentar();

      expect(eventos.subidas.length).toBe(1);
      expect(actual.imagenCambiada).toHaveBeenCalledWith(expect.objectContaining({ tieneImagen: true }));
      expect(texto()).toContain('Imagen guardada.');
    });

    // Hallazgo de la revisión de código: la imagen cambia el evento compartido, y
    // eso no puede rellenar el formulario con los datos guardados
    it('subir una imagen no borra lo que se estaba escribiendo', async () => {
      await abrir('/eventos/5/datos');
      eventos.respuesta = () => Promise.resolve({ ...JAZZ, tieneImagen: true });
      await escribir('lugar', 'Teatro Mayor');

      elegirArchivo(archivo('image/jpeg'));
      await asentar();

      expect(actual.imagenCambiada).toHaveBeenCalled();
      expect(campo('lugar').value).toBe('Teatro Mayor');
      expect(pagina().tieneCambiosSinGuardar()).toBe(true);
    });

    // Usabilidad: el backend decide por el contenido, pero se avisa sin subir
    it('una imagen de más de 2 MB se avisa sin subirla', async () => {
      await abrir('/eventos/5/datos');

      elegirArchivo(archivo('image/png', 2 * 1024 * 1024 + 1));
      await asentar();

      expect(eventos.subidas).toEqual([]);
      expect(texto()).toContain('La imagen pesa más de 2 MB.');
    });

    it('un archivo que no es JPG, PNG ni WebP se avisa sin subirlo', async () => {
      await abrir('/eventos/5/datos');

      elegirArchivo(archivo('image/gif'));
      await asentar();

      expect(eventos.subidas).toEqual([]);
      expect(texto()).toContain('JPG, PNG o WebP');
    });

    // CU-10 RF-23: un .jpg que no lo es lo detecta el backend por su contenido
    it('si el backend la rechaza, muestra su mensaje', async () => {
      await abrir('/eventos/5/datos');
      eventos.respuesta = () => Promise.reject(rechazo('IMAGEN_INVALIDA'));

      elegirArchivo(archivo('image/jpeg'));
      await asentar();

      expect(texto()).toContain('Ese archivo no es una imagen JPG, PNG o WebP.');
      expect(actual.imagenCambiada).not.toHaveBeenCalled();
    });

    it('quitar la imagen pide confirmación y cancelar no la quita', async () => {
      await abrir('/eventos/5/datos', { ...JAZZ, tieneImagen: true });

      botonQuitar().click();
      await asentar();
      const dialogo = pantalla().querySelector('dialog')!;
      expect(dialogo.hasAttribute('open')).toBe(true);
      dialogo.querySelector<HTMLButtonElement>('button.secundario')!.click();
      await asentar();

      expect(eventos.quitadas).toBe(0);
    });

    it('al confirmar quita la imagen', async () => {
      await abrir('/eventos/5/datos', { ...JAZZ, tieneImagen: true });

      botonQuitar().click();
      await asentar();
      pantalla().querySelector('dialog')!.querySelector<HTMLButtonElement>('button.principal')!.click();
      await asentar();

      expect(eventos.quitadas).toBe(1);
      expect(actual.imagenCambiada).toHaveBeenCalled();
    });

    it('sin imagen no ofrece quitarla', async () => {
      await abrir('/eventos/5/datos');

      expect(
        Array.from(pantalla().querySelectorAll('button')).some((boton) => boton.textContent?.trim() === 'Quitar imagen'),
      ).toBe(false);
    });

    function botonQuitar(): HTMLButtonElement {
      return Array.from(pantalla().querySelectorAll<HTMLButtonElement>('button')).find(
        (boton) => boton.textContent?.trim() === 'Quitar imagen',
      )!;
    }
  });
});
