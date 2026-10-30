import { TestBed } from '@angular/core/testing';

import {
  CLAVE_ESTILO,
  CLAVE_MODO,
  CLAVE_TEMA_ANTERIOR,
  guardarEstilo,
  guardarModo,
  leerEstilo,
  leerModo,
  milisegundosHastaElCambio,
  temaDe,
} from './almacen-tema';
import { TemaService } from './tema.service';

// decisiones.md → «Tres estilos y modo día/noche»
describe('almacén de la apariencia', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => vi.restoreAllMocks());

  it('sin nada guardado: estilo adultos y modo noche', () => {
    expect(leerEstilo()).toBe('adultos');
    expect(leerModo()).toBe('noche');
  });

  it('recupera el estilo y el modo guardados', () => {
    guardarEstilo('ninos');
    guardarModo('automatico');

    expect(leerEstilo()).toBe('ninos');
    expect(leerModo()).toBe('automatico');
    expect(localStorage.getItem(CLAVE_ESTILO)).toBe('ninos');
    expect(localStorage.getItem(CLAVE_MODO)).toBe('automatico');
  });

  it('valores desconocidos cuentan como el por defecto', () => {
    localStorage.setItem(CLAVE_ESTILO, 'rosado');
    localStorage.setItem(CLAVE_MODO, 'tarde');

    expect(leerEstilo()).toBe('adultos');
    expect(leerModo()).toBe('noche');
  });

  // Quien eligió el claro con el botón sol/luna lo conserva como «Día»
  it('el modo claro guardado antes pasa a ser Día, y guardar borra la clave vieja', () => {
    localStorage.setItem(CLAVE_TEMA_ANTERIOR, 'claro');

    expect(leerModo()).toBe('dia');

    guardarModo('dia');
    expect(localStorage.getItem(CLAVE_TEMA_ANTERIOR)).toBeNull();
  });

  it('si el almacenamiento falla, no rompe y usa los valores por defecto', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('bloqueado');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('bloqueado');
    });

    expect(() => guardarEstilo('ninos')).not.toThrow();
    expect(() => guardarModo('dia')).not.toThrow();
    expect(leerEstilo()).toBe('adultos');
    expect(leerModo()).toBe('noche');
  });

  it('en automático es de día de 6:00 a 18:59 y de noche el resto', () => {
    const a = (hora: number, minuto = 0) => temaDe('automatico', new Date(2026, 9, 12, hora, minuto));

    expect(a(5, 59)).toBe('oscuro');
    expect(a(6)).toBe('claro');
    expect(a(18, 59)).toBe('claro');
    expect(a(19)).toBe('oscuro');
    expect(a(0)).toBe('oscuro');
  });

  it('día y noche no dependen de la hora', () => {
    expect(temaDe('dia', new Date(2026, 9, 12, 23, 0))).toBe('claro');
    expect(temaDe('noche', new Date(2026, 9, 12, 12, 0))).toBe('oscuro');
  });

  it('calcula cuánto falta para el próximo cambio entre día y noche', () => {
    const minutos = (desde: Date) => milisegundosHastaElCambio(desde) / 60_000;

    expect(minutos(new Date(2026, 9, 12, 5, 30))).toBe(30);
    expect(minutos(new Date(2026, 9, 12, 18, 0))).toBe(60);
    expect(minutos(new Date(2026, 9, 12, 23, 0))).toBe(7 * 60);
  });
});

describe('TemaService', () => {
  let meta: HTMLMetaElement;
  const raiz = document.documentElement;

  beforeEach(() => {
    localStorage.clear();
    delete raiz.dataset['tema'];
    delete raiz.dataset['estilo'];
    meta = document.createElement('meta');
    meta.name = 'theme-color';
    meta.content = '#0b1020';
    document.head.appendChild(meta);
    TestBed.resetTestingModule();
  });

  afterEach(() => {
    meta.remove();
    vi.useRealTimers();
  });

  function crear(): TemaService {
    const servicio = TestBed.inject(TemaService);
    TestBed.tick();
    return servicio;
  }

  it('empieza en adultos y noche, sin marcar el documento', () => {
    const servicio = crear();

    expect(servicio.estilo()).toBe('adultos');
    expect(servicio.modo()).toBe('noche');
    expect(servicio.tema()).toBe('oscuro');
    expect(raiz.dataset['tema']).toBeUndefined();
    expect(raiz.dataset['estilo']).toBeUndefined();
    expect(meta.content).toBe('#0b1020');
  });

  it('elegir Día lo aplica al documento, cambia la barra y lo recuerda', () => {
    const servicio = crear();

    servicio.elegirModo('dia');
    TestBed.tick();

    expect(servicio.tema()).toBe('claro');
    expect(raiz.dataset['tema']).toBe('claro');
    expect(meta.content).toBe('#f6f7fb');
    expect(localStorage.getItem(CLAVE_MODO)).toBe('dia');
  });

  it('elegir un estilo lo aplica al documento, con la barra de su paleta, y lo recuerda', () => {
    const servicio = crear();

    servicio.elegirEstilo('jovenes');
    TestBed.tick();
    expect(raiz.dataset['estilo']).toBe('jovenes');
    expect(meta.content).toBe('#120a24');
    expect(localStorage.getItem(CLAVE_ESTILO)).toBe('jovenes');

    servicio.elegirModo('dia');
    TestBed.tick();
    expect(meta.content).toBe('#f7f5ff');

    servicio.elegirEstilo('adultos');
    TestBed.tick();
    expect(raiz.dataset['estilo']).toBeUndefined();
  });

  // Sin elección no se guarda nada: un cambio futuro del valor por defecto le llega
  it('arrancar no guarda nada; solo elegir', () => {
    const servicio = crear();
    expect(localStorage.getItem(CLAVE_ESTILO)).toBeNull();
    expect(localStorage.getItem(CLAVE_MODO)).toBeNull();

    servicio.elegirEstilo('ninos');
    expect(localStorage.getItem(CLAVE_ESTILO)).toBe('ninos');
    expect(localStorage.getItem(CLAVE_MODO)).toBeNull();
  });

  it('arranca con lo guardado', () => {
    localStorage.setItem(CLAVE_ESTILO, 'ninos');
    localStorage.setItem(CLAVE_MODO, 'dia');

    const servicio = crear();

    expect(servicio.estilo()).toBe('ninos');
    expect(raiz.dataset['estilo']).toBe('ninos');
    expect(raiz.dataset['tema']).toBe('claro');
    expect(meta.content).toBe('#fff9ec');
  });

  it('en automático sigue la hora y cambia sola al llegar las 19:00', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 9, 12, 18, 59));
    localStorage.setItem(CLAVE_MODO, 'automatico');

    const servicio = crear();
    expect(servicio.tema()).toBe('claro');

    vi.advanceTimersByTime(60_000);
    TestBed.tick();

    expect(servicio.tema()).toBe('oscuro');
    expect(raiz.dataset['tema']).toBeUndefined();
  });

  // Con la pestaña de fondo o la computadora suspendida, el temporizador se atrasa
  it('al volver a la pestaña revisa la hora', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 9, 12, 18, 0));
    localStorage.setItem(CLAVE_MODO, 'automatico');
    const servicio = crear();

    vi.setSystemTime(new Date(2026, 9, 12, 22, 0));
    document.dispatchEvent(new Event('visibilitychange'));
    TestBed.tick();

    expect(servicio.tema()).toBe('oscuro');
  });
});
