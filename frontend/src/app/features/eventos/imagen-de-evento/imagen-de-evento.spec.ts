import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { ImagenDeEvento } from './imagen-de-evento';

const PNG = new Blob([new Uint8Array([0x89, 0x50, 0x4e, 0x47])], { type: 'image/png' });

describe('ImagenDeEvento', () => {
  let fixture: ComponentFixture<ImagenDeEvento>;
  let http: HttpTestingController;
  let creadas: string[];
  let revocadas: string[];

  beforeEach(() => {
    creadas = [];
    revocadas = [];
    let siguiente = 0;
    vi.spyOn(URL, 'createObjectURL').mockImplementation(() => {
      const url = `blob:prueba-${++siguiente}`;
      creadas.push(url);
      return url;
    });
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation((url: string) => {
      revocadas.push(url);
    });
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  function crear(idEvento: number, tieneImagen: boolean, descripcion = '') {
    fixture = TestBed.createComponent(ImagenDeEvento);
    fixture.componentRef.setInput('idEvento', idEvento);
    fixture.componentRef.setInput('tieneImagen', tieneImagen);
    fixture.componentRef.setInput('descripcion', descripcion);
    // Detección manual: la petición de la imagen queda abierta hasta que el test
    // la responde, así que no se puede esperar a que la aplicación esté estable,
    // y con autoDetectChanges los ticks de zone.js se pisan con los del test
    fixture.detectChanges();
  }

  // Después de responder la petición: espera a que termine y vuelve a pintar
  async function alResponder() {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function imagen(): HTMLImageElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector('img');
  }

  // Un evento sin imagen no pide nada: queda el fondo neutro
  it('sin imagen no la pide ni muestra nada', async () => {
    crear(5, false);

    http.expectNone(() => true);
    expect(imagen()).toBeNull();
  });

  // El <img> no manda el token: la imagen se trae como Blob y se muestra con
  // una URL de objeto
  it('con imagen la pide y la muestra desde una URL de objeto', async () => {
    crear(5, true, 'Imagen de Noche de jazz');

    http.expectOne('/api/eventos/5/imagen').flush(PNG);
    await alResponder();

    expect(imagen()?.getAttribute('src')).toBe('blob:prueba-1');
    expect(imagen()?.getAttribute('alt')).toBe('Imagen de Noche de jazz');
  });

  // En la lista el nombre ya está en la fila: la miniatura es decorativa
  it('sin descripción es decorativa', async () => {
    crear(5, true);

    http.expectOne('/api/eventos/5/imagen').flush(PNG);
    await alResponder();

    expect(imagen()?.getAttribute('alt')).toBe('');
  });

  // Otro administrador pudo quitarla: el 404 deja el fondo neutro, sin error
  it('si la imagen ya no está, queda el fondo neutro', async () => {
    crear(5, true);

    // La petición espera un Blob: el cuerpo del error también llega como Blob
    const problema = new Blob(['{"status":404,"codigo":"IMAGEN_NO_ENCONTRADA"}'], {
      type: 'application/problem+json',
    });
    http.expectOne('/api/eventos/5/imagen').flush(problema, { status: 404, statusText: 'Not Found' });
    await alResponder();

    expect(imagen()).toBeNull();
    expect(creadas).toEqual([]);
  });

  // Pasar a otro evento no deja la imagen anterior ni su URL sin liberar
  it('al cambiar de evento libera la URL anterior', async () => {
    crear(5, true);
    http.expectOne('/api/eventos/5/imagen').flush(PNG);
    await alResponder();

    fixture.componentRef.setInput('idEvento', 6);
    fixture.detectChanges();

    expect(revocadas).toEqual(['blob:prueba-1']);
    http.expectOne('/api/eventos/6/imagen').flush(PNG);
    await alResponder();
    expect(imagen()?.getAttribute('src')).toBe('blob:prueba-2');
  });

  // Reemplazar la imagen no cambia el id ni tieneImagen: la versión es la que
  // avisa que hay que volver a pedirla
  it('una versión nueva vuelve a pedir la imagen', async () => {
    crear(5, true);
    http.expectOne('/api/eventos/5/imagen').flush(PNG);
    await alResponder();

    fixture.componentRef.setInput('version', 1);
    fixture.detectChanges();

    // Cada respuesta del servidor trae un Blob nuevo
    const otra = new Blob([new Uint8Array([0xff, 0xd8, 0xff])], { type: 'image/jpeg' });
    http.expectOne('/api/eventos/5/imagen').flush(otra);
    await alResponder();
    expect(imagen()?.getAttribute('src')).toBe('blob:prueba-2');
    expect(revocadas).toEqual(['blob:prueba-1']);
  });

  it('al destruirse libera la URL', async () => {
    crear(5, true);
    http.expectOne('/api/eventos/5/imagen').flush(PNG);
    await alResponder();

    fixture.destroy();

    expect(revocadas).toEqual(['blob:prueba-1']);
  });
});
