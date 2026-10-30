import { Routes } from '@angular/router';

import { cambiosSinGuardarGuard } from './core/guards/cambios-sin-guardar.guard';
import { rolGuard } from './core/guards/rol.guard';
import { sesionGuard } from './core/guards/sesion.guard';

// Cada pantalla se carga cuando hace falta, para que el paquete inicial no
// arrastre las pantallas de administración. data.pagina es la página que suma
// visitas en el pie (core/modelos/visitas.ts); las pestañas de la ficha de un
// evento cuentan como su ruta padre.
export const routes: Routes = [
  {
    path: 'inicio-sesion',
    title: 'Iniciar sesión · Sarao',
    data: { pagina: 'INICIO_SESION' },
    loadComponent: () =>
      import('./features/inicio-sesion/inicio-sesion.page').then((m) => m.InicioSesionPage),
  },
  {
    path: 'registro',
    title: 'Crear cuenta · Sarao',
    data: { pagina: 'REGISTRO' },
    loadComponent: () => import('./features/registro/registro.page').then((m) => m.RegistroPage),
  },
  {
    path: 'recuperacion',
    title: 'Recuperar contraseña · Sarao',
    data: { pagina: 'RECUPERACION' },
    loadComponent: () =>
      import('./features/recuperacion/recuperacion.page').then((m) => m.RecuperacionPage),
  },
  {
    path: 'restablecer',
    title: 'Contraseña nueva · Sarao',
    data: { pagina: 'RESTABLECIMIENTO' },
    loadComponent: () =>
      import('./features/restablecer/restablecer.page').then((m) => m.RestablecerPage),
  },
  // CU-05: la portada es el catálogo, con o sin sesión
  {
    path: '',
    pathMatch: 'full',
    title: 'Sarao',
    data: { pagina: 'PORTADA' },
    loadComponent: () => import('./features/catalogo/lista/catalogo.page').then((m) => m.CatalogoPage),
  },
  // CU-06: comprar es del Cliente. Sin sesión, el guard manda a iniciar sesión y
  // vuelve acá con el tipo elegido. Antes que el detalle, que es más corto
  {
    path: 'catalogo/:id/entradas/:idTipo',
    title: 'Comprar entradas · Sarao',
    canActivate: [rolGuard],
    data: { rol: 'CLIENTE', pagina: 'SELECCION_DE_ENTRADAS' },
    loadComponent: () =>
      import('./features/compra/seleccion/seleccion-de-entradas.page').then((m) => m.SeleccionDeEntradasPage),
  },
  // CU-06 RF-27: la reserva recién hecha, o vuelta a abrir por su URL
  {
    path: 'reservas/:id',
    title: 'Tu reserva · Sarao',
    canActivate: [rolGuard],
    data: { rol: 'CLIENTE', pagina: 'RESERVA' },
    loadComponent: () => import('./features/compra/reserva/reserva.page').then((m) => m.ReservaPage),
  },
  // CU-08: las entradas de las compras confirmadas del Cliente
  {
    path: 'mis-entradas',
    title: 'Mis entradas · Sarao',
    canActivate: [rolGuard],
    data: { rol: 'CLIENTE', pagina: 'MIS_ENTRADAS' },
    loadComponent: () => import('./features/entradas/lista/mis-entradas.page').then((m) => m.MisEntradasPage),
  },
  {
    path: 'mis-entradas/:id',
    title: 'Tu entrada · Sarao',
    canActivate: [rolGuard],
    data: { rol: 'CLIENTE', pagina: 'DETALLE_DE_ENTRADA' },
    loadComponent: () => import('./features/entradas/detalle/entrada.page').then((m) => m.EntradaPage),
  },
  {
    path: 'catalogo/:id',
    title: 'Evento · Sarao',
    data: { pagina: 'DETALLE_DE_EVENTO' },
    loadComponent: () =>
      import('./features/catalogo/detalle/evento-del-catalogo.page').then((m) => m.EventoDelCatalogoPage),
  },
  // La bienvenida con los accesos de cada rol (CU-05 RF-20)
  {
    path: 'panel',
    title: 'Panel · Sarao',
    canActivate: [sesionGuard],
    data: { pagina: 'PANEL' },
    loadComponent: () => import('./features/panel/panel.page').then((m) => m.PanelPage),
  },
  {
    path: 'mis-datos',
    title: 'Mis datos · Sarao',
    canActivate: [sesionGuard],
    canDeactivate: [cambiosSinGuardarGuard],
    data: { pagina: 'MIS_DATOS' },
    loadComponent: () => import('./features/mis-datos/mis-datos.page').then((m) => m.MisDatosPage),
  },
  {
    path: 'usuarios',
    title: 'Usuarios · Sarao',
    canActivate: [rolGuard],
    data: { rol: 'ADMINISTRADOR', pagina: 'USUARIOS' },
    loadComponent: () => import('./features/usuarios/usuarios.page').then((m) => m.UsuariosPage),
  },
  {
    path: 'eventos',
    title: 'Eventos · Sarao',
    canActivate: [rolGuard],
    data: { rol: 'ADMINISTRADOR', pagina: 'EVENTOS' },
    loadComponent: () => import('./features/eventos/lista/eventos.page').then((m) => m.EventosPage),
  },
  // Antes que la ficha: «nuevo» no es un id
  {
    path: 'eventos/nuevo',
    title: 'Nuevo evento · Sarao',
    canActivate: [rolGuard],
    canDeactivate: [cambiosSinGuardarGuard],
    data: { rol: 'ADMINISTRADOR', pagina: 'NUEVO_EVENTO' },
    loadComponent: () =>
      import('./features/eventos/datos/datos-evento.page').then((m) => m.DatosEventoPage),
  },
  // La ficha: las pestañas Datos, Tipos de entrada y Estado son rutas hijas
  {
    path: 'eventos/:id',
    title: 'Evento · Sarao',
    canActivate: [rolGuard],
    data: { rol: 'ADMINISTRADOR', pagina: 'FICHA_DE_EVENTO' },
    loadComponent: () => import('./features/eventos/ficha/evento.page').then((m) => m.EventoPage),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'datos' },
      {
        path: 'datos',
        canDeactivate: [cambiosSinGuardarGuard],
        loadComponent: () =>
          import('./features/eventos/datos/datos-evento.page').then((m) => m.DatosEventoPage),
      },
      {
        path: 'tipos',
        canDeactivate: [cambiosSinGuardarGuard],
        loadComponent: () =>
          import('./features/eventos/tipos/tipos-entrada.page').then((m) => m.TiposEntradaPage),
      },
      {
        path: 'estado',
        loadComponent: () =>
          import('./features/eventos/estado/estado-evento.page').then((m) => m.EstadoEventoPage),
      },
    ],
  },
  // CU-13: las devoluciones de los eventos cancelados son del Administrador
  {
    path: 'devoluciones',
    title: 'Devoluciones · Sarao',
    canActivate: [rolGuard],
    data: { rol: 'ADMINISTRADOR', pagina: 'DEVOLUCIONES' },
    loadComponent: () => import('./features/devoluciones/lista/devoluciones.page').then((m) => m.DevolucionesPage),
  },
  {
    path: 'devoluciones/:id',
    title: 'Devoluciones del evento · Sarao',
    canActivate: [rolGuard],
    data: { rol: 'ADMINISTRADOR', pagina: 'DEVOLUCIONES_DEL_EVENTO' },
    loadComponent: () =>
      import('./features/devoluciones/evento/devoluciones-del-evento.page').then((m) => m.DevolucionesDelEventoPage),
  },
  // CU-14: los reportes son del Administrador
  {
    path: 'reportes',
    title: 'Reportes · Sarao',
    canActivate: [rolGuard],
    data: { rol: 'ADMINISTRADOR', pagina: 'REPORTES' },
    loadComponent: () => import('./features/reportes/reportes.page').then((m) => m.ReportesPage),
  },
  // CU-15: validar entradas en la puerta es del Personal autorizado
  {
    path: 'validar',
    title: 'Validar entradas · Sarao',
    canActivate: [rolGuard],
    data: { rol: 'PERSONAL', pagina: 'VALIDACION' },
    loadComponent: () => import('./features/validacion/validar-entradas.page').then((m) => m.ValidarEntradasPage),
  },
  { path: '**', redirectTo: '' },
];
