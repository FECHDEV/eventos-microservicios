import { HttpErrorResponse } from '@angular/common/http';
import {
	afterNextRender,
	ChangeDetectionStrategy,
	Component,
	computed,
	DestroyRef,
	effect,
	ElementRef,
	inject,
	Injector,
	signal,
	untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { comoErrorApi } from '../../../core/api/autenticacion.service';
import { mensajeDeError } from '../../../core/api/mensajes-error';
import { ReservasService } from '../../../core/api/reservas.service';
import { ErrorApi } from '../../../core/modelos/error-api';
import { terminaAlDiaSiguiente } from '../../../core/modelos/evento';
import { Pago, ResultadoSimulado } from '../../../core/modelos/pago';
import { fechaCorta, horaCorta } from '../../../shared/formato/fechas';
import { cantidad, precio } from '../../../shared/formato/precios';
import { CodigoQr } from '../../../shared/qr/codigo-qr';
import { idDeLaUrl } from '../../../shared/navegacion/id-de-la-url';

// La reserva recién hecha o vuelta a abrir por su URL (CU-06 RF-27), con el
// tiempo que le queda, y su pago (CU-07): el cobro con su QR, la consulta
// periódica mientras está pendiente y el resultado. Quien decide si se pagó es
// el backend, consultando al sistema de pago. Solo el Cliente llega (rolGuard).

// RF-25: cada cuánto se pregunta por un pago pendiente
const INTERVALO_DE_CONSULTA = 3000;
@Component({
	selector: 'app-reserva',
	imports: [RouterLink, CodigoQr],
	templateUrl: './reserva.page.html',
	styleUrl: './reserva.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReservaPage {
	private readonly reservas = inject(ReservasService);
	private readonly elemento = inject(ElementRef<HTMLElement>);
	private readonly injector = inject(Injector);
	private readonly parametros = toSignal(inject(ActivatedRoute).paramMap, { requireSync: true });

	// Basura en la URL no llega al backend
	private readonly id = computed(() => idDeLaUrl(this.parametros().get('id')));
	protected readonly recurso = this.reservas.reserva(this.id);
	protected readonly reserva = computed(() => (this.recurso.hasValue() ? this.recurso.value() : undefined));

	// La de otro cliente responde 404 igual que una inexistente (RF-20)
	protected readonly noEncontrada = computed(() => {
		const error = this.recurso.error();
		return this.id() === null || (error instanceof HttpErrorResponse && error.status === 404);
	});
	protected readonly errorDeCarga = computed(() => {
		const error = this.recurso.error();
		return error === undefined || this.noEncontrada() ? '' : mensajeDeError(comoErrorApi(error));
	});

	// La cuenta arranca de los segundos que calculó el servidor, no del reloj ni
	// de la zona del navegador: al llegar la reserva se fija el instante del fin
	private readonly ahora = signal(Date.now());
	private readonly venceEn = computed(() => {
		const reserva = this.reserva();
		return reserva === undefined ? null : Date.now() + reserva.segundosRestantes * 1000;
	});
	protected readonly segundos = computed(() => {
		const fin = this.venceEn();
		return fin === null ? 0 : Math.max(0, Math.ceil((fin - this.ahora()) / 1000));
	});
	// --- Pago (CU-07) ---

	// El cobro, cuando existe. Sin cobro (404 PAGO_NO_ENCONTRADO) se ofrece «Pagar»
	protected readonly pago = signal<Pago | null>(null);
	protected readonly sinCobro = signal(false);
	protected readonly errorDelPago = signal<ErrorApi | null>(null);
	protected readonly pagando = signal(false);
	protected readonly simulando = signal(false);
	// FA-01 (o la reserva que venció justo al pagar): las entradas ya se liberaron
	protected readonly cobroFallido = signal<ErrorApi | null>(null);

	protected readonly confirmada = computed(() => this.pago()?.estado === 'PAGADO' && !this.pago()?.requiereDevolucion);
	protected readonly textoConfirmada = computed(() => {
		const entradas = this.pago()?.entradas ?? 0;
		return entradas === 1
			? 'Compra confirmada. Te enviamos tu entrada por correo.'
			: `Compra confirmada. Te enviamos tus ${entradas} entradas por correo.`;
	});
	protected readonly paraDevolucion = computed(
		() => this.pago()?.estado === 'PAGADO' && !!this.pago()?.requiereDevolucion,
	);
	protected readonly conError = computed(() => this.pago()?.estado === 'ERROR');
	// PAGADO o ERROR: el sistema de pago ya respondió y no hay más que esperar
	private readonly terminado = computed(
		() => this.pago()?.estado === 'PAGADO' || this.conError() || this.cobroFallido() !== null,
	);

	// La cuenta en cero solo vence a una VIGENTE: una CONFIRMADA (pagada) también
	// llega con 0 segundos. Un pago ya resuelto dice lo suyo en lugar del vencimiento
	protected readonly vencida = computed(() => {
		if (this.terminado()) {
			return false;
		}
		const estado = this.reserva()?.estado;
		return (
			estado === 'EXPIRADA' || this.pago()?.estado === 'EXPIRADO' || (estado === 'VIGENTE' && this.segundos() === 0)
		);
	});
	protected readonly contando = computed(
		() => this.reserva()?.estado === 'VIGENTE' && !this.vencida() && !this.terminado(),
	);
	protected readonly ofrecePagar = computed(() => this.sinCobro() && this.contando());
	protected readonly esperandoPago = computed(() => this.pago()?.estado === 'PENDIENTE' && this.contando());
	protected readonly cuenta = computed(() => {
		const segundos = this.segundos();
		const minutos = Math.floor(segundos / 60);
		return `${String(minutos).padStart(2, '0')}:${String(segundos % 60).padStart(2, '0')}`;
	});

	protected readonly fecha = fechaCorta;
	protected readonly hora = horaCorta;
	protected readonly precio = precio;
	protected readonly cantidad = cantidad;
	protected readonly terminaAlDiaSiguiente = terminaAlDiaSiguiente;

	constructor() {
		const reloj = setInterval(() => this.ahora.set(Date.now()), 1000);
		inject(DestroyRef).onDestroy(() => clearInterval(reloj));
		void this.consultarAlAbrir();
		// RF-25: solo mientras el pago está pendiente y la reserva vigente. Al
		// llegar a un estado final, al vencer o al salir, el intervalo se limpia
		effect((alLimpiar) => {
			if (!this.esperandoPago()) {
				return;
			}
			const consulta = setInterval(() => void this.consultar(), INTERVALO_DE_CONSULTA);
			alLimpiar(() => clearInterval(consulta));
		});
		// Al vencer con el pago pendiente se pregunta una última vez: pudo llegar después de la última vuelta
		effect(() => {
			if (this.vencida() && untracked(this.pago)?.estado === 'PENDIENTE') {
				void untracked(() => this.consultar());
			}
		});
	}

	// Una sola vez: dice si ya hay cobro (y cómo va) o si hay que ofrecer «Pagar»
	protected async consultarAlAbrir(): Promise<void> {
		const id = this.id();
		if (id === null) {
			return;
		}
		this.errorDelPago.set(null);
		try {
			this.pago.set(await this.reservas.consultarPago(id));
		} catch (error) {
			const rechazo = error as ErrorApi;
			if (rechazo.codigo === 'PAGO_NO_ENCONTRADO') {
				this.sinCobro.set(true);
			} else if (rechazo.codigo !== 'RESERVA_NO_ENCONTRADA') {
				// La reserva ajena o inexistente ya la informa la carga de la reserva
				this.errorDelPago.set(rechazo);
			}
		}
	}

	// Un error de red no corta el ciclo: se vuelve a preguntar en la próxima vuelta
	private async consultar(): Promise<void> {
		const id = this.id();
		if (id === null) {
			return;
		}
		try {
			this.pago.set(await this.reservas.consultarPago(id));
		} catch {
			// Se reintenta en la próxima consulta
		}
	}

	// RF-3 y RF-4: el botón se deshabilita mientras pide el cobro
	protected async pagar(): Promise<void> {
		const id = this.id();
		if (id === null || this.pagando()) {
			return;
		}
		this.pagando.set(true);
		this.errorDelPago.set(null);
		try {
			this.pago.set(await this.reservas.pagar(id));
			this.sinCobro.set(false);
			// «Pagar» desaparece: el foco pasa al cobro en lugar de perderse en la página
			afterNextRender(
				() => (this.elemento.nativeElement as HTMLElement).querySelector<HTMLElement>('#titulo-cobro')?.focus(),
				{ injector: this.injector },
			);
		} catch (error) {
			const rechazo = error as ErrorApi;
			if (rechazo.codigo === 'ERROR_SISTEMA_PAGO' || rechazo.codigo === 'RESERVA_NO_VIGENTE') {
				this.cobroFallido.set(rechazo);
			} else {
				this.errorDelPago.set(rechazo);
			}
		} finally {
			this.pagando.set(false);
		}
	}

	// RF-27: herramienta de pruebas, solo con el simulador
	protected async simular(resultado: ResultadoSimulado): Promise<void> {
		const id = this.id();
		if (id === null || this.simulando()) {
			return;
		}
		this.simulando.set(true);
		this.errorDelPago.set(null);
		try {
			await this.reservas.simularPago(id, resultado);
			await this.consultar();
		} catch (error) {
			this.errorDelPago.set(error as ErrorApi);
		} finally {
			this.simulando.set(false);
		}
	}

	protected mensaje(error: ErrorApi | null): string {
		return mensajeDeError(error);
	}
}
