import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CodigoQr } from './codigo-qr';

// Como los códigos de las entradas: 22 caracteres aptos para URL
const CODIGO = 'x3Vb9QkLm2PzR7tYcW4sNa';

describe('CodigoQr', () => {
  async function dibujar(valor: string, etiqueta = 'Código QR de prueba'): Promise<ComponentFixture<CodigoQr>> {
    const fijo = TestBed.createComponent(CodigoQr);
    fijo.componentRef.setInput('valor', valor);
    fijo.componentRef.setInput('etiqueta', etiqueta);
    fijo.autoDetectChanges();
    await fijo.whenStable();
    return fijo;
  }

  function svg(fijo: ComponentFixture<CodigoQr>): SVGSVGElement | null {
    return fijo.nativeElement.querySelector('svg');
  }

  function trazo(fijo: ComponentFixture<CodigoQr>): string {
    return svg(fijo)?.querySelector('path')?.getAttribute('d') ?? '';
  }

  it('es una imagen con el nombre accesible recibido', async () => {
    const fijo = await dibujar(CODIGO, 'Código QR de la entrada 1 de Noche de jazz');

    expect(svg(fijo)?.getAttribute('role')).toBe('img');
    expect(svg(fijo)?.getAttribute('aria-label')).toBe('Código QR de la entrada 1 de Noche de jazz');
  });

  // 22 bytes con corrección M piden la versión 2: 25 módulos, más 4 de zona silenciosa por lado
  it('deja la zona silenciosa de 4 módulos alrededor', async () => {
    const fijo = await dibujar(CODIGO);

    expect(svg(fijo)?.getAttribute('viewBox')).toBe('0 0 33 33');
  });

  // La esquina del patrón de posición de arriba a la izquierda siempre es oscura
  it('dibuja los módulos oscuros desplazados por la zona silenciosa', async () => {
    const fijo = await dibujar(CODIGO);

    expect(trazo(fijo).startsWith('M4 4h1v1h-1z')).toBe(true);
  });

  it('el mismo valor da el mismo dibujo, y otro valor otro distinto', async () => {
    const uno = trazo(await dibujar(CODIGO));
    const igual = trazo(await dibujar(CODIGO));
    const otro = trazo(await dibujar('otro-codigo-de-entrada'));

    expect(igual).toBe(uno);
    expect(otro).not.toBe(uno);
  });

  it('al cambiar el valor se redibuja', async () => {
    const fijo = await dibujar(CODIGO);
    const antes = trazo(fijo);

    fijo.componentRef.setInput('valor', 'otro-codigo-de-entrada');
    await fijo.whenStable();

    expect(trazo(fijo)).not.toBe(antes);
  });

  // Un cobro real trae un texto largo
  it('un texto largo se dibuja en una versión más grande', async () => {
    const fijo = await dibujar('0'.repeat(300));

    const lado = Number(svg(fijo)?.getAttribute('viewBox')?.split(' ')[2]);
    expect(lado).toBeGreaterThan(33);
    expect(trazo(fijo)).not.toBe('');
  });

  it('sin valor no dibuja nada', async () => {
    const fijo = await dibujar('');

    expect(svg(fijo)).toBeNull();
  });
});
