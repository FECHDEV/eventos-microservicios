import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationRef, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { DevolucionesDelEvento, EventoCancelado } from '../modelos/devolucion';
import { DevolucionesService } from './devoluciones.service';

// Copias de lo que responde el backend en DevolucionesControllerTest
const CONCIERTO: EventoCancelado = {
  idEvento: 5,
  nombre: 'Concierto',
  fecha: '2026-10-04',
  horaInicio: '22:00:00',
  horaFin: '03:00:00',
  lugar: 'Teatro Municipal',
  pendientes: 2,
};

const DEVOLUCIONES: DevolucionesDelEvento = {
  idEvento: 5,
  nombre: 'Concierto',
  fecha: '2026-10-04',
  horaInicio: '22:00:00',
  horaFin: '03:00:00',
  lugar: 'Teatro Municipal',
  pendientes: [
    {
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
    },
  ],
  devueltas: [],
};

const YA_DEVUELTA = {
  status: 409,
  detail: 'No quedan entradas pendientes de devolución en lo indicado.',
  codigo: 'ENTRADA_YA_DEVUELTA',
};

describe('DevolucionesService', () => {
  let servicio: DevolucionesService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(DevolucionesService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // CU-13 paso 2
  it('pide los eventos cancelados', async () => {
    const recurso = TestBed.runInInjectionContext(() => servicio.eventosCancelados());
    TestBed.tick();

    http.expectOne({ method: 'GET', url: '/api/devoluciones' }).flush([CONCIERTO]);
    await TestBed.inject(ApplicationRef).whenStable();

    expect(recurso.value()).toEqual([CONCIERTO]);
  });

  // CU-13 paso 4
  it('pide las devoluciones de un evento', async () => {
    const recurso = TestBed.runInInjectionContext(() => servicio.delEvento(signal(5)));
    TestBed.tick();

    http.expectOne({ method: 'GET', url: '/api/devoluciones/5' }).flush(DEVOLUCIONES);
    await TestBed.inject(ApplicationRef).whenStable();

    expect(recurso.value()).toEqual(DEVOLUCIONES);
  });

  it('sin id no pide el evento', () => {
    TestBed.runInInjectionContext(() => servicio.delEvento(signal(null)));
    TestBed.tick();

    http.expectNone((pedido) => pedido.url.startsWith('/api/devoluciones'));
  });

  // CU-13 RF-9 y RF-10
  it('registra la devolución de una compra', async () => {
    const respuesta = servicio.registrarCompra(40);

    const peticion = http.expectOne({ method: 'POST', url: '/api/devoluciones/compras/40' });
    expect(peticion.request.body).toBeNull();
    peticion.flush({ registradas: 2 });

    await expect(respuesta).resolves.toEqual({ registradas: 2 });
  });

  it('registra la devolución de una entrada', async () => {
    const respuesta = servicio.registrarEntrada(81);

    http.expectOne({ method: 'POST', url: '/api/devoluciones/entradas/81' }).flush({ registradas: 1 });

    await expect(respuesta).resolves.toEqual({ registradas: 1 });
  });

  // FA-02
  it('un 409 rechaza con su código', async () => {
    const respuesta = servicio.registrarCompra(40);

    http.expectOne('/api/devoluciones/compras/40').flush(YA_DEVUELTA, { status: 409, statusText: 'Conflict' });

    await expect(respuesta).rejects.toMatchObject({ codigo: 'ENTRADA_YA_DEVUELTA' });
  });

  it('sin red rechaza con SIN_CONEXION', async () => {
    const respuesta = servicio.registrarEntrada(81);

    http.expectOne('/api/devoluciones/entradas/81').error(new ProgressEvent('error'));

    await expect(respuesta).rejects.toMatchObject({ codigo: 'SIN_CONEXION' });
  });
});
