import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationRef, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { Pago } from '../modelos/pago';
import { Reserva } from '../modelos/reserva';
import { ReservasService } from './reservas.service';

// Copia de lo que responde el backend en ReservasControllerTest
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

// Copia de lo que responde el backend en PagosControllerTest
const PAGO: Pago = {
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

describe('ReservasService', () => {
  let servicio: ReservasService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(ReservasService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // CU-06 paso 1: un solo tipo y una cantidad; el cliente sale del token
  it('reserva con el tipo y la cantidad', async () => {
    const respuesta = servicio.reservar({ idTipoEntrada: 3, cantidad: 3 });

    const peticion = http.expectOne({ method: 'POST', url: '/api/reservas' });
    expect(peticion.request.body).toEqual({ idTipoEntrada: 3, cantidad: 3 });
    peticion.flush(RESERVA, { status: 201, statusText: 'Created' });

    await expect(respuesta).resolves.toEqual(RESERVA);
  });

  // FA-03: el rechazo trae cuántas puede agregar todavía
  it('un rechazo llega con su código y sus datos', async () => {
    const respuesta = servicio.reservar({ idTipoEntrada: 3, cantidad: 3 });

    http.expectOne('/api/reservas').flush(
      {
        status: 409,
        detail: 'El límite es de 4 entradas por cliente para este evento; puede agregar 1 más.',
        codigo: 'LIMITE_COMPRA_EXCEDIDO',
        limite: 4,
        puedeAgregar: 1,
      },
      { status: 409, statusText: 'Conflict' },
    );

    await expect(respuesta).rejects.toMatchObject({ codigo: 'LIMITE_COMPRA_EXCEDIDO', limite: 4, puedeAgregar: 1 });
  });

  it('sin conexión el error es SIN_CONEXION', async () => {
    const respuesta = servicio.reservar({ idTipoEntrada: 3, cantidad: 1 });

    http.expectOne('/api/reservas').error(new ProgressEvent('error'));

    await expect(respuesta).rejects.toMatchObject({ codigo: 'SIN_CONEXION' });
  });

  it('pide la reserva', async () => {
    const recurso = TestBed.runInInjectionContext(() => servicio.reserva(signal(40)));
    TestBed.tick();

    http.expectOne({ method: 'GET', url: '/api/reservas/40' }).flush(RESERVA);
    await TestBed.inject(ApplicationRef).whenStable();

    expect(recurso.value()).toEqual(RESERVA);
  });

  // CU-07 paso 1: genera el cobro, sin cuerpo
  it('pagar pide el cobro de la reserva', async () => {
    const respuesta = servicio.pagar(40);

    const peticion = http.expectOne({ method: 'POST', url: '/api/reservas/40/pago' });
    expect(peticion.request.body).toBeNull();
    peticion.flush(PAGO);

    await expect(respuesta).resolves.toEqual(PAGO);
  });

  it('consultar el pago pregunta al backend cómo va', async () => {
    const respuesta = servicio.consultarPago(40);

    http.expectOne({ method: 'GET', url: '/api/reservas/40/pago' }).flush({ ...PAGO, estado: 'PAGADO', entradas: 3 });

    await expect(respuesta).resolves.toMatchObject({ estado: 'PAGADO', entradas: 3 });
  });

  it('simular manda el resultado elegido', async () => {
    const respuesta = servicio.simularPago(40, 'ERROR');

    const peticion = http.expectOne({ method: 'POST', url: '/api/reservas/40/pago/simulacion' });
    expect(peticion.request.body).toEqual({ resultado: 'ERROR' });
    peticion.flush(null, { status: 204, statusText: 'No Content' });

    await expect(respuesta).resolves.toBeUndefined();
  });

  // FA-01: el cobro no se pudo generar
  it('un 502 al pagar llega como ERROR_SISTEMA_PAGO', async () => {
    const respuesta = servicio.pagar(40);

    http.expectOne('/api/reservas/40/pago').flush(
      { status: 502, detail: 'No se pudo generar el cobro.', codigo: 'ERROR_SISTEMA_PAGO' },
      { status: 502, statusText: 'Bad Gateway' },
    );

    await expect(respuesta).rejects.toMatchObject({ codigo: 'ERROR_SISTEMA_PAGO' });
  });

  it('sin conexión, consultar el pago falla con SIN_CONEXION', async () => {
    const respuesta = servicio.consultarPago(40);

    http.expectOne('/api/reservas/40/pago').error(new ProgressEvent('error'));

    await expect(respuesta).rejects.toMatchObject({ codigo: 'SIN_CONEXION' });
  });

  it('un rechazo al simular llega con su código', async () => {
    const respuesta = servicio.simularPago(40, 'PAGADO');

    http.expectOne('/api/reservas/40/pago/simulacion').flush(
      { status: 404, detail: 'No existe.', codigo: 'PAGO_NO_ENCONTRADO' },
      { status: 404, statusText: 'Not Found' },
    );

    await expect(respuesta).rejects.toMatchObject({ codigo: 'PAGO_NO_ENCONTRADO' });
  });

  it('sin id no pide la reserva', () => {
    TestBed.runInInjectionContext(() => servicio.reserva(signal(null)));
    TestBed.tick();

    http.expectNone((pedido) => pedido.url.startsWith('/api/reservas'));
  });
});
