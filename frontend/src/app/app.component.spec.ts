import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, RouterOutlet } from '@angular/router';

import { AppComponent } from './app.component';
import { Rol } from './core/modelos/usuario';
import { Sesion } from './core/modelos/sesion';
import { SesionService } from './core/sesion/sesion.service';

@Component({ template: '' })
class PantallaVacia {}

@Component({ imports: [RouterOutlet], template: '<router-outlet />' })
class PantallaConPestanas {}

function sesionVigente(rol: Rol = 'CLIENTE'): Sesion {
  return {
    token: 'jwt.de.prueba',
    tipo: 'Bearer',
    expiracion: new Date(Date.now() + 3_600_000).toISOString(),
    usuario: {
      idUsuario: 2,
      nombre: 'Ana',
      apellido: 'Rojas',
      correo: 'ana@mail.com',
      telefono: null,
      fechaNacimiento: '2000-05-20',
      rol,
      estado: 'ACTIVA',
      fechaRegistro: '2026-09-14T10:00:00',
    },
  };
}

describe('AppComponent', () => {
  beforeEach(async () => {
    localStorage.clear();
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideRouter([
          { path: '', component: PantallaVacia, data: { pagina: 'PORTADA' } },
          { path: 'catalogo/:id', component: PantallaVacia, data: { pagina: 'DETALLE_DE_EVENTO' } },
          {
            path: 'fichas/:id',
            component: PantallaConPestanas,
            data: { pagina: 'FICHA_DE_EVENTO' },
            children: [
              { path: 'datos', component: PantallaVacia },
              { path: 'tipos', component: PantallaVacia },
            ],
          },
          { path: 'mis-datos', component: PantallaVacia },
          { path: 'mis-entradas', component: PantallaVacia },
          { path: 'panel', component: PantallaVacia },
          { path: 'inicio-sesion', component: PantallaVacia },
          { path: 'registro', component: PantallaVacia },
          { path: 'usuarios', component: PantallaVacia },
          { path: 'eventos', component: PantallaVacia },
          { path: 'devoluciones', component: PantallaVacia },
          { path: 'reportes', component: PantallaVacia },
          { path: 'validar', component: PantallaVacia },
          { path: 'eventos/:id/datos', component: PantallaVacia },
        ]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
  });

  function crear() {
    const fijo = TestBed.createComponent(AppComponent);
    fijo.detectChanges();
    return fijo;
  }

  it('se crea', () => {
    expect(crear().componentInstance).toBeTruthy();
  });

  it('muestra la marca en el encabezado', () => {
    const encabezado: HTMLElement = crear().nativeElement.querySelector('header');
    expect(encabezado.textContent).toContain('Sarao');
  });

  // La aplicación se presenta como el producto de una empresa (decisiones.md → Sistema de diseño)
  it('no menciona la universidad ni el origen académico', () => {
    const texto: string = crear().nativeElement.textContent.toLowerCase();
    expect(texto).not.toContain('universidad');
    expect(texto).not.toContain('uagrm');
  });

  // decisiones.md → «Tres estilos y modo día/noche»
  describe('menú de apariencia', () => {
    afterEach(() => {
      delete document.documentElement.dataset['tema'];
      delete document.documentElement.dataset['estilo'];
    });

    function abrir(fijo: ReturnType<typeof crear>): HTMLElement {
      fijo.nativeElement.querySelector('header button[aria-label="Apariencia"]').click();
      fijo.detectChanges();
      return fijo.nativeElement.querySelector('#menu-apariencia');
    }

    it('sin sesión está en el encabezado y marca lo elegido', () => {
      const menu = abrir(crear());

      expect(menu.querySelector<HTMLInputElement>('input[value="adultos"]')!.checked).toBe(true);
      expect(menu.querySelector<HTMLInputElement>('input[value="noche"]')!.checked).toBe(true);
    });

    it('con sesión también está', async () => {
      expect(abrir(await en('/panel', 'CLIENTE'))).toBeTruthy();
    });

    it('elegir estilo y modo los aplica a toda la página', async () => {
      const fijo = crear();
      const menu = abrir(fijo);

      menu.querySelector<HTMLInputElement>('input[value="jovenes"]')!.click();
      menu.querySelector<HTMLInputElement>('input[value="dia"]')!.click();
      await fijo.whenStable();

      expect(document.documentElement.dataset['estilo']).toBe('jovenes');
      expect(document.documentElement.dataset['tema']).toBe('claro');
    });
  });

  it('deja lugar para las pantallas', () => {
    expect(crear().nativeElement.querySelector('router-outlet')).toBeTruthy();
  });

  it('sin sesión no ofrece cerrarla', () => {
    expect(crear().nativeElement.querySelector('button.salir')).toBeNull();
  });

  it('con sesión muestra el nombre y permite cerrarla', () => {
    TestBed.inject(SesionService).iniciar(sesionVigente());

    const fijo = crear();

    expect(fijo.nativeElement.querySelector('header').textContent).toContain('Ana Rojas');
    expect(fijo.nativeElement.querySelector('button.salir').textContent?.trim()).toBe('Cerrar sesión');
  });

  it('«Cerrar sesión» la cierra', () => {
    const sesion = TestBed.inject(SesionService);
    sesion.iniciar(sesionVigente());
    const fijo = crear();

    fijo.nativeElement.querySelector('button.salir').click();
    fijo.detectChanges();

    expect(sesion.autenticado()).toBe(false);
    expect(fijo.nativeElement.querySelector('button.salir')).toBeNull();
  });

  // En un celular un nombre largo se recorta con puntos suspensivos: el
  // nombre completo sigue disponible al pasar el puntero
  it('conserva el nombre completo aunque se recorte', () => {
    TestBed.inject(SesionService).iniciar(sesionVigente());

    const usuario: HTMLElement = crear().nativeElement.querySelector('.usuario');

    expect(usuario.getAttribute('title')).toBe('Ana Rojas');
  });

  function enlacesDelMenu(fijo: ReturnType<typeof crear>): HTMLAnchorElement[] {
    return Array.from(fijo.nativeElement.querySelectorAll('nav a'));
  }

  function nombres(enlaces: HTMLAnchorElement[]): string[] {
    return enlaces.map((enlace) => enlace.textContent?.trim() ?? '');
  }

  async function en(url: string, rol: Rol = 'ADMINISTRADOR') {
    TestBed.inject(SesionService).iniciar(sesionVigente(rol));
    const fijo = crear();
    await TestBed.inject(Router).navigateByUrl(url);
    await fijo.whenStable();
    fijo.detectChanges();
    return fijo;
  }

  describe('navegación entre módulos', () => {
    // CU-05 RF-18: la portada es pública; el encabezado invita a entrar
    it('sin sesión muestra «Inicio» y ofrece iniciar sesión y crear cuenta', async () => {
      const fijo = crear();
      await TestBed.inject(Router).navigateByUrl('/');
      await fijo.whenStable();
      fijo.detectChanges();

      expect(nombres(enlacesDelMenu(fijo))).toEqual(['Inicio']);
      expect(fijo.nativeElement.querySelector('a[href="/inicio-sesion"]')?.textContent?.trim()).toBe(
        'Iniciar sesión',
      );
      expect(fijo.nativeElement.querySelector('a[href="/registro"]')?.textContent?.trim()).toBe('Crear cuenta');
      expect(fijo.nativeElement.querySelector('a[href="/mis-datos"]')).toBeNull();
    });

    it('con sesión ya no ofrece iniciar sesión ni crear cuenta', async () => {
      const fijo = await en('/', 'CLIENTE');

      expect(fijo.nativeElement.querySelector('a[href="/inicio-sesion"]')).toBeNull();
      expect(fijo.nativeElement.querySelector('a[href="/registro"]')).toBeNull();
    });

    // Pedido en la prueba manual: una casa explícita para volver al inicio
    // CU-08 RF-14: el cliente ve sus entradas
    it('un cliente ve «Inicio», con el ícono de la casa, y «Mis entradas»', async () => {
      const fijo = await en('/', 'CLIENTE');

      const enlaces = enlacesDelMenu(fijo);
      expect(nombres(enlaces)).toEqual(['Inicio', 'Mis entradas']);
      expect(enlaces[0].getAttribute('href')).toBe('/');
      expect(enlaces[0].querySelector('svg')?.getAttribute('aria-hidden')).toBe('true');
      expect(enlaces[1].getAttribute('href')).toBe('/mis-entradas');
    });

    it('en Mis entradas queda marcado ese módulo', async () => {
      const fijo = await en('/mis-entradas', 'CLIENTE');

      const actuales = enlacesDelMenu(fijo).filter((enlace) => enlace.getAttribute('aria-current') === 'page');
      expect(nombres(actuales)).toEqual(['Mis entradas']);
    });

    // RF-20: la bienvenida con los accesos está en el Panel
    // CU-13 RF-17: el administrador también ve Devoluciones; CU-14 RF-18, Reportes
    it('un administrador ve Inicio, Panel, Usuarios, Eventos, Devoluciones y Reportes', async () => {
      const fijo = await en('/');

      expect(nombres(enlacesDelMenu(fijo))).toEqual([
        'Inicio',
        'Panel',
        'Usuarios',
        'Eventos',
        'Devoluciones',
        'Reportes',
      ]);
      expect(enlacesDelMenu(fijo)[1].getAttribute('href')).toBe('/panel');
      expect(enlacesDelMenu(fijo)[5].getAttribute('href')).toBe('/reportes');
    });

    it('marca Reportes en su pantalla', async () => {
      const fijo = await en('/reportes');

      const actuales = enlacesDelMenu(fijo).filter((enlace) => enlace.getAttribute('aria-current') === 'page');
      expect(nombres(actuales)).toEqual(['Reportes']);
    });

    // CU-15 RF-16: el personal autorizado valida entradas
    it('el personal autorizado ve Inicio, Panel y Validar entradas', async () => {
      const fijo = await en('/', 'PERSONAL');

      expect(nombres(enlacesDelMenu(fijo))).toEqual(['Inicio', 'Panel', 'Validar entradas']);
      expect(enlacesDelMenu(fijo)[2].getAttribute('href')).toBe('/validar');
    });

    it('marca el módulo actual', async () => {
      const fijo = await en('/');

      const actuales = enlacesDelMenu(fijo).filter((enlace) => enlace.getAttribute('aria-current') === 'page');
      expect(nombres(actuales)).toEqual(['Inicio']);
    });

    // Dentro de la ficha de un evento se sigue en el módulo Eventos
    it('en Devoluciones queda marcado ese módulo', async () => {
      const fijo = await en('/devoluciones');

      const actuales = enlacesDelMenu(fijo).filter((enlace) => enlace.getAttribute('aria-current') === 'page');
      expect(nombres(actuales)).toEqual(['Devoluciones']);
    });

    it('dentro de un evento marca Eventos y no Inicio', async () => {
      const fijo = await en('/eventos/5/datos');

      const actuales = enlacesDelMenu(fijo).filter((enlace) => enlace.getAttribute('aria-current') === 'page');
      expect(nombres(actuales)).toEqual(['Eventos']);
    });
  });

  // decisiones.md → «Búsqueda en el encabezado»: busca en el catálogo desde cualquier pantalla
  describe('buscador del encabezado', () => {
    function campo(fijo: ReturnType<typeof crear>): HTMLInputElement {
      return fijo.nativeElement.querySelector('header form[role="search"] input[type="search"]');
    }

    async function buscar(fijo: ReturnType<typeof crear>, texto: string): Promise<void> {
      const entrada = campo(fijo);
      entrada.value = texto;
      entrada.dispatchEvent(new Event('input'));
      fijo.nativeElement.querySelector('header form[role="search"]').dispatchEvent(new Event('submit'));
      await fijo.whenStable();
      fijo.detectChanges();
    }

    it('sin sesión está en el encabezado, con su nombre accesible', () => {
      const fijo = crear();

      expect(campo(fijo).getAttribute('aria-label')).toBe('Buscar eventos');
      expect(fijo.nativeElement.querySelector('header form[role="search"]').getAttribute('aria-label')).toBe(
        'Buscar eventos',
      );
    });

    it('con sesión también está, para todos los roles', async () => {
      for (const rol of ['CLIENTE', 'ADMINISTRADOR', 'PERSONAL'] as const) {
        expect(campo(await en('/panel', rol))).toBeTruthy();
      }
    });

    it('desde otra pantalla lleva a la portada con el texto buscado', async () => {
      const fijo = await en('/panel');

      await buscar(fijo, '  rock  ');

      expect(TestBed.inject(Router).url).toBe('/?texto=rock');
    });

    it('en la portada conserva las fechas y vuelve a la primera página', async () => {
      const fijo = await en('/?texto=jazz&desde=2026-11-01&hasta=2026-11-30&pagina=2');

      await buscar(fijo, 'rock');

      expect(TestBed.inject(Router).url).toBe('/?texto=rock&desde=2026-11-01&hasta=2026-11-30');
    });

    it('vacío quita el texto de la búsqueda', async () => {
      const fijo = await en('/?texto=jazz');

      await buscar(fijo, '   ');

      expect(TestBed.inject(Router).url).toBe('/');
    });

    it('muestra el texto que filtra la portada y se vacía fuera de ella', async () => {
      const fijo = await en('/?texto=jazz');
      expect(campo(fijo).value).toBe('jazz');

      await TestBed.inject(Router).navigateByUrl('/panel');
      await fijo.whenStable();
      fijo.detectChanges();

      expect(campo(fijo).value).toBe('');
    });

    it('no acepta más de 200 caracteres, como el catálogo', () => {
      expect(campo(crear()).getAttribute('maxlength')).toBe('200');
    });

    // Pegado en la URL el texto puede ser más largo: el backend lo rechazaría
    it('no manda un texto de más de 200 caracteres que vino en la URL', async () => {
      const largo = 'a'.repeat(201);
      const fijo = await en(`/panel`);
      await TestBed.inject(Router).navigateByUrl(`/?texto=${largo}`);
      await fijo.whenStable();
      fijo.detectChanges();

      fijo.nativeElement.querySelector('header form[role="search"]').dispatchEvent(new Event('submit'));
      await fijo.whenStable();

      expect(TestBed.inject(Router).url).toBe(`/?texto=${largo}`);
    });

    it('no pisa lo que se está escribiendo cuando el catálogo cambia de página', async () => {
      const fijo = await en('/?texto=jazz');
      const entrada = campo(fijo);
      entrada.value = 'rock';
      entrada.dispatchEvent(new Event('input'));

      await TestBed.inject(Router).navigateByUrl('/?texto=jazz&pagina=1');
      await fijo.whenStable();
      fijo.detectChanges();

      expect(campo(fijo).value).toBe('rock');
    });

    it('lee la URL como el catálogo: descarta fechas inválidas y toma el primer texto', async () => {
      const fijo = await en('/?texto=jazz&texto=blues&desde=foo&hasta=2026-11-30');
      expect(campo(fijo).value).toBe('jazz');

      await buscar(fijo, 'rock');

      expect(TestBed.inject(Router).url).toBe('/?texto=rock&hasta=2026-11-30');
    });
  });

  // decisiones.md → «Contador de visitas por página»
  describe('contador de visitas del pie', () => {
    async function ir(fijo: ReturnType<typeof crear>, url: string): Promise<void> {
      await TestBed.inject(Router).navigateByUrl(url);
      await fijo.whenStable();
      fijo.detectChanges();
    }

    function pie(fijo: ReturnType<typeof crear>): string {
      return fijo.nativeElement.querySelector('footer')?.textContent?.trim() ?? '';
    }

    async function responder(fijo: ReturnType<typeof crear>, pagina: string, total: number): Promise<void> {
      TestBed.inject(HttpTestingController)
        .expectOne({ method: 'POST', url: `/api/visitas/${pagina}` })
        .flush({ total });
      await fijo.whenStable();
      fijo.detectChanges();
    }

    it('suma una visita a la página abierta y muestra el total en el pie, sin sesión', async () => {
      const fijo = crear();
      await ir(fijo, '/');

      await responder(fijo, 'PORTADA', 1234);

      expect(pie(fijo)).toBe('Visitas a esta página: 1.234');
    });

    it('buscar o paginar en la misma página no suma otra visita', async () => {
      const fijo = crear();
      await ir(fijo, '/');
      await responder(fijo, 'PORTADA', 7);

      await ir(fijo, '/?texto=rock&pagina=1');

      TestBed.inject(HttpTestingController).expectNone('/api/visitas/PORTADA');
      expect(pie(fijo)).toBe('Visitas a esta página: 7');
    });

    it('cada evento abierto suma al mismo detalle de evento', async () => {
      const fijo = crear();
      await ir(fijo, '/catalogo/1');
      await responder(fijo, 'DETALLE_DE_EVENTO', 3);

      await ir(fijo, '/catalogo/2');
      await responder(fijo, 'DETALLE_DE_EVENTO', 4);

      expect(pie(fijo)).toBe('Visitas a esta página: 4');
    });

    it('una pestaña toma la página de su ruta padre', async () => {
      const fijo = await en('/fichas/5/datos');

      await responder(fijo, 'FICHA_DE_EVENTO', 2);

      expect(pie(fijo)).toBe('Visitas a esta página: 2');
    });

    it('cambiar de pestaña no suma otra visita; abrir otra ficha sí', async () => {
      const fijo = await en('/fichas/5/datos');
      await responder(fijo, 'FICHA_DE_EVENTO', 2);

      await ir(fijo, '/fichas/5/tipos');
      TestBed.inject(HttpTestingController).expectNone('/api/visitas/FICHA_DE_EVENTO');
      expect(pie(fijo)).toBe('Visitas a esta página: 2');

      await ir(fijo, '/fichas/6/datos');
      await responder(fijo, 'FICHA_DE_EVENTO', 3);
      expect(pie(fijo)).toBe('Visitas a esta página: 3');
    });

    it('mientras se cuenta la página nueva no muestra el total de la anterior', async () => {
      const fijo = crear();
      await ir(fijo, '/');
      await responder(fijo, 'PORTADA', 9);

      await ir(fijo, '/catalogo/1');

      expect(pie(fijo)).not.toContain('9');
      await responder(fijo, 'DETALLE_DE_EVENTO', 1);
      expect(pie(fijo)).toBe('Visitas a esta página: 1');
    });

    it('si no se puede contar, el pie no muestra el contador', async () => {
      const fijo = crear();
      await ir(fijo, '/');

      TestBed.inject(HttpTestingController)
        .expectOne('/api/visitas/PORTADA')
        .flush(null, { status: 500, statusText: 'Error' });
      await fijo.whenStable();
      fijo.detectChanges();

      expect(pie(fijo)).not.toContain('Visitas');
    });

    it('una pantalla sin página no cuenta', async () => {
      const fijo = await en('/panel');

      TestBed.inject(HttpTestingController).expectNone(() => true);
      expect(pie(fijo)).not.toContain('Visitas');
    });
  });

  describe('perfil', () => {
    it('el nombre lleva a Mis datos, con el ícono de una persona', async () => {
      const fijo = await en('/', 'CLIENTE');

      const perfil: HTMLAnchorElement = fijo.nativeElement.querySelector('a.perfil');
      expect(perfil.getAttribute('href')).toBe('/mis-datos');
      expect(perfil.textContent).toContain('Ana Rojas');
      expect(perfil.getAttribute('aria-label')).toBe('Mis datos de Ana Rojas');
      expect(perfil.querySelector('svg')?.getAttribute('aria-hidden')).toBe('true');
    });

    it('en Mis datos queda marcado', async () => {
      const fijo = await en('/mis-datos', 'CLIENTE');

      expect(fijo.nativeElement.querySelector('a.perfil').getAttribute('aria-current')).toBe('page');
    });
  });
});
