import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { VisitasService } from './visitas.service';

// decisiones.md → «Contador de visitas por página»
describe('VisitasService', () => {
  let servicio: VisitasService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(VisitasService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('suma una visita a la página y devuelve su total', async () => {
    const total = firstValueFrom(servicio.registrar('DETALLE_DE_EVENTO'));

    const peticion = http.expectOne({ method: 'POST', url: '/api/visitas/DETALLE_DE_EVENTO' });
    expect(peticion.request.body).toBeNull();
    peticion.flush({ total: 1234 });

    expect(await total).toBe(1234);
  });
});
