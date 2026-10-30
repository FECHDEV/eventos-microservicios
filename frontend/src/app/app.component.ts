import { ChangeDetectionStrategy, Component, computed, inject, linkedSignal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { form, FormField, maxLength } from '@angular/forms/signals';
import {
  ActivatedRouteSnapshot,
  convertToParamMap,
  NavigationEnd,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
  UrlTree,
} from '@angular/router';
import { catchError, concat, distinctUntilChanged, filter, map, of, switchMap } from 'rxjs';

import { VisitasService } from './core/api/visitas.service';
import { MARCA } from './core/marca';
import { FiltrosDelCatalogo } from './core/modelos/catalogo';
import { aConsulta, LARGO_MAXIMO_DEL_TEXTO, leerFiltros } from './core/modelos/filtros-en-la-url';
import { nombreCompleto, Rol } from './core/modelos/usuario';
import { PaginaVisitada } from './core/modelos/visitas';
import { SesionService } from './core/sesion/sesion.service';
import { TemaService } from './core/tema/tema.service';
import { Estilo, Modo } from './shared/apariencia/apariencia';
import { MenuAparienciaComponent } from './shared/apariencia/menu-apariencia.component';
import { cantidad } from './shared/formato/precios';

// Los módulos del encabezado, además de Inicio (la portada, el catálogo), que lo ven todos,
// con sesión o sin ella. Ocultarlos según el rol es usabilidad: quien autoriza
// es el backend
const MODULOS: readonly { ruta: string; nombre: string; roles: readonly Rol[] }[] = [
  { ruta: '/mis-entradas', nombre: 'Mis entradas', roles: ['CLIENTE'] },
  { ruta: '/panel', nombre: 'Panel', roles: ['ADMINISTRADOR', 'PERSONAL'] },
  { ruta: '/usuarios', nombre: 'Usuarios', roles: ['ADMINISTRADOR'] },
  { ruta: '/eventos', nombre: 'Eventos', roles: ['ADMINISTRADOR'] },
  { ruta: '/devoluciones', nombre: 'Devoluciones', roles: ['ADMINISTRADOR'] },
  { ruta: '/reportes', nombre: 'Reportes', roles: ['ADMINISTRADOR'] },
  { ruta: '/validar', nombre: 'Validar entradas', roles: ['PERSONAL'] },
];

@Component({
  selector: 'app-root',
  imports: [FormField, MenuAparienciaComponent, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './app.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './app.component.scss',
})
export class AppComponent {
  private readonly sesion = inject(SesionService);
  private readonly router = inject(Router);
  private readonly temas = inject(TemaService);
  private readonly visitas = inject(VisitasService);

  protected readonly marca = MARCA;
  protected readonly autenticado = this.sesion.autenticado;
  protected readonly modulos = computed(() => {
    const rol = this.sesion.rol();
    return rol === null ? [] : MODULOS.filter((modulo) => modulo.roles.includes(rol));
  });
  protected readonly nombre = computed(() => {
    const usuario = this.sesion.usuario();
    return usuario === null ? '' : nombreCompleto(usuario);
  });

  // El menú «Apariencia» (decisiones.md → «Tres estilos y modo día/noche»)
  protected readonly estilo = this.temas.estilo;
  protected readonly modo = this.temas.modo;

  // El buscador muestra el texto que filtra la portada; en otra pantalla queda vacío.
  // Es un string: otra navegación con el mismo texto (paginar, filtrar por fecha)
  // no pisa lo que se está escribiendo
  private readonly url = toSignal(
    this.router.events.pipe(
      filter((evento) => evento instanceof NavigationEnd),
      map((evento) => this.router.parseUrl(evento.urlAfterRedirects)),
    ),
    { initialValue: this.router.parseUrl(this.router.url) },
  );
  private readonly textoDeLaPortada = computed(() => filtrosDeLaPortada(this.url())?.texto ?? '');
  private readonly busqueda = linkedSignal(() => ({ texto: this.textoDeLaPortada() }));
  protected readonly buscador = form(this.busqueda, (ruta) => maxLength(ruta.texto, LARGO_MAXIMO_DEL_TEXTO));

  // Una búsqueda nueva empieza en la primera página. En la portada se conservan las
  // fechas, leídas como las lee el catálogo. Un texto de más de 200 caracteres (que
  // solo llega pegado en la URL) no se manda: el backend lo rechazaría
  protected buscar(evento: Event): void {
    evento.preventDefault();
    if (this.buscador().invalid()) {
      return;
    }
    const texto = this.busqueda().texto.trim();
    const portada = filtrosDeLaPortada(this.url());
    const filtros: FiltrosDelCatalogo = {
      texto: texto === '' ? null : texto,
      desde: portada?.desde ?? null,
      hasta: portada?.hasta ?? null,
      pagina: 0,
    };
    void this.router.navigate(['/'], { queryParams: aConsulta(filtros) });
  }

  // El pie (decisiones.md → «Contador de visitas por página»): abrir una pantalla
  // suma una visita a su página. Se compara la URL de la ruta que dice la página:
  // buscar o paginar (la consulta) y cambiar de pestaña en la ficha de un evento (la
  // ruta hija) no es abrir otra. Mientras se cuenta no queda el total de la
  // pantalla anterior, y si falla el pie no muestra nada: el contador no es motivo
  // para un aviso de error
  protected readonly visitasDeLaPagina = toSignal(
    this.router.events.pipe(
      filter((evento) => evento instanceof NavigationEnd),
      map(() => paginaVisitada(this.router.routerState.snapshot.root)),
      distinctUntilChanged((anterior, actual) => anterior?.ruta === actual?.ruta),
      switchMap((visitada) =>
        visitada === null
          ? of(null)
          : concat(of(null), this.visitas.registrar(visitada.pagina).pipe(catchError(() => of(null)))),
      ),
    ),
    { initialValue: null },
  );
  protected readonly cantidad = cantidad;

  protected elegirEstilo(estilo: Estilo): void {
    this.temas.elegirEstilo(estilo);
  }

  protected elegirModo(modo: Modo): void {
    this.temas.elegirModo(modo);
  }

  protected salir(): void {
    this.sesion.cerrar();
    void this.router.navigate(['/inicio-sesion']);
  }
}

// Los filtros de la URL si es la portada; en otra pantalla, ninguno
function filtrosDeLaPortada(url: UrlTree): FiltrosDelCatalogo | null {
  const esLaPortada = (url.root.children['primary']?.segments.length ?? 0) === 0;
  return esLaPortada ? leerFiltros(convertToParamMap(url.queryParams)) : null;
}

// La página de la pantalla abierta: la de la ruta más profunda que dice una, con
// la URL de esa ruta. Así las pestañas de la ficha de un evento cuentan como la
// ficha, y dos fichas distintas son dos visitas
function paginaVisitada(raiz: ActivatedRouteSnapshot): { pagina: PaginaVisitada; ruta: string } | null {
  let visitada: { pagina: PaginaVisitada; ruta: string } | null = null;
  for (let ruta: ActivatedRouteSnapshot | null = raiz; ruta !== null; ruta = ruta.firstChild) {
    const pagina = ruta.data['pagina'] as PaginaVisitada | undefined;
    if (pagina !== undefined && ruta.routeConfig?.data?.['pagina'] === pagina) {
      visitada = { pagina, ruta: ruta.pathFromRoot.flatMap((paso) => paso.url.map((segmento) => segmento.path)).join('/') };
    }
  }
  return visitada;
}
