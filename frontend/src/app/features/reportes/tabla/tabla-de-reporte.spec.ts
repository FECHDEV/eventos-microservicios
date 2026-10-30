import { TestBed } from '@angular/core/testing';

import { Tabla } from '../../../core/modelos/reporte';
import { TablaDeReporte } from './tabla-de-reporte';

const ESPACIO = ' ';

// Copias de ReportesControllerTest
const RESUMEN: Tabla = {
  columnas: [
    { titulo: 'Evento', tipo: 'TEXTO' },
    { titulo: 'Tipo de entrada', tipo: 'TEXTO' },
    { titulo: 'Monto', tipo: 'MONTO' },
  ],
  filas: [['Concierto', 'General', 300]],
  totales: ['Total', null, 300],
};

const DETALLE: Tabla = {
  columnas: [
    { titulo: 'Fecha de pago', tipo: 'FECHA_HORA' },
    { titulo: 'Cliente', tipo: 'TEXTO' },
    { titulo: 'Monto', tipo: 'MONTO' },
  ],
  filas: [['2026-09-02T10:14:00', '<b>Ana</b>', 300]],
  totales: null,
};

function dibujar(tabla: Tabla, titulo = 'Resumen'): HTMLElement {
  const fixture = TestBed.createComponent(TablaDeReporte);
  fixture.componentRef.setInput('tabla', tabla);
  fixture.componentRef.setInput('titulo', titulo);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

function textos(elemento: HTMLElement, selector: string): string[] {
  return [...elemento.querySelectorAll(selector)].map((celda) => celda.textContent?.trim() ?? '');
}

describe('TablaDeReporte', () => {
  it('muestra el título, las columnas y cada valor con su formato', () => {
    const elemento = dibujar(RESUMEN);

    expect(elemento.querySelector('caption')?.textContent?.trim()).toBe('Resumen');
    expect(textos(elemento, 'thead th')).toEqual(['Evento', 'Tipo de entrada', 'Monto']);
    expect([...elemento.querySelectorAll('thead th')].every((th) => th.getAttribute('scope') === 'col')).toBe(true);
    expect(textos(elemento, 'tbody td')).toEqual(['Concierto', 'General', `Bs${ESPACIO}300,00`]);
  });

  // Las cifras centradas bajo su título (pedido del usuario en la prueba manual)
  it('centra los números bajo su título', () => {
    const elemento = dibujar(RESUMEN);

    const celdas = elemento.querySelectorAll('tbody td');
    expect(celdas[0].classList.contains('numero')).toBe(false);
    expect(celdas[2].classList.contains('numero')).toBe(true);
  });

  it('los totales van al pie, con «Total» y las celdas vacías en blanco', () => {
    const elemento = dibujar(RESUMEN);

    expect(textos(elemento, 'tfoot td')).toEqual(['Total', '', `Bs${ESPACIO}300,00`]);
  });

  it('una tabla sin totales no tiene pie', () => {
    const elemento = dibujar(DETALLE, 'Detalle');

    expect(elemento.querySelector('tfoot')).toBeNull();
    expect(textos(elemento, 'tbody td')[0]).toBe('02/09/2026 10:14');
  });

  // Los datos se muestran como texto: nada de innerHTML
  it('un texto con HTML se ve como texto', () => {
    const elemento = dibujar(DETALLE, 'Detalle');

    expect(textos(elemento, 'tbody td')[1]).toBe('<b>Ana</b>');
    expect(elemento.querySelector('tbody b')).toBeNull();
  });
});
