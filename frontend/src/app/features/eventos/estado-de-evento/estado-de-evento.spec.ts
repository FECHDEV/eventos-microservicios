import { TestBed } from '@angular/core/testing';

import { EstadoDeEvento } from './estado-de-evento';

// El estado no depende solo del color: lleva su texto y una forma por estado
describe('EstadoDeEvento', () => {
  it.each([
    ['BORRADOR', 'Borrador'],
    ['ACTIVO', 'Activo'],
    ['POSTERGADO', 'Postergado'],
    ['CANCELADO', 'Cancelado'],
  ])('%s se muestra como «%s»', (estado, nombre) => {
    const fixture = TestBed.createComponent(EstadoDeEvento);
    fixture.componentRef.setInput('estado', estado);
    fixture.detectChanges();

    const elemento = fixture.nativeElement as HTMLElement;
    expect(elemento.textContent?.trim()).toBe(nombre);
    expect(elemento.getAttribute('data-estado')).toBe(estado);
  });

  // Etiqueta calculada, no un estado: el evento sigue ACTIVO o POSTERGADO
  it('un evento terminado lo dice junto al estado', () => {
    const fixture = TestBed.createComponent(EstadoDeEvento);
    fixture.componentRef.setInput('estado', 'ACTIVO');
    fixture.componentRef.setInput('terminado', true);
    fixture.detectChanges();

    const elemento = fixture.nativeElement as HTMLElement;
    expect(elemento.textContent?.trim()).toBe('Activo · Terminado');
    expect(elemento.hasAttribute('data-terminado')).toBe(true);
  });

  it('sin terminar no agrega nada', () => {
    const fixture = TestBed.createComponent(EstadoDeEvento);
    fixture.componentRef.setInput('estado', 'ACTIVO');
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).hasAttribute('data-terminado')).toBe(false);
  });
});
