import { HttpErrorResponse } from '@angular/common/http';
import { Signal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { CatalogoService } from '../../../core/api/catalogo.service';
import { EventoDelCatalogo } from '../../../core/modelos/catalogo';
import { Sesion } from '../../../core/modelos/sesion';
import { Rol } from '../../../core/modelos/usuario';
import { SesionService } from '../../../core/sesion/sesion.service';
import { EventoDelCatalogoPage } from './evento-del-catalogo.page';

// Como responde GET /api/catalogo/5 (CatalogoControllerTest)
const JAZZ: EventoDelCatalogo = {
  idEvento: 5,
  nombre: 'Noche de jazz',
  descripcion: 'Cuarteto de jazz.\nDos sets.',
  fecha: '2026-10-04',
  fechaOriginal: null,
  horaInicio: '22:00:00',
  horaFin: '03:00:00',
  lugar: 'Teatro Municipal',
  restriccionEdad: 18,
  limiteCompra: 4,
  ventaAbierta: true,
  tieneImagen: true,
  tiposEntrada: [
    { idTipoEntrada: 1, nombre: 'General', precio: 50, disponible: true },
    { idTipoEntrada: 2, nombre: 'VIP', precio: 150, disponible: false },
  ],
};

function sesionDe(rol: Rol): Sesion {
  return {
    token: 'jwt.de.prueba',
    tipo: 'Bearer',
    expiracion: new Date(Date.now() + 3_600_000).toISOString(),
    usuario: {
      idUsuario: 2,
      nombre: 'Ana',
      apellido: 'Rojas',
      correo: 'ana@mail.com',
      telefono: null,
      fechaNacimiento: '2000-05-20',
      rol,
      estado: 'ACTIVA',
      fechaRegistro: '2026-09-14T10:00:00',
    },
  };
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

class CatalogoFalso {
  eventoFalso = new RecursoFalso<EventoDelCatalogo>();
  idPedido: Signal<number | null> | null = null;

  evento(idEvento: Signal<number | null>) {
    this.idPedido = idEvento;
    return this.eventoFalso;
  }

  urlDeImagen(idEvento: number): string {
    return `/api/catalogo/${idEvento}/imagen`;
  }
}

describe('EventoDelCatalogoPage', () => {
  let harness: RouterTestingHarness;
  let catalogo: CatalogoFalso;

  // null: el evento todavía no llegó (o falló). Sin rol, sin sesión
  async function abrir(url = '/catalogo/5', evento: EventoDelCatalogo | null = JAZZ, rol: Rol | null = null) {
    TestBed.resetTestingModule();
    localStorage.clear();
    catalogo = new CatalogoFalso();
    catalogo.eventoFalso.value.set(evento ?? undefined);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: '', children: [] },
          { path: 'catalogo/:id', component: EventoDelCatalogoPage },
        ]),
        { provide: CatalogoService, useValue: catalogo },
      ],
    });
    if (rol !== null) {
      TestBed.inject(SesionService).iniciar(sesionDe(rol));
    }
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, EventoDelCatalogoPage);
    harness.fixture.autoDetectChanges();
    await harness.fixture.whenStable();
  }

  function pantalla(): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  function texto(): string {
    return pantalla().textContent ?? '';
  }

  function fila(nombre: string): HTMLTableRowElement {
    const encontrada = Array.from(pantalla().querySelectorAll<HTMLTableRowElement>('tbody tr')).find((cada) =>
      cada.textContent?.includes(nombre),
    );
    if (encontrada === undefined) {
      throw new Error(`No hay fila de «${nombre}»`);
    }
    return encontrada;
  }

  function enlace(textoDelEnlace: string): HTMLAnchorElement | undefined {
    return Array.from(pantalla().querySelectorAll<HTMLAnchorElement>('a')).find(
      (cada) => cada.textContent?.trim() === textoDelEnlace,
    );
  }

  it('pide el evento del id de la URL', async () => {
    await abrir();

    expect(catalogo.idPedido?.()).toBe(5);
  });

  // CU-05 RF-14
  it('muestra los datos del evento', async () => {
    await abrir();

    expect(pantalla().querySelector('h1')?.textContent).toContain('Noche de jazz');
    expect(texto()).toContain('04/10/2026');
    expect(texto()).toContain('22:00–03:00');
    expect(texto()).toContain('termina al día siguiente');
    expect(texto()).toContain('Teatro Municipal');
    expect(texto()).toContain('Desde 18 años');
    expect(texto()).toContain('Hasta 4 entradas por cliente');
    expect(texto()).toContain('Venta abierta');
  });

  // Cada línea de la descripción es un párrafo; las líneas vacías no dejan huecos
  it('muestra la descripción en párrafos', async () => {
    await abrir('/catalogo/5', { ...JAZZ, descripcion: 'Cuarteto de jazz.\n\nDos sets.' });

    const parrafos = Array.from(pantalla().querySelectorAll('.descripcion p')).map((cada) => cada.textContent);
    expect(parrafos).toEqual(['Cuarteto de jazz.', 'Dos sets.']);
  });

  it('muestra la imagen grande si el evento la tiene', async () => {
    await abrir();

    const imagen = pantalla().querySelector('img')!;
    expect(imagen.getAttribute('src')).toBe('/api/catalogo/5/imagen');
    expect(imagen.getAttribute('alt')).toBe('Imagen de Noche de jazz');
    expect(imagen.getAttribute('width')).not.toBeNull();
    expect(imagen.getAttribute('height')).not.toBeNull();
  });

  it('sin imagen muestra el fondo neutro', async () => {
    await abrir('/catalogo/5', { ...JAZZ, tieneImagen: false });

    expect(pantalla().querySelector('img')).toBeNull();
    expect(pantalla().querySelector('.sin-imagen')).not.toBeNull();
  });

  // RF-15: precio en bolivianos y si quedan, sin la cantidad
  it('lista los tipos con su precio y si están disponibles', async () => {
    await abrir();

    expect(fila('General').textContent).toContain('Bs 50,00');
    expect(fila('General').textContent).toContain('Disponible');
    expect(fila('VIP').textContent).toContain('Bs 150,00');
    expect(fila('VIP').textContent).toContain('Agotado');
  });

  it('sin restricción ni límite lo dice', async () => {
    await abrir('/catalogo/5', { ...JAZZ, restriccionEdad: null, limiteCompra: null });

    expect(texto()).toContain('Sin restricción de edad');
    expect(texto()).toContain('Sin límite de entradas');
  });

  it('un evento postergado muestra su fecha original', async () => {
    await abrir('/catalogo/5', { ...JAZZ, fecha: '2026-11-01', fechaOriginal: '2026-10-04' });

    expect(texto()).toContain('01/11/2026');
    expect(texto()).toContain('Postergado · antes el 04/10/2026');
  });

  it('con la venta cerrada lo dice', async () => {
    await abrir('/catalogo/5', { ...JAZZ, ventaAbierta: false });

    expect(texto()).toContain('Venta cerrada');
    expect(texto()).not.toContain('Venta abierta');
  });

  it('sin descripción no deja un hueco', async () => {
    await abrir('/catalogo/5', { ...JAZZ, descripcion: null });

    expect(pantalla().querySelector('.descripcion')).toBeNull();
  });

  // Un activo siempre tiene tipos; si faltaran, un aviso en vez de una tabla vacía
  it('sin tipos de entrada lo avisa', async () => {
    await abrir('/catalogo/5', { ...JAZZ, tiposEntrada: [] });

    expect(pantalla().querySelector('table')).toBeNull();
    expect(texto()).toContain('Este evento todavía no tiene entradas a la venta.');
  });

  it('ofrece volver al catálogo', async () => {
    await abrir();

    expect(enlace('← Todos los eventos')?.getAttribute('href')).toBe('/');
  });

  function comprar(): HTMLAnchorElement[] {
    return Array.from(pantalla().querySelectorAll<HTMLAnchorElement>('a')).filter(
      (cada) => cada.textContent?.trim() === 'Comprar',
    );
  }

  // CU-06 RF-22 y RF-23: sin sesión el enlace es el mismo; la ruta manda a iniciar sesión
  it.each([null, 'CLIENTE'] as const)('con sesión %s ofrece comprar cada tipo disponible', async (rol) => {
    await abrir('/catalogo/5', JAZZ, rol);

    const enlaces = comprar();
    expect(enlaces).toHaveLength(1);
    expect(enlaces[0].getAttribute('href')).toBe('/catalogo/5/entradas/1');
    expect(enlaces[0].getAttribute('aria-label')).toBe('Comprar General');
    expect(fila('General').contains(enlaces[0])).toBe(true);
    expect(fila('VIP').textContent).not.toContain('Comprar');
  });

  // En el celular cada fila es un bloque: la etiqueta de cada dato sale de data-etiqueta
  it('cada celda de la tabla lleva su etiqueta para el celular', async () => {
    await abrir();

    const etiquetas = Array.from(fila('General').querySelectorAll('th, td')).map((celda) =>
      celda.getAttribute('data-etiqueta'),
    );
    expect(etiquetas).toEqual(['Tipo', 'Precio', 'Disponibilidad', null]);
  });

  it('con la venta cerrada no ofrece comprar', async () => {
    await abrir('/catalogo/5', { ...JAZZ, ventaAbierta: false }, 'CLIENTE');

    expect(comprar()).toHaveLength(0);
  });

  // RF-24: ocultarlo es usabilidad; el backend responde 403
  it.each(['ADMINISTRADOR', 'PERSONAL'] as const)('a un %s no le ofrece comprar', async (rol) => {
    await abrir('/catalogo/5', JAZZ, rol);

    expect(comprar()).toHaveLength(0);
  });

  // Basura en la URL: no llega al backend
  it.each(['/catalogo/abc', '/catalogo/0'])('%s no pide nada y dice que no lo encontró', async (url) => {
    await abrir(url, null);

    expect(catalogo.idPedido?.()).toBeNull();
    expect(texto()).toContain('No encontramos ese evento.');
    expect(enlace('Ver los eventos disponibles')?.getAttribute('href')).toBe('/');
  });

  // RF-16: un borrador, un cancelado o uno terminado responden como si no existieran
  it('un 404 del backend dice que no lo encontró', async () => {
    await abrir('/catalogo/9', null);
    catalogo.eventoFalso.error.set(
      new HttpErrorResponse({
        status: 404,
        error: { status: 404, detail: 'No existe el evento indicado.', codigo: 'EVENTO_NO_ENCONTRADO' },
      }),
    );
    await harness.fixture.whenStable();

    expect(texto()).toContain('No encontramos ese evento.');
    expect(texto()).not.toContain('Reintentar');
  });

  it('si la carga falla por otro motivo, ofrece reintentar', async () => {
    await abrir('/catalogo/5', null);
    catalogo.eventoFalso.error.set(new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') }));
    await harness.fixture.whenStable();

    expect(texto()).toContain('No pudimos conectar con el servidor');
    Array.from(pantalla().querySelectorAll('button'))
      .find((boton) => boton.textContent?.trim() === 'Reintentar')!
      .click();
    expect(catalogo.eventoFalso.reload).toHaveBeenCalled();
  });

  it('mientras carga lo dice', async () => {
    await abrir('/catalogo/5', null);

    expect(texto()).toContain('Cargando el evento…');
  });
});
