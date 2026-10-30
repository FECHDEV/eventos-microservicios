import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { OpcionesDeReporte, Reporte } from '../modelos/reporte';
import { ReportesService } from './reportes.service';

// Copias de lo que responde el backend en ReportesControllerTest
const OPCIONES: OpcionesDeReporte = {
  eventos: [{ idEvento: 5, nombre: 'Concierto', fecha: '2026-10-04', tipos: [{ idTipoEntrada: 9, nombre: 'General' }] }],
  personal: [{ idUsuario: 3, nombre: 'Pedro Soto' }],
  dominioDeCorreo: 'mail.com',
};

const REPORTE: Reporte = {
  idReporte: 12,
  tipo: 'VENTAS',
  titulo: 'Reporte de ventas',
  fechaGeneracion: '2026-09-23T14:05:00',
  criterios: [{ nombre: 'Desde', valor: '01/09/2026' }],
  resumen: {
    columnas: [
      { titulo: 'Evento', tipo: 'TEXTO' },
      { titulo: 'Monto', tipo: 'MONTO' },
    ],
    filas: [['Concierto', 300]],
    totales: ['Total', 300],
  },
  detalle: {
    columnas: [
      { titulo: 'Fecha de pago', tipo: 'FECHA_HORA' },
      { titulo: 'Cliente', tipo: 'TEXTO' },
      { titulo: 'Monto', tipo: 'MONTO' },
    ],
    filas: [['2026-09-02T10:14:00', 'Ana Rojas', 300]],
    totales: null,
  },
};

const SIN_RESULTADOS = {
  status: 404,
  detail: 'No hay datos que cumplan los criterios indicados.',
  codigo: 'REPORTE_SIN_RESULTADOS',
};

describe('ReportesService', () => {
  let servicio: ReportesService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(ReportesService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // CU-14 paso 2
  it('pide las opciones de los filtros', async () => {
    const recurso = TestBed.runInInjectionContext(() => servicio.opciones());
    TestBed.tick();

    http.expectOne({ method: 'GET', url: '/api/reportes/opciones' }).flush(OPCIONES);
    await TestBed.inject(ApplicationRef).whenStable();

    expect(recurso.value()).toEqual(OPCIONES);
  });

  // CU-14 pasos 4 y 5
  it('genera el reporte con los criterios tal cual', async () => {
    const respuesta = servicio.generar({ tipo: 'VENTAS', desde: '2026-09-01', idEvento: 5 });

    const peticion = http.expectOne({ method: 'POST', url: '/api/reportes' });
    expect(peticion.request.body).toEqual({ tipo: 'VENTAS', desde: '2026-09-01', idEvento: 5 });
    peticion.flush(REPORTE, { status: 201, statusText: 'Created' });

    await expect(respuesta).resolves.toEqual(REPORTE);
  });

  // CU-14 pasos 6 y 7
  it.each([
    ['pdf', '/api/reportes/12/pdf'],
    ['excel', '/api/reportes/12/excel'],
  ] as const)('exporta en %s como archivo', async (formato, url) => {
    const respuesta = servicio.exportar(12, formato);

    const peticion = http.expectOne({ method: 'GET', url });
    expect(peticion.request.responseType).toBe('blob');
    const archivo = new Blob(['%PDF-']);
    peticion.flush(archivo);

    await expect(respuesta).resolves.toBe(archivo);
  });

  // FA-02
  it('sin resultados rechaza con su código', async () => {
    const respuesta = servicio.generar({ tipo: 'VENTAS' });

    http.expectOne('/api/reportes').flush(SIN_RESULTADOS, { status: 404, statusText: 'Not Found' });

    await expect(respuesta).rejects.toMatchObject({ codigo: 'REPORTE_SIN_RESULTADOS' });
  });

  it('sin red rechaza con SIN_CONEXION', async () => {
    const respuesta = servicio.generar({ tipo: 'VENTAS' });

    http.expectOne('/api/reportes').error(new ProgressEvent('error'));

    await expect(respuesta).rejects.toMatchObject({ codigo: 'SIN_CONEXION' });
  });
});
