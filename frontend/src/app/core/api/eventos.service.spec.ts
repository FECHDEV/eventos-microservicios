import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationRef, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { ErrorApi } from '../modelos/error-api';
import { DatosDeEvento, Evento, EventoResumen } from '../modelos/evento';
import { Pagina } from '../modelos/pagina';
import { DatosDeTipoEntrada, TipoEntrada } from '../modelos/tipo-entrada';
import { EventosService } from './eventos.service';

// Copias de lo que responde el backend en EventosControllerTest y TiposEntradaControllerTest
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

const JAZZ_EN_LA_LISTA: EventoResumen = {
  idEvento: 5,
  nombre: 'Noche de jazz',
  fecha: '2026-10-04',
  horaInicio: '22:00:00',
  horaFin: '03:00:00',
  lugar: 'Teatro Municipal',
  estado: 'BORRADOR',
  ventaAbierta: false,
  tieneImagen: false,
};

const PAGINA: Pagina<EventoResumen> = {
  contenido: [JAZZ_EN_LA_LISTA],
  pagina: 0,
  tamano: 20,
  totalElementos: 21,
  totalPaginas: 2,
};

const DATOS: DatosDeEvento = {
  nombre: 'Noche de jazz',
  descripcion: 'Cuarteto',
  fecha: '2026-10-04',
  horaInicio: '22:00',
  horaFin: '03:00',
  lugar: 'Teatro Municipal',
  restriccionEdad: 18,
  limiteCompra: 4,
};

const GENERAL: TipoEntrada = {
  idTipoEntrada: 3,
  nombre: 'General',
  precio: 50,
  cantidadTotal: 100,
  cantidadDisponible: 70,
};

const DATOS_DE_TIPO: DatosDeTipoEntrada = { nombre: 'General', precio: 50, cantidadTotal: 100 };

const CANCELADO: ErrorApi = {
  status: 409,
  detail: 'Un evento cancelado no se puede modificar.',
  codigo: 'EVENTO_CANCELADO',
};

describe('EventosService', () => {
  let servicio: EventosService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(EventosService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function estable() {
    await TestBed.inject(ApplicationRef).whenStable();
  }

  describe('lecturas', () => {
    // CU-10 RF-4: la primera página sin parámetros de más
    it('pide la lista sin filtros ni página cuando no hacen falta', async () => {
      const recurso = TestBed.runInInjectionContext(() =>
        servicio.listado(signal({ estado: null, pagina: 0 })),
      );
      TestBed.tick();

      const peticion = http.expectOne((pedido) => pedido.url === '/api/eventos');
      expect(peticion.request.method).toBe('GET');
      expect(peticion.request.params.keys()).toEqual([]);
      peticion.flush(PAGINA);
      await estable();

      expect(recurso.value()).toEqual(PAGINA);
    });

    // CU-10 RF-5: el estado y la página viajan como parámetros
    it('envía el estado y la página', async () => {
      TestBed.runInInjectionContext(() => servicio.listado(signal({ estado: 'ACTIVO', pagina: 2 })));
      TestBed.tick();

      const peticion = http.expectOne((pedido) => pedido.url === '/api/eventos');
      expect(peticion.request.params.get('estado')).toBe('ACTIVO');
      expect(peticion.request.params.get('pagina')).toBe('2');
      peticion.flush(PAGINA);
      await estable();
    });

    it('pide el detalle del evento', async () => {
      const recurso = TestBed.runInInjectionContext(() => servicio.evento(signal(5)));
      TestBed.tick();

      http.expectOne({ method: 'GET', url: '/api/eventos/5' }).flush(JAZZ);
      await estable();

      expect(recurso.value()).toEqual(JAZZ);
    });

    // Un id que no es válido en la URL no llega al backend
    it('sin id no pide el evento ni sus tipos', () => {
      TestBed.runInInjectionContext(() => {
        servicio.evento(signal(null));
        servicio.tipos(signal(null));
        servicio.imagen(signal(null));
      });
      TestBed.tick();

      http.expectNone(() => true);
    });

    it('pide los tipos de entrada del evento', async () => {
      const recurso = TestBed.runInInjectionContext(() => servicio.tipos(signal(5)));
      TestBed.tick();

      http.expectOne({ method: 'GET', url: '/api/eventos/5/tipos-entrada' }).flush([GENERAL]);
      await estable();

      expect(recurso.value()).toEqual([GENERAL]);
    });

    // El <img> no puede mandar el token: la imagen se trae como Blob
    it('pide la imagen como Blob', async () => {
      const recurso = TestBed.runInInjectionContext(() => servicio.imagen(signal(5)));
      TestBed.tick();

      const peticion = http.expectOne({ method: 'GET', url: '/api/eventos/5/imagen' });
      expect(peticion.request.responseType).toBe('blob');
      const png = new Blob([new Uint8Array([0x89, 0x50, 0x4e, 0x47])], { type: 'image/png' });
      peticion.flush(png);
      await estable();

      expect(recurso.value()).toBe(png);
    });
  });

  describe('escrituras del evento', () => {
    it('registra con los datos del formulario', async () => {
      const respuesta = servicio.registrar(DATOS);

      const peticion = http.expectOne({ method: 'POST', url: '/api/eventos' });
      expect(peticion.request.body).toEqual(DATOS);
      peticion.flush(JAZZ, { status: 201, statusText: 'Created' });

      await expect(respuesta).resolves.toEqual(JAZZ);
    });

    it('modifica el evento', async () => {
      const respuesta = servicio.modificar(5, DATOS);

      const peticion = http.expectOne({ method: 'PUT', url: '/api/eventos/5' });
      expect(peticion.request.body).toEqual(DATOS);
      peticion.flush(JAZZ);

      await expect(respuesta).resolves.toEqual(JAZZ);
    });

    it('un rechazo del backend llega como ErrorApi', async () => {
      const respuesta = servicio.modificar(5, DATOS);

      http.expectOne('/api/eventos/5').flush(CANCELADO, { status: 409, statusText: 'Conflict' });

      await expect(respuesta).rejects.toEqual(CANCELADO);
    });

    it('sin conexión el error es SIN_CONEXION', async () => {
      const respuesta = servicio.registrar(DATOS);

      http.expectOne('/api/eventos').error(new ProgressEvent('error'));

      await expect(respuesta).rejects.toMatchObject({ codigo: 'SIN_CONEXION' });
    });

    // CU-10 RF-22: multipart, con la parte que espera el controlador
    it('sube la imagen como multipart en la parte imagen', async () => {
      const archivo = new File([new Uint8Array([0xff, 0xd8, 0xff])], 'foto.jpg', { type: 'image/jpeg' });
      const respuesta = servicio.subirImagen(5, archivo);

      const peticion = http.expectOne({ method: 'POST', url: '/api/eventos/5/imagen' });
      expect(peticion.request.body).toBeInstanceOf(FormData);
      expect((peticion.request.body as FormData).get('imagen')).toBe(archivo);
      peticion.flush({ ...JAZZ, tieneImagen: true });

      await expect(respuesta).resolves.toMatchObject({ tieneImagen: true });
    });

    it('quita la imagen', async () => {
      const respuesta = servicio.quitarImagen(5);

      http.expectOne({ method: 'DELETE', url: '/api/eventos/5/imagen' }).flush(JAZZ);

      await expect(respuesta).resolves.toEqual(JAZZ);
    });
  });

  describe('tipos de entrada', () => {
    it('registra un tipo', async () => {
      const respuesta = servicio.registrarTipo(5, DATOS_DE_TIPO);

      const peticion = http.expectOne({ method: 'POST', url: '/api/eventos/5/tipos-entrada' });
      expect(peticion.request.body).toEqual(DATOS_DE_TIPO);
      peticion.flush(GENERAL, { status: 201, statusText: 'Created' });

      await expect(respuesta).resolves.toEqual(GENERAL);
    });

    it('modifica un tipo', async () => {
      const respuesta = servicio.modificarTipo(5, 3, DATOS_DE_TIPO);

      const peticion = http.expectOne({ method: 'PUT', url: '/api/eventos/5/tipos-entrada/3' });
      expect(peticion.request.body).toEqual(DATOS_DE_TIPO);
      peticion.flush(GENERAL);

      await expect(respuesta).resolves.toEqual(GENERAL);
    });
  });

  describe('estado del evento', () => {
    // CU-12: las cuatro operaciones devuelven el evento actualizado
    it.each([
      ['activar', 'activacion'],
      ['cancelar', 'cancelacion'],
      ['cerrarVenta', 'cierre-de-venta'],
    ] as const)('%s va a /%s', async (operacion, ruta) => {
      const respuesta = servicio[operacion](5);

      http.expectOne({ method: 'POST', url: `/api/eventos/5/${ruta}` }).flush(JAZZ);

      await expect(respuesta).resolves.toEqual(JAZZ);
    });

    // CU-12 FA-02: la activación dice además cuántas entradas usadas reactivó
    it('activar devuelve también las entradas reactivadas', async () => {
      const respuesta = servicio.activar(5);

      http.expectOne({ method: 'POST', url: '/api/eventos/5/activacion' }).flush({ ...JAZZ, entradasReactivadas: 2 });

      await expect(respuesta).resolves.toEqual({ ...JAZZ, entradasReactivadas: 2 });
    });

    it('posterga con la fecha nueva', async () => {
      const respuesta = servicio.postergar(5, '2026-11-01');

      const peticion = http.expectOne({ method: 'POST', url: '/api/eventos/5/postergacion' });
      expect(peticion.request.body).toEqual({ fecha: '2026-11-01' });
      peticion.flush({ ...JAZZ, estado: 'POSTERGADO', fecha: '2026-11-01', fechaOriginal: '2026-10-04' });

      await expect(respuesta).resolves.toMatchObject({ estado: 'POSTERGADO', fechaOriginal: '2026-10-04' });
    });
  });
});
