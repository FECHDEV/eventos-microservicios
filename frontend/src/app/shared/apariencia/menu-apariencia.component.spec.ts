import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { Estilo, Modo } from './apariencia';
import { MenuAparienciaComponent } from './menu-apariencia.component';

@Component({
  imports: [MenuAparienciaComponent],
  template: `
    <p id="afuera">Afuera</p>
    <app-menu-apariencia
      [estilo]="estilo()"
      [modo]="modo()"
      (estiloElegido)="estilo.set($event)"
      (modoElegido)="modo.set($event)"
    />
  `,
})
class Anfitrion {
  readonly estilo = signal<Estilo>('adultos');
  readonly modo = signal<Modo>('noche');
}

// decisiones.md → «Tres estilos y modo día/noche»
describe('MenuAparienciaComponent', () => {
  function crear() {
    const fijo = TestBed.createComponent(Anfitrion);
    fijo.detectChanges();
    return fijo;
  }

  function boton(fijo: ReturnType<typeof crear>): HTMLButtonElement {
    return fijo.nativeElement.querySelector('button.disparador');
  }

  function menu(fijo: ReturnType<typeof crear>): HTMLElement | null {
    return fijo.nativeElement.querySelector('#menu-apariencia');
  }

  function abrir(fijo: ReturnType<typeof crear>): void {
    boton(fijo).click();
    fijo.detectChanges();
  }

  it('empieza cerrado, con un botón con nombre que dice si está abierto', () => {
    const fijo = crear();

    expect(menu(fijo)).toBeNull();
    expect(boton(fijo).getAttribute('aria-label')).toBe('Apariencia');
    expect(boton(fijo).getAttribute('aria-expanded')).toBe('false');
    // Cerrado, el menú no existe: no se puede señalar
    expect(boton(fijo).hasAttribute('aria-controls')).toBe(false);
  });

  it('al abrirlo muestra los tres estilos y los tres modos, con lo elegido marcado', () => {
    const fijo = crear();
    abrir(fijo);

    expect(boton(fijo).getAttribute('aria-expanded')).toBe('true');
    expect(boton(fijo).getAttribute('aria-controls')).toBe('menu-apariencia');
    const grupos = Array.from(menu(fijo)!.querySelectorAll('fieldset'));
    expect(grupos.map((grupo) => grupo.querySelector('legend')?.textContent?.trim())).toEqual(['Estilo', 'Modo']);
    const opciones = (grupo: Element) =>
      Array.from(grupo.querySelectorAll('label')).map((etiqueta) => etiqueta.textContent?.trim());
    expect(opciones(grupos[0])).toEqual(['Niños', 'Jóvenes', 'Adultos']);
    expect(opciones(grupos[1])).toEqual(['Automático, según la hora', 'Día', 'Noche']);
    expect(menu(fijo)!.querySelector<HTMLInputElement>('input[value="adultos"]')!.checked).toBe(true);
    expect(menu(fijo)!.querySelector<HTMLInputElement>('input[value="noche"]')!.checked).toBe(true);
  });

  it('elegir una opción la avisa en el momento y deja el menú abierto', () => {
    const fijo = crear();
    abrir(fijo);

    menu(fijo)!.querySelector<HTMLInputElement>('input[value="ninos"]')!.click();
    menu(fijo)!.querySelector<HTMLInputElement>('input[value="automatico"]')!.click();
    fijo.detectChanges();

    expect(fijo.componentInstance.estilo()).toBe('ninos');
    expect(fijo.componentInstance.modo()).toBe('automatico');
    expect(menu(fijo)).not.toBeNull();
  });

  it('Escape adentro del menú lo cierra y devuelve el foco al botón', () => {
    const fijo = crear();
    abrir(fijo);
    const radio = menu(fijo)!.querySelector<HTMLInputElement>('input[value="ninos"]')!;
    radio.focus();

    radio.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fijo.detectChanges();

    expect(menu(fijo)).toBeNull();
    expect(document.activeElement).toBe(boton(fijo));
  });

  // El Escape de otra parte de la página (un diálogo, un buscador) no es para el menú
  it('un Escape fuera del menú no lo toca', () => {
    const fijo = crear();
    abrir(fijo);

    fijo.nativeElement
      .querySelector('#afuera')
      .dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fijo.detectChanges();

    expect(menu(fijo)).not.toBeNull();
  });

  it('se cierra cuando el foco sale del menú con Tab, no cuando se mueve adentro', () => {
    const fijo = crear();
    abrir(fijo);
    const radio = menu(fijo)!.querySelector<HTMLInputElement>('input[value="ninos"]')!;

    boton(fijo).dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: radio }));
    fijo.detectChanges();
    expect(menu(fijo)).not.toBeNull();

    radio.dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: document.body }));
    fijo.detectChanges();
    expect(menu(fijo)).toBeNull();
  });

  it('un clic afuera lo cierra; uno adentro no', () => {
    const fijo = crear();
    abrir(fijo);

    menu(fijo)!.querySelector('legend')!.click();
    fijo.detectChanges();
    expect(menu(fijo)).not.toBeNull();

    fijo.nativeElement.querySelector('#afuera').click();
    fijo.detectChanges();
    expect(menu(fijo)).toBeNull();
  });

  it('el botón lo abre y lo cierra', () => {
    const fijo = crear();
    abrir(fijo);
    abrir(fijo);

    expect(menu(fijo)).toBeNull();
  });
});
