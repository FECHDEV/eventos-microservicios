import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationRef, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { Entrada, EntradasDelCliente } from '../modelos/entrada';
import { EntradasService } from './entradas.service';

// Copia de lo que responde el backend en EntradasControllerTest
const ENTRADAS: EntradasDelCliente = {
  proximas: [
    {
      idReserva: 40,
      idEvento: 5,
      nombreEvento: 'Noche de jazz',
      fecha: '2026-10-04',
      horaInicio: '22:00:00',
      horaFin: '03:00:00',
      lugar: 'Teatro Municipal',
      estadoEvento: 'ACTIVO',
      tipoEntrada: 'General',
      cantidad: 2,
      entradas: [
        { idEntrada: 81, numero: 1, estado: 'ACTIVA' },
        { idEntrada: 82, numero: 2, estado: 'UTILIZADA' },
      ],
    },
  ],
  pasadas: [],
  canceladas: [],
};

const ENTRADA: Entrada = {
  idEntrada: 81,
  numero: 1,
  cantidad: 2,
  estado: 'ACTIVA',
  fechaEmision: '2026-09-22T20:00:00',
  codigoQr: 'codigo-81',
  idEvento: 5,
  nombreEvento: 'Noche de jazz',
  fecha: '2026-10-04',
  horaInicio: '22:00:00',
  horaFin: '03:00:00',
  lugar: 'Teatro Municipal',
  estadoEvento: 'ACTIVO',
  tipoEntrada: 'General',
};

describe('EntradasService', () => {
  let servicio: EntradasService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(EntradasService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // CU-08 paso 2
  it('pide las entradas del Cliente', async () => {
    const recurso = TestBed.runInInjectionContext(() => servicio.entradas());
    TestBed.tick();

    http.expectOne({ method: 'GET', url: '/api/entradas' }).flush(ENTRADAS);
    await TestBed.inject(ApplicationRef).whenStable();

    expect(recurso.value()).toEqual(ENTRADAS);
  });

  // CU-08 paso 4
  it('pide una entrada por su id', async () => {
    const recurso = TestBed.runInInjectionContext(() => servicio.entrada(signal(81)));
    TestBed.tick();

    http.expectOne({ method: 'GET', url: '/api/entradas/81' }).flush(ENTRADA);
    await TestBed.inject(ApplicationRef).whenStable();

    expect(recurso.value()).toEqual(ENTRADA);
  });

  it('sin id no pide la entrada', () => {
    TestBed.runInInjectionContext(() => servicio.entrada(signal(null)));
    TestBed.tick();

    http.expectNone((pedido) => pedido.url.startsWith('/api/entradas'));
  });
});
