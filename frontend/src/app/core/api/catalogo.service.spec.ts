import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationRef, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { EventoDelCatalogo, EventoDelCatalogoResumen, FiltrosDelCatalogo } from '../modelos/catalogo';
import { Pagina } from '../modelos/pagina';
import { CatalogoService } from './catalogo.service';

// Copias de lo que responde el backend en CatalogoControllerTest
const JAZZ_EN_EL_CATALOGO: EventoDelCatalogoResumen = {
  idEvento: 5,
  nombre: 'Noche de jazz',
  fecha: '2026-10-04',
  fechaOriginal: null,
  horaInicio: '22:00:00',
  horaFin: '03:00:00',
  lugar: 'Teatro Municipal',
  ventaAbierta: true,
  tieneImagen: false,
};

const PAGINA: Pagina<EventoDelCatalogoResumen> = {
  contenido: [JAZZ_EN_EL_CATALOGO],
  pagina: 0,
  tamano: 12,
  totalElementos: 13,
  totalPaginas: 2,
};

const JAZZ: EventoDelCatalogo = {
  ...JAZZ_EN_EL_CATALOGO,
  descripcion: 'Cuarteto',
  restriccionEdad: 18,
  limiteCompra: 4,
  tiposEntrada: [
    { idTipoEntrada: 1, nombre: 'General', precio: 50, disponible: true },
    { idTipoEntrada: 2, nombre: 'VIP', precio: 150, disponible: false },
  ],
};

const SIN_FILTROS: FiltrosDelCatalogo = { texto: null, desde: null, hasta: null, pagina: 0 };

describe('CatalogoService', () => {
  let servicio: CatalogoService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(CatalogoService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function estable() {
    await TestBed.inject(ApplicationRef).whenStable();
  }

  function pedirListado(filtros: FiltrosDelCatalogo) {
    const recurso = TestBed.runInInjectionContext(() => servicio.listado(signal(filtros)));
    TestBed.tick();
    return { recurso, peticion: http.expectOne((pedido) => pedido.url === '/api/catalogo') };
  }

  // CU-05 RF-5: la primera página sin parámetros de más
  it('pide el catálogo sin parámetros cuando no hay filtros', async () => {
    const { recurso, peticion } = pedirListado(SIN_FILTROS);

    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.params.keys()).toEqual([]);
    peticion.flush(PAGINA);
    await estable();

    expect(recurso.value()).toEqual(PAGINA);
  });

  // CU-05 RF-7 a RF-9
  it('envía el texto recortado, las fechas y la página', async () => {
    const { peticion } = pedirListado({ texto: '  jazz ', desde: '2026-10-01', hasta: '2026-10-31', pagina: 2 });

    expect(peticion.request.params.get('texto')).toBe('jazz');
    expect(peticion.request.params.get('desde')).toBe('2026-10-01');
    expect(peticion.request.params.get('hasta')).toBe('2026-10-31');
    expect(peticion.request.params.get('pagina')).toBe('2');
    peticion.flush(PAGINA);
    await estable();
  });

  // Un texto de espacios no filtra (CU-05, casos límite)
  it('un texto de espacios no viaja', async () => {
    const { peticion } = pedirListado({ ...SIN_FILTROS, texto: '   ' });

    expect(peticion.request.params.has('texto')).toBe(false);
    peticion.flush(PAGINA);
    await estable();
  });

  it('pide el detalle del evento', async () => {
    const recurso = TestBed.runInInjectionContext(() => servicio.evento(signal(5)));
    TestBed.tick();

    http.expectOne({ method: 'GET', url: '/api/catalogo/5' }).flush(JAZZ);
    await estable();

    expect(recurso.value()).toEqual(JAZZ);
  });

  it('sin id no pide el detalle', () => {
    TestBed.runInInjectionContext(() => servicio.evento(signal(null)));
    TestBed.tick();

    http.expectNone((pedido) => pedido.url.startsWith('/api/catalogo'));
  });

  // La imagen es pública: va directo en un <img>, sin token ni Blob
  it('arma la dirección de la imagen', () => {
    expect(servicio.urlDeImagen(5)).toBe('/api/catalogo/5/imagen');
  });
});
