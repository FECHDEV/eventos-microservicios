import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { routes } from './app.routes';
import { PAGINAS_VISITADAS } from './core/modelos/visitas';
import { Sesion } from './core/modelos/sesion';
import { Rol } from './core/modelos/usuario';
import { SesionService } from './core/sesion/sesion.service';

function sesionCon(rol: Rol): Sesion {
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

// Las rutas reales de la aplicación, con sus guards
describe('rutas', () => {
  async function navegarComo(rol: Rol | null, url: string): Promise<string> {
    localStorage.clear();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    if (rol !== null) {
      TestBed.inject(SesionService).iniciar(sesionCon(rol));
    }
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    return TestBed.inject(Router).url;
  }

  afterEach(() => localStorage.clear());

  // CU-09 es del administrador: los demás vuelven al inicio (usabilidad; el
  // backend igual responde 403)
  it('la gestión de usuarios es solo del administrador', async () => {
    expect(await navegarComo('ADMINISTRADOR', '/usuarios')).toBe('/usuarios');
    expect(await navegarComo('CLIENTE', '/usuarios')).toBe('/');
    expect(await navegarComo('PERSONAL', '/usuarios')).toBe('/');
  });

  it('sin sesión la gestión de usuarios lleva a iniciar sesión', async () => {
    expect(await navegarComo(null, '/usuarios')).toBe('/inicio-sesion?destino=%2Fusuarios');
  });

  // CU-10, CU-11 y CU-12 son del administrador, con la misma regla
  it('la gestión de eventos es solo del administrador', async () => {
    expect(await navegarComo('ADMINISTRADOR', '/eventos')).toBe('/eventos');
    expect(await navegarComo('CLIENTE', '/eventos')).toBe('/');
    expect(await navegarComo('PERSONAL', '/eventos')).toBe('/');
  });

  // Entrar a la ficha lleva a su primera pestaña
  it('la ficha de un evento es solo del administrador y abre en Datos', async () => {
    expect(await navegarComo('ADMINISTRADOR', '/eventos/5')).toBe('/eventos/5/datos');
    expect(await navegarComo('ADMINISTRADOR', '/eventos/5/tipos')).toBe('/eventos/5/tipos');
    expect(await navegarComo('ADMINISTRADOR', '/eventos/5/estado')).toBe('/eventos/5/estado');
    expect(await navegarComo('CLIENTE', '/eventos/5')).toBe('/');
  });

  // «nuevo» no es un id: el alta se declara antes que la ficha
  it('el alta de un evento es solo del administrador', async () => {
    expect(await navegarComo('ADMINISTRADOR', '/eventos/nuevo')).toBe('/eventos/nuevo');
    expect(await navegarComo('CLIENTE', '/eventos/nuevo')).toBe('/');
  });

  // CU-05 RF-18: la portada y el detalle de un evento no piden sesión
  it('sin sesión la portada y el detalle de un evento se ven', async () => {
    expect(await navegarComo(null, '/')).toBe('/');
    expect(await navegarComo(null, '/catalogo/5')).toBe('/catalogo/5');
  });

  it('el panel pide sesión', async () => {
    expect(await navegarComo(null, '/panel')).toBe('/inicio-sesion?destino=%2Fpanel');
    expect(await navegarComo('ADMINISTRADOR', '/panel')).toBe('/panel');
  });

  // CU-06 RF-23 y RF-24: comprar es del Cliente; sin sesión se entra y se vuelve al tipo elegido
  it('la compra es del cliente y sin sesión pide entrar', async () => {
    expect(await navegarComo('CLIENTE', '/catalogo/5/entradas/1')).toBe('/catalogo/5/entradas/1');
    expect(await navegarComo(null, '/catalogo/5/entradas/1')).toBe(
      '/inicio-sesion?destino=%2Fcatalogo%2F5%2Fentradas%2F1',
    );
    expect(await navegarComo('ADMINISTRADOR', '/catalogo/5/entradas/1')).toBe('/');
    expect(await navegarComo('PERSONAL', '/catalogo/5/entradas/1')).toBe('/');
  });

  it('la reserva es del cliente y sin sesión pide entrar', async () => {
    expect(await navegarComo('CLIENTE', '/reservas/40')).toBe('/reservas/40');
    expect(await navegarComo(null, '/reservas/40')).toBe('/inicio-sesion?destino=%2Freservas%2F40');
    expect(await navegarComo('ADMINISTRADOR', '/reservas/40')).toBe('/');
  });

  // CU-08: las entradas son del cliente
  it('mis entradas son del cliente y sin sesión pide entrar', async () => {
    expect(await navegarComo('CLIENTE', '/mis-entradas')).toBe('/mis-entradas');
    expect(await navegarComo(null, '/mis-entradas')).toBe('/inicio-sesion?destino=%2Fmis-entradas');
    expect(await navegarComo('ADMINISTRADOR', '/mis-entradas')).toBe('/');
    expect(await navegarComo('PERSONAL', '/mis-entradas')).toBe('/');
  });

  it('el detalle de una entrada es del cliente y sin sesión pide entrar', async () => {
    expect(await navegarComo('CLIENTE', '/mis-entradas/81')).toBe('/mis-entradas/81');
    expect(await navegarComo(null, '/mis-entradas/81')).toBe('/inicio-sesion?destino=%2Fmis-entradas%2F81');
    expect(await navegarComo('ADMINISTRADOR', '/mis-entradas/81')).toBe('/');
  });

  // CU-13: las devoluciones son del administrador
  it('las devoluciones son del administrador', async () => {
    expect(await navegarComo('ADMINISTRADOR', '/devoluciones')).toBe('/devoluciones');
    expect(await navegarComo('CLIENTE', '/devoluciones')).toBe('/');
    expect(await navegarComo('PERSONAL', '/devoluciones')).toBe('/');
    expect(await navegarComo(null, '/devoluciones')).toBe('/inicio-sesion?destino=%2Fdevoluciones');
    expect(await navegarComo('ADMINISTRADOR', '/devoluciones/5')).toBe('/devoluciones/5');
    expect(await navegarComo('CLIENTE', '/devoluciones/5')).toBe('/');
  });

  // CU-14: los reportes son del administrador
  it('los reportes son del administrador', async () => {
    expect(await navegarComo('ADMINISTRADOR', '/reportes')).toBe('/reportes');
    expect(await navegarComo('CLIENTE', '/reportes')).toBe('/');
    expect(await navegarComo('PERSONAL', '/reportes')).toBe('/');
    expect(await navegarComo(null, '/reportes')).toBe('/inicio-sesion?destino=%2Freportes');
  });

  // CU-15: validar entradas es del personal autorizado
  it('validar entradas es del personal autorizado', async () => {
    expect(await navegarComo('PERSONAL', '/validar')).toBe('/validar');
    expect(await navegarComo('CLIENTE', '/validar')).toBe('/');
    expect(await navegarComo('ADMINISTRADOR', '/validar')).toBe('/');
    expect(await navegarComo(null, '/validar')).toBe('/inicio-sesion?destino=%2Fvalidar');
  });

  it('sin sesión la gestión de eventos lleva a iniciar sesión', async () => {
    expect(await navegarComo(null, '/eventos?estado=ACTIVO')).toBe(
      '/inicio-sesion?destino=%2Feventos%3Festado%3DACTIVO',
    );
  });

  // decisiones.md → «Contador de visitas por página»: cada pantalla dice qué página cuenta.
  // Las pestañas de la ficha de un evento la toman de su ruta padre
  it('cada pantalla tiene su página para el contador de visitas, sin repetir', () => {
    const pantallas = routes.filter((ruta) => ruta.path !== '**');
    const paginas = pantallas.map((ruta) => ruta.data?.['pagina']);

    expect(paginas.every((pagina) => PAGINAS_VISITADAS.includes(pagina))).toBe(true);
    expect(new Set(paginas).size).toBe(pantallas.length);
    expect(new Set(paginas).size).toBe(PAGINAS_VISITADAS.length);
  });
});
