import {
	afterNextRender,
	ChangeDetectionStrategy,
	Component,
	DestroyRef,
	ElementRef,
	inject,
	InjectionToken,
	NgZone,
	output,
	signal,
	viewChild,
} from '@angular/core';
import jsQR from 'jsqr';

// El cuadro se achica a este ancho antes de decodificarlo: un QR en la guía
// se lee igual y jsqr trabaja sobre una fracción de los píxeles
const ANCHO_PARA_LEER = 640;

// Entre dos lecturas: unas 10 por segundo alcanzan para la puerta
const PAUSA_ENTRE_LECTURAS_MS = 100;

// Lee el QR de un cuadro del video: dibuja el cuadro en un canvas y se lo pasa
// a jsqr. Es un token para que los tests lo reemplacen: jsdom no dibuja
export const LEER_CUADRO_QR = new InjectionToken<(video: HTMLVideoElement) => string | null>('LEER_CUADRO_QR', {
	providedIn: 'root',
	factory: () => {
		const lienzo = document.createElement('canvas');
		return (video) => {
			if (video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA || video.videoWidth === 0) {
				return null;
			}
			// Cambiar el tamaño del canvas lo vuelve a reservar: solo si cambió
			const escala = Math.min(1, ANCHO_PARA_LEER / video.videoWidth);
			const ancho = Math.round(video.videoWidth * escala);
			const alto = Math.round(video.videoHeight * escala);
			if (lienzo.width !== ancho || lienzo.height !== alto) {
				lienzo.width = ancho;
				lienzo.height = alto;
			}
			const contexto = lienzo.getContext('2d', { willReadFrequently: true });
			if (contexto === null) {
				return null;
			}
			contexto.drawImage(video, 0, 0, lienzo.width, lienzo.height);
			const imagen = contexto.getImageData(0, 0, lienzo.width, lienzo.height);
			return jsQR(imagen.data, imagen.width, imagen.height, { inversionAttempts: 'dontInvert' })?.data ?? null;
		};
	},
});

// Abre la cámara trasera y emite el texto de cada QR que lee (CU-15 RF-13).
// Emite en cada cuadro que lo encuentra: quien lo usa decide si repetir. Sin
// cámara, o sin permiso, lo dice y no emite nada; al destruirse apaga la cámara.
@Component({
	selector: 'app-lector-qr',
	template: `
		@if (sinCamara()) {
			<p class="sin-camara" role="status">No pudimos usar la cámara. Escribe el código de la entrada.</p>
		} @else {
			<div class="visor">
				<video #video playsinline muted aria-label="Cámara para leer el código QR"></video>
				<span class="guia" aria-hidden="true"></span>
			</div>
		}
	`,
	styleUrl: './lector-qr.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LectorQr {
	readonly leido = output<string>();

	private readonly leerCuadro = inject(LEER_CUADRO_QR);
	private readonly zona = inject(NgZone);
	private readonly video = viewChild<ElementRef<HTMLVideoElement>>('video');
	protected readonly sinCamara = signal(false);

	private stream: MediaStream | null = null;
	private cuadro: number | null = null;
	private activo = true;

	constructor() {
		afterNextRender(() => void this.encender());
		inject(DestroyRef).onDestroy(() => this.apagar());
	}

	private async encender(): Promise<void> {
		const medios = navigator.mediaDevices;
		if (!medios?.getUserMedia) {
			this.sinCamara.set(true);
			return;
		}
		try {
			const stream = await medios.getUserMedia({ video: { facingMode: 'environment' }, audio: false });
			// Si la pantalla se cerró mientras se pedía el permiso, la cámara se apaga enseguida
			if (!this.activo) {
				detener(stream);
				return;
			}
			this.stream = stream;
			const video = this.video()?.nativeElement;
			if (video === undefined) {
				throw new Error('Sin video');
			}
			video.srcObject = stream;
			await video.play();
			// Fuera de la zona: un cuadro sin QR no hace revisar toda la pantalla
			this.zona.runOutsideAngular(() => this.leer(video, 0));
		} catch {
			// Si la cámara quedó abierta pero no se puede mostrar, se apaga
			if (this.stream !== null) {
				detener(this.stream);
				this.stream = null;
			}
			this.sinCamara.set(true);
		}
	}

	private leer(video: HTMLVideoElement, ultimaLectura: number): void {
		this.cuadro = requestAnimationFrame((ahora) => {
			if (!this.activo) {
				return;
			}
			if (ahora - ultimaLectura < PAUSA_ENTRE_LECTURAS_MS) {
				this.leer(video, ultimaLectura);
				return;
			}
			const texto = this.leerCuadro(video);
			if (texto !== null && texto !== '') {
				this.zona.run(() => this.leido.emit(texto));
			}
			this.leer(video, ahora);
		});
	}

	private apagar(): void {
		this.activo = false;
		if (this.cuadro !== null) {
			cancelAnimationFrame(this.cuadro);
		}
		if (this.stream !== null) {
			detener(this.stream);
		}
	}
}

function detener(stream: MediaStream): void {
	for (const pista of stream.getTracks()) {
		pista.stop();
	}
}
