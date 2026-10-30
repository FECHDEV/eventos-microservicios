import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { ReportesService } from '../../core/api/reportes.service';
import { ErrorApi } from '../../core/modelos/error-api';
import {
  EnvioDeReporte,
  FormatoDeArchivo,
  OpcionesDeReporte,
  Reporte,
  SolicitudDeEnvio,
  SolicitudDeReporte,
} from '../../core/modelos/reporte';
import { ReportesPage } from './reportes.page';

// Copias de ReportesControllerTest
const OPCIONES: OpcionesDeReporte = {
  eventos: [
    {
      idEvento: 5,
      nombre: 'Concierto',
      fecha: '2026-10-04',
      tipos: [
        { idTipoEntrada: 9, nombre: 'General' },
        { idTipoEntrada: 10, nombre: 'VIP' },
      ],
    },
    { idEvento: 6, nombre: 'Feria', fecha: '2026-10-10', tipos: [{ idTipoEntrada: 11, nombre: 'Entrada' }] },
  ],
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

const SIN_RESULTADOS: ErrorApi = {
  status: 404,
  detail: 'No hay datos que cumplan los criterios indicados.',
  codigo: 'REPORTE_SIN_RESULTADOS',
};

// Lo que la pantalla usa del httpResource
class RecursoFalso<T> {
  readonly value = signal<T | undefined>(undefined);
  readonly error = signal<unknown>(undefined);
  readonly reload = vi.fn(() => true);
  hasValue(): boolean {
    return this.value() !== undefined;
  }
}

class ReportesFalso {
  recurso = new RecursoFalso<OpcionesDeReporte>();
  generar = vi.fn<(solicitud: SolicitudDeReporte) => Promise<Reporte>>(() => Promise.resolve(REPORTE));
  exportar = vi.fn<(id: number, formato: FormatoDeArchivo) => Promise<Blob>>(() => Promise.resolve(new Blob(['x'])));
  enviarPorCorreo = vi.fn<(id: number, solicitud: SolicitudDeEnvio) => Promise<EnvioDeReporte>>(() =>
    Promise.resolve({ destinatarios: 2 }),
  );

  opciones() {
    return this.recurso;
  }
}

// Una promesa que el test resuelve cuando quiere, para simular una respuesta lenta
function pendiente<T>() {
  let resolver!: (valor: T) => void;
  const promesa = new Promise<T>((seguir) => (resolver = seguir));
  return { promesa, resolver };
}

describe('ReportesPage', () => {
  let harness: RouterTestingHarness;
  let servicio: ReportesFalso;

  async function abrir(opciones: OpcionesDeReporte | null = OPCIONES) {
    TestBed.resetTestingModule();
    servicio = new ReportesFalso();
    servicio.recurso.value.set(opciones ?? undefined);
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: 'reportes', component: ReportesPage }]), { provide: ReportesService, useValue: servicio }],
    });
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/reportes', ReportesPage);
    harness.fixture.autoDetectChanges();
    await harness.fixture.whenStable();
  }

  function pantalla(): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  function texto(): string {
    return pantalla().textContent ?? '';
  }

  function campo<T extends HTMLElement = HTMLInputElement>(id: string): T | null {
    return pantalla().querySelector<T>(`#${id}`);
  }

  function boton(textoDelBoton: string): HTMLButtonElement | undefined {
    return Array.from(pantalla().querySelectorAll<HTMLButtonElement>('button')).find(
      (cada) => cada.textContent?.trim() === textoDelBoton,
    );
  }

  async function asentar() {
    await new Promise((seguir) => setTimeout(seguir, 0));
    await harness.fixture.whenStable();
  }

  async function elegirTipo(nombre: string) {
    const radio = Array.from(pantalla().querySelectorAll<HTMLInputElement>('input[type="radio"]')).find(
      (cada) => cada.closest('label')?.textContent?.trim() === nombre,
    )!;
    radio.click();
    await asentar();
  }

  async function escribir(id: string, valor: string) {
    const input = campo(id)!;
    input.value = valor;
    input.dispatchEvent(new Event('input'));
    await asentar();
  }

  async function elegir(id: string, valor: string) {
    const select = campo<HTMLSelectElement>(id)!;
    select.value = valor;
    // Como el navegador: primero input (lo lee [formField]) y después change
    select.dispatchEvent(new Event('input'));
    select.dispatchEvent(new Event('change'));
    await asentar();
  }

  async function generar() {
    boton('Generar reporte')!.click();
    await asentar();
  }

  it('se titula «Reportes» y empieza en Ventas', async () => {
    await abrir();

    expect(pantalla().querySelector('h1')?.textContent?.trim()).toBe('Reportes');
    expect(pantalla().querySelector('fieldset legend')?.textContent?.trim()).toBe('Tipo de reporte');
    const marcado = pantalla().querySelector<HTMLInputElement>('input[type="radio"]:checked');
    expect(marcado?.closest('label')?.textContent?.trim()).toBe('Ventas');
  });

  // RF-2: cada tipo con sus filtros
  it.each([
    ['Ventas', ['desde', 'hasta', 'idEvento', 'idTipoEntrada']],
    ['Ingresos', ['desde', 'hasta', 'idEvento']],
    ['Entradas', ['idEvento', 'idTipoEntrada', 'estado']],
    ['Clientes', ['desde', 'hasta', 'idEvento']],
    ['Accesos', ['desde', 'hasta', 'idEvento', 'idPersonal']],
  ])('%s muestra solo sus filtros', async (tipo, filtros) => {
    await abrir();
    await elegirTipo(tipo);

    const presentes = ['desde', 'hasta', 'idEvento', 'idTipoEntrada', 'estado', 'idPersonal'].filter(
      (id) => campo(id) !== null,
    );
    expect(presentes).toEqual(filtros);
  });

  it('en Clientes el rango es de la fecha de registro', async () => {
    await abrir();
    await elegirTipo('Clientes');

    expect(pantalla().querySelector('label[for="desde"]')?.textContent?.trim()).toBe('Registrados desde');
    expect(pantalla().querySelector('label[for="hasta"]')?.textContent?.trim()).toBe('Registrados hasta');
  });

  // FA-01
  it('sin filtros manda solo el tipo', async () => {
    await abrir();
    await generar();

    expect(servicio.generar).toHaveBeenCalledWith({ tipo: 'VENTAS' });
  });

  it('manda solo los criterios con valor, los ids como números', async () => {
    await abrir();
    await escribir('desde', '2026-09-01');
    await elegir('idEvento', '5');
    await generar();

    expect(servicio.generar).toHaveBeenCalledWith({ tipo: 'VENTAS', desde: '2026-09-01', idEvento: 5 });
  });

  it('un filtro de otro tipo no viaja', async () => {
    await abrir();
    await elegirTipo('Entradas');
    await elegir('estado', 'DEVUELTA');
    await elegirTipo('Ventas');
    await generar();

    expect(servicio.generar).toHaveBeenCalledWith({ tipo: 'VENTAS' });
  });

  it('el tipo de entrada se elige después del evento, entre los de ese evento', async () => {
    await abrir();

    expect(campo<HTMLSelectElement>('idTipoEntrada')!.disabled).toBe(true);
    await elegir('idEvento', '5');
    const tipos = Array.from(campo<HTMLSelectElement>('idTipoEntrada')!.options).map((opcion) => opcion.text);
    expect(tipos).toEqual(['Todos', 'General', 'VIP']);
    expect(campo<HTMLSelectElement>('idTipoEntrada')!.disabled).toBe(false);

    await elegir('idTipoEntrada', '10');
    await elegir('idEvento', '6');
    await generar();

    expect(servicio.generar).toHaveBeenCalledWith({ tipo: 'VENTAS', idEvento: 6 });
  });

  it('los eventos se ofrecen con su fecha', async () => {
    await abrir();

    const eventos = Array.from(campo<HTMLSelectElement>('idEvento')!.options).map((opcion) => opcion.text);
    expect(eventos).toEqual(['Todos', 'Concierto (04/10/2026)', 'Feria (10/10/2026)']);
  });

  // RF-4
  it('un rango invertido marca el campo y no llama al backend', async () => {
    await abrir();
    await escribir('desde', '2026-09-30');
    await escribir('hasta', '2026-09-01');
    await generar();

    expect(servicio.generar).not.toHaveBeenCalled();
    expect(campo('hasta')!.getAttribute('aria-invalid')).toBe('true');
    expect(texto()).toContain('La fecha hasta no puede ser anterior a la fecha desde.');
    // Quien usa teclado llega directo al campo a corregir
    expect(document.activeElement).toBe(campo('hasta'));
  });

  // RF-6
  it('muestra el reporte con su fecha, sus criterios y las dos tablas', async () => {
    await abrir();
    await generar();

    const titulo = pantalla().querySelector<HTMLElement>('h2');
    expect(titulo?.textContent?.trim()).toBe('Reporte de ventas');
    expect(texto()).toContain('Generado el 23/09/2026 14:05');
    expect(texto()).toContain('Desde: 01/09/2026');
    const tablas = Array.from(pantalla().querySelectorAll('caption')).map((caption) => caption.textContent?.trim());
    expect(tablas).toEqual(['Resumen', 'Detalle']);
    expect(texto()).toContain('1 fila');
    expect(document.activeElement).toBe(titulo);
  });

  it('sin filtros lo dice', async () => {
    await abrir();
    servicio.generar.mockResolvedValue({ ...REPORTE, criterios: [] });
    await generar();

    expect(texto()).toContain('Sin filtros');
  });

  // FA-02
  it('sin resultados avisa y deja los filtros como estaban', async () => {
    await abrir();
    servicio.generar.mockRejectedValue(SIN_RESULTADOS);
    await escribir('desde', '2026-09-03');
    await generar();

    const aviso = pantalla().querySelector('[role="status"] .aviso');
    expect(aviso?.textContent?.trim()).toBe('No hay datos que cumplan esos criterios. Prueba con otros filtros.');
    expect(campo('desde')!.value).toBe('2026-09-03');
    expect(pantalla().querySelector('caption')).toBeNull();
  });

  it('un doble clic genera una sola vez', async () => {
    await abrir();
    const respuesta = pendiente<Reporte>();
    servicio.generar.mockReturnValue(respuesta.promesa);

    boton('Generar reporte')!.click();
    await asentar();
    expect(boton('Generando…')!.disabled).toBe(true);
    boton('Generando…')!.click();
    respuesta.resolver(REPORTE);
    await asentar();

    expect(servicio.generar).toHaveBeenCalledTimes(1);
  });

  // Lo que se ve corresponde siempre a los filtros que se ven
  it('una respuesta que llega después de cambiar el tipo no se muestra', async () => {
    await abrir();
    const respuesta = pendiente<Reporte>();
    servicio.generar.mockReturnValue(respuesta.promesa);

    await generar();
    await elegirTipo('Ingresos');
    respuesta.resolver(REPORTE);
    await asentar();

    expect(pantalla().querySelector('caption')).toBeNull();
  });

  it('cambiar un filtro borra el reporte anterior', async () => {
    await abrir();
    await generar();
    await escribir('desde', '2026-09-01');

    expect(pantalla().querySelector('caption')).toBeNull();
  });

  // RF-4 desde el backend
  it('un campo rechazado por el backend se marca', async () => {
    await abrir();
    servicio.generar.mockRejectedValue({
      status: 400,
      detail: 'Hay campos a corregir.',
      codigo: 'DATOS_INVALIDOS',
      errores: [{ campo: 'hasta', mensaje: 'no puede ser anterior a la fecha desde' }],
    } satisfies ErrorApi);
    await generar();

    expect(campo('hasta')!.getAttribute('aria-invalid')).toBe('true');
    expect(texto()).toContain('La fecha hasta no puede ser anterior a la fecha desde.');
  });

  it('otro rechazo se muestra como alerta', async () => {
    await abrir();
    servicio.generar.mockRejectedValue({ status: 404, detail: 'x', codigo: 'EVENTO_NO_ENCONTRADO' } satisfies ErrorApi);
    await generar();

    expect(pantalla().querySelector('[role="alert"] .aviso')?.textContent?.trim()).toBe('No encontramos ese evento.');
  });

  // RF-15 y RF-16
  describe('descargar', () => {
    let descargados: string[];

    beforeEach(() => {
      descargados = [];
      URL.createObjectURL = vi.fn(() => 'blob:prueba');
      URL.revokeObjectURL = vi.fn();
      vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
        descargados.push(this.download);
      });
    });

    afterEach(() => vi.restoreAllMocks());

    it.each([
      ['Descargar PDF', 'pdf', 'reporte-ventas-12.pdf'],
      ['Descargar Excel', 'excel', 'reporte-ventas-12.xlsx'],
    ] as const)('«%s» baja el archivo del reporte', async (textoDelBoton, formato, nombre) => {
      await abrir();
      await generar();

      boton(textoDelBoton)!.click();
      await asentar();

      expect(servicio.exportar).toHaveBeenCalledWith(12, formato);
      expect(descargados).toEqual([nombre]);
    });

    it('si falla, avisa y el botón vuelve a quedar disponible', async () => {
      await abrir();
      await generar();
      servicio.exportar.mockRejectedValue({ status: 0, detail: 'x', codigo: 'SIN_CONEXION' } satisfies ErrorApi);

      boton('Descargar PDF')!.click();
      await asentar();

      expect(pantalla().querySelector('[role="alert"] .aviso')?.textContent?.trim()).toBe(
        'No se pudo descargar el archivo. Vuelve a intentar.',
      );
      expect(boton('Descargar PDF')!.disabled).toBe(false);
      expect(descargados).toEqual([]);
    });
  });

  // Las opciones de los filtros
  it('si las opciones no cargan, lo dice y se puede reintentar', async () => {
    await abrir(null);
    servicio.recurso.error.set(new Error('sin red'));
    await asentar();

    expect(texto()).toContain('No se pudieron cargar los eventos.');
    boton('Reintentar')!.click();
    expect(servicio.recurso.reload).toHaveBeenCalled();
    expect(campo('desde')).not.toBeNull();
  });

  // CU-14 RF-19, RF-20 y RF-24
  describe('enviar por correo', () => {
    it('manda los destinatarios escritos y el formato elegido', async () => {
      await abrir();
      await generar();
      await escribir('destinatarios', 'ana@mail.com, pedro@mail.com');
      await elegir('formatoEnvio', 'AMBOS');

      boton('Enviar por correo')!.click();
      await asentar();

      expect(servicio.enviarPorCorreo).toHaveBeenCalledWith(12, {
        destinatarios: ['ana@mail.com', 'pedro@mail.com'],
        formato: 'AMBOS',
      });
      expect(pantalla().querySelector('[role="status"] .aviso')?.textContent?.trim()).toBe(
        'Enviado a 2 destinatarios.',
      );
    });

    // RF-19: el Administrador sabe a qué dominio puede mandar
    it('dice el dominio al que se puede mandar', async () => {
      await abrir();
      await generar();

      expect(texto()).toContain('Hasta 10 buzones @mail.com, separados por comas.');
    });

    // RF-22, en la pantalla: nombra la dirección que falla, sin llamar al backend
    it('una dirección mal escrita o de otro dominio se marca sin llamar al backend', async () => {
      await abrir();
      await generar();
      await escribir('destinatarios', 'ana@mail.com, ana@gmail.com');

      boton('Enviar por correo')!.click();
      await asentar();

      expect(servicio.enviarPorCorreo).not.toHaveBeenCalled();
      expect(texto()).toContain('«ana@gmail.com» no es del dominio mail.com.');

      await escribir('destinatarios', 'ana@mail.com, ana');
      expect(texto()).toContain('«ana» no es una dirección de correo válida.');

      await escribir('destinatarios', 'ana>pedro@mail.com');
      expect(texto()).toContain('«ana>pedro@mail.com» no es una dirección de correo válida.');

      await escribir('destinatarios', Array.from({ length: 11 }, (_, i) => `ana${i}@mail.com`).join(', '));
      expect(texto()).toContain('Escribe hasta 10 destinatarios.');
      expect(servicio.enviarPorCorreo).not.toHaveBeenCalled();
    });

    // Una respuesta que llega después de generar otro reporte no se muestra en el nuevo
    it('la respuesta de un envío en curso no cae sobre otro reporte', async () => {
      await abrir();
      await generar();
      await escribir('destinatarios', 'ana@mail.com');
      let responder!: (envio: EnvioDeReporte) => void;
      servicio.enviarPorCorreo.mockReturnValue(new Promise((resolver) => (responder = resolver)));
      boton('Enviar por correo')!.click();
      await asentar();

      servicio.generar.mockResolvedValue({ ...REPORTE, idReporte: 13 });
      await escribir('desde', '2026-09-01');
      await generar();
      responder({ destinatarios: 1 });
      await asentar();

      expect(campo('destinatarios')).not.toBeNull();
      expect(texto()).not.toContain('Enviado a');
    });

    // El aviso es del reporte que se envió, no del que está en pantalla después
    it('al generar otro reporte, el aviso del envío anterior desaparece', async () => {
      await abrir();
      await generar();
      await escribir('destinatarios', 'ana@mail.com');
      boton('Enviar por correo')!.click();
      await asentar();
      expect(texto()).toContain('Enviado a 2 destinatarios.');

      servicio.generar.mockResolvedValue({ ...REPORTE, idReporte: 13 });
      await escribir('desde', '2026-09-01');
      await generar();

      expect(campo('destinatarios')).not.toBeNull();
      expect(texto()).not.toContain('Enviado a');
    });

    it('no está hasta que hay un reporte en pantalla', async () => {
      await abrir();

      expect(campo('destinatarios')).toBeNull();
    });

    // RF-22
    it('sin destinatarios marca el campo y no llama al backend', async () => {
      await abrir();
      await generar();

      boton('Enviar por correo')!.click();
      await asentar();

      expect(servicio.enviarPorCorreo).not.toHaveBeenCalled();
      expect(campo('destinatarios')!.getAttribute('aria-invalid')).toBe('true');
      expect(texto()).toContain('Escribe al menos un destinatario.');
      expect(document.activeElement).toBe(campo('destinatarios'));
    });

    // Quien decide es el backend: lo que responda se muestra bajo el campo, hasta que se corrige
    it('el rechazo del backend se muestra bajo el campo y se va al corregir', async () => {
      await abrir();
      await generar();
      await escribir('destinatarios', 'ana@mail.com');
      servicio.enviarPorCorreo.mockRejectedValue({
        status: 400,
        detail: 'Hay campos a corregir.',
        codigo: 'DATOS_INVALIDOS',
        errores: [{ campo: 'destinatarios', mensaje: 'incluyen «ana@mail.com», que no es del dominio otro.com' }],
      } satisfies ErrorApi);

      boton('Enviar por correo')!.click();
      await asentar();

      expect(campo('destinatarios')!.getAttribute('aria-invalid')).toBe('true');
      expect(texto()).toContain('Los destinatarios incluyen «ana@mail.com», que no es del dominio otro.com.');

      await escribir('destinatarios', 'pedro@mail.com');
      expect(texto()).not.toContain('Los destinatarios incluyen');
    });

    // RF-23
    it('si falla, avisa y el botón vuelve a quedar disponible', async () => {
      await abrir();
      await generar();
      await escribir('destinatarios', 'ana@mail.com');
      servicio.enviarPorCorreo.mockRejectedValue({ status: 0, detail: 'x', codigo: 'SIN_CONEXION' } satisfies ErrorApi);

      boton('Enviar por correo')!.click();
      await asentar();

      expect(pantalla().querySelector('[role="alert"] .aviso')?.textContent?.trim()).toBe(
        'No pudimos conectar con el servidor. Intenta de nuevo.',
      );
      expect(boton('Enviar por correo')!.disabled).toBe(false);
    });
  });
});
