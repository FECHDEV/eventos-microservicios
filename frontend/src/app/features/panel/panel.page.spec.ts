import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Sesion } from '../../core/modelos/sesion';
import { Rol } from '../../core/modelos/usuario';
import { SesionService } from '../../core/sesion/sesion.service';
import { PanelPage } from './panel.page';

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

describe('PanelPage', () => {
  let fijo: ComponentFixture<PanelPage>;
  let sesion: SesionService;

  beforeEach(async () => {
    localStorage.clear();
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [PanelPage],
      providers: [provideRouter([])],
    }).compileComponents();
    sesion = TestBed.inject(SesionService);
  });

  function crear() {
    fijo = TestBed.createComponent(PanelPage);
    fijo.detectChanges();
    return fijo.nativeElement as HTMLElement;
  }

  it('saluda a la persona por su nombre', () => {
    sesion.iniciar(sesionCon('CLIENTE'));

    expect(crear().textContent).toContain('Ana');
  });

  // Pedido en la prueba manual: el lema de la marca sobraba
  it('no muestra el lema de la marca', () => {
    sesion.iniciar(sesionCon('ADMINISTRADOR'));

    expect(crear().textContent).not.toContain('Santa Cruz');
  });

  it('un cliente no ve la gestión de usuarios', () => {
    sesion.iniciar(sesionCon('CLIENTE'));

    expect(crear().textContent).not.toContain('Usuarios');
  });

  // Ocultarlo es usabilidad: quien autoriza de verdad es el backend
  it('un administrador ve la gestión de usuarios', () => {
    sesion.iniciar(sesionCon('ADMINISTRADOR'));

    expect(crear().textContent).toContain('Usuarios');
  });

  it('todos ven sus datos personales', () => {
    sesion.iniciar(sesionCon('PERSONAL'));

    expect(crear().textContent).toContain('Mis datos');
  });

  // CU-04: el acceso ya lleva a la pantalla
  it('el acceso a mis datos lleva a su pantalla', () => {
    sesion.iniciar(sesionCon('CLIENTE'));

    const enlace = crear().querySelector<HTMLAnchorElement>('a[href="/mis-datos"]');

    expect(enlace?.textContent).toContain('Mis datos');
    expect(fijo.nativeElement.textContent).not.toContain('CU-04');
  });

  // La interfaz es de Sarao: nada de jerga interna del proyecto a la vista
  it('no muestra referencias a casos de uso', () => {
    sesion.iniciar(sesionCon('ADMINISTRADOR'));

    expect(crear().textContent).not.toMatch(/CU-\d/);
  });

  // CU-09: el acceso del administrador ya lleva a su pantalla
  it('el acceso a usuarios lleva a su pantalla', () => {
    sesion.iniciar(sesionCon('ADMINISTRADOR'));

    const enlace = crear().querySelector<HTMLAnchorElement>('a[href="/usuarios"]');

    expect(enlace?.textContent).toContain('Usuarios');
    expect(fijo.nativeElement.textContent).not.toContain('Disponible pronto');
  });

  // CU-10, CU-11 y CU-12: solo el administrador ve la gestión de eventos
  it('el acceso a eventos es del administrador y lleva a su pantalla', () => {
    sesion.iniciar(sesionCon('ADMINISTRADOR'));

    const enlace = crear().querySelector<HTMLAnchorElement>('a[href="/eventos"]');

    expect(enlace?.textContent).toContain('Eventos');
  });

  it('un cliente no ve la gestión de eventos', () => {
    sesion.iniciar(sesionCon('CLIENTE'));

    expect(crear().querySelector('a[href="/eventos"]')).toBeNull();
  });

  // CU-15: el personal autorizado entra a validar entradas desde el panel
  it('el acceso a validar entradas es del personal autorizado', () => {
    sesion.iniciar(sesionCon('PERSONAL'));

    const enlace = crear().querySelector<HTMLAnchorElement>('a[href="/validar"]');

    expect(enlace?.textContent).toContain('Validar entradas');
  });

  it('el administrador no ve validar entradas', () => {
    sesion.iniciar(sesionCon('ADMINISTRADOR'));

    expect(crear().querySelector('a[href="/validar"]')).toBeNull();
  });

  // CU-13: el acceso a devoluciones, como los otros módulos del administrador
  it('el acceso a devoluciones es del administrador y lleva a su pantalla', () => {
    sesion.iniciar(sesionCon('ADMINISTRADOR'));

    const enlace = crear().querySelector<HTMLAnchorElement>('a[href="/devoluciones"]');

    expect(enlace?.textContent).toContain('Devoluciones');
  });

  it('un cliente no ve las devoluciones', () => {
    sesion.iniciar(sesionCon('CLIENTE'));

    expect(crear().querySelector('a[href="/devoluciones"]')).toBeNull();
  });

  // CU-14: el acceso a los reportes
  it('el acceso a reportes es del administrador y lleva a su pantalla', () => {
    sesion.iniciar(sesionCon('ADMINISTRADOR'));

    const enlace = crear().querySelector<HTMLAnchorElement>('a[href="/reportes"]');

    expect(enlace?.textContent).toContain('Reportes');
  });

  it('el personal no ve los reportes', () => {
    sesion.iniciar(sesionCon('PERSONAL'));

    expect(crear().querySelector('a[href="/reportes"]')).toBeNull();
  });
});
