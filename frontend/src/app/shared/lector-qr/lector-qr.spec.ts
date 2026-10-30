import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { LEER_CUADRO_QR, LectorQr } from './lector-qr';

// Una pista de video falsa: la cámara se apaga llamando a stop()
function streamFalso() {
  const pista = { stop: vi.fn() };
  return { pista, stream: { getTracks: () => [pista] } as unknown as MediaStream };
}

function conCamara(obtener: () => Promise<MediaStream>) {
  Object.defineProperty(navigator, 'mediaDevices', {
    value: { getUserMedia: vi.fn(obtener) },
    configurable: true,
  });
}

function sinCamara() {
  Object.defineProperty(navigator, 'mediaDevices', { value: undefined, configurable: true });
}

// Deja correr unos cuadros de requestAnimationFrame
async function esperarCuadros() {
  await new Promise((seguir) => setTimeout(seguir, 60));
}

describe('LectorQr', () => {
  let leerCuadro: ReturnType<typeof vi.fn<(video: HTMLVideoElement) => string | null>>;

  beforeEach(() => {
    leerCuadro = vi.fn<(video: HTMLVideoElement) => string | null>(() => null);
    // jsdom no reproduce video
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue(undefined);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    sinCamara();
  });

  async function crear(): Promise<{ fijo: ComponentFixture<LectorQr>; leidos: string[] }> {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [{ provide: LEER_CUADRO_QR, useValue: leerCuadro }] });
    const fijo = TestBed.createComponent(LectorQr);
    const leidos: string[] = [];
    fijo.componentInstance.leido.subscribe((codigo) => leidos.push(codigo));
    fijo.autoDetectChanges();
    await fijo.whenStable();
    await esperarCuadros();
    return { fijo, leidos };
  }

  function texto(fijo: ComponentFixture<LectorQr>): string {
    return fijo.nativeElement.textContent ?? '';
  }

  // RF-13
  it('sin cámara lo dice y deja escribir el código', async () => {
    sinCamara();

    const { fijo, leidos } = await crear();

    expect(texto(fijo)).toContain('No pudimos usar la cámara. Escribe el código de la entrada.');
    expect(fijo.nativeElement.querySelector('video')).toBeNull();
    expect(leidos).toEqual([]);
  });

  it('con el permiso denegado dice lo mismo', async () => {
    conCamara(() => Promise.reject(new DOMException('denegado', 'NotAllowedError')));

    const { fijo } = await crear();

    expect(texto(fijo)).toContain('No pudimos usar la cámara.');
  });

  it('pide la cámara trasera y la muestra', async () => {
    const { stream } = streamFalso();
    conCamara(() => Promise.resolve(stream));

    const { fijo } = await crear();

    expect(navigator.mediaDevices.getUserMedia).toHaveBeenCalledWith({
      video: { facingMode: 'environment' },
      audio: false,
    });
    const video: HTMLVideoElement = fijo.nativeElement.querySelector('video');
    expect(video.srcObject).toBe(stream);
    expect(video.getAttribute('aria-label')).toBe('Cámara para leer el código QR');
  });

  it('cuando un cuadro tiene un QR, emite su texto', async () => {
    conCamara(() => Promise.resolve(streamFalso().stream));
    leerCuadro.mockReturnValue('codigo-81');

    const { leidos } = await crear();

    expect(leidos.length).toBeGreaterThan(0);
    expect(new Set(leidos)).toEqual(new Set(['codigo-81']));
  });

  it('sin QR en el cuadro no emite nada', async () => {
    conCamara(() => Promise.resolve(streamFalso().stream));

    const { leidos } = await crear();

    expect(leerCuadro).toHaveBeenCalled();
    expect(leidos).toEqual([]);
  });

  // Algunos navegadores no dejan reproducir el video: la cámara no queda prendida sin mostrarse
  it('si el video no se puede reproducir, apaga la cámara y lo dice', async () => {
    const { pista, stream } = streamFalso();
    conCamara(() => Promise.resolve(stream));
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockRejectedValue(new DOMException('bloqueado', 'NotAllowedError'));

    const { fijo } = await crear();

    expect(pista.stop).toHaveBeenCalled();
    expect(texto(fijo)).toContain('No pudimos usar la cámara.');
  });

  // Decodificar cada cuadro a 60 por segundo calienta el celular: con 10 alcanza
  it('no lee más de 10 cuadros por segundo', async () => {
    conCamara(() => Promise.resolve(streamFalso().stream));
    const { fijo } = await crear();
    const antes = leerCuadro.mock.calls.length;

    await new Promise((seguir) => setTimeout(seguir, 500));

    expect(leerCuadro.mock.calls.length - antes).toBeLessThanOrEqual(6);
    fijo.destroy();
  });

  it('al salir apaga la cámara y deja de leer', async () => {
    const { pista, stream } = streamFalso();
    conCamara(() => Promise.resolve(stream));
    const { fijo } = await crear();

    fijo.destroy();
    const lecturas = leerCuadro.mock.calls.length;
    await esperarCuadros();

    expect(pista.stop).toHaveBeenCalled();
    expect(leerCuadro.mock.calls.length).toBe(lecturas);
  });
});
