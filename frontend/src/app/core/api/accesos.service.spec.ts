import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { EventoParaValidar } from '../modelos/acceso';
import { AccesosService } from './accesos.service';

// Copia de lo que responde el backend en AccesosControllerTest
const JAZZ: EventoParaValidar = {
  idEvento: 5,
  nombre: 'Noche de jazz',
  fecha: '2026-10-04',
  horaInicio: '22:00:00',
  horaFin: '03:00:00',
  lugar: 'Teatro Municipal',
};

describe('AccesosService', () => {
  let servicio: AccesosService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(AccesosService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // CU-15 RF-2
  it('pide los eventos para validar', async () => {
    const recurso = TestBed.runInInjectionContext(() => servicio.eventosParaValidar());
    TestBed.tick();

    http.expectOne({ method: 'GET', url: '/api/accesos/eventos' }).flush([JAZZ]);
    await TestBed.inject(ApplicationRef).whenStable();

    expect(recurso.value()).toEqual([JAZZ]);
  });

  // CU-15 paso 1: el código y el evento elegido
  it('valida un código contra el evento', async () => {
    const respuesta = servicio.validar(5, 'codigo-81');

    const peticion = http.expectOne({ method: 'POST', url: '/api/accesos' });
    expect(peticion.request.body).toEqual({ idEvento: 5, codigo: 'codigo-81' });
    peticion.flush({ resultado: 'VALIDA', tipoEntrada: 'General' });

    await expect(respuesta).resolves.toEqual({ resultado: 'VALIDA', tipoEntrada: 'General' });
  });

  // RF-4
  it('un 409 rechaza con su código', async () => {
    const respuesta = servicio.validar(5, 'codigo-81');

    http
      .expectOne('/api/accesos')
      .flush(
        { status: 409, detail: 'El evento no admite validar entradas ahora.', codigo: 'EVENTO_NO_DISPONIBLE' },
        { status: 409, statusText: 'Conflict' },
      );

    await expect(respuesta).rejects.toMatchObject({ codigo: 'EVENTO_NO_DISPONIBLE' });
  });

  it('sin red rechaza con SIN_CONEXION', async () => {
    const respuesta = servicio.validar(5, 'codigo-81');

    http.expectOne('/api/accesos').error(new ProgressEvent('error'));

    await expect(respuesta).rejects.toMatchObject({ codigo: 'SIN_CONEXION' });
  });
});
