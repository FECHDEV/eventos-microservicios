import { HttpClient, httpResource, HttpResourceRef } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import {
	EnvioDeReporte,
	FormatoDeArchivo,
	OpcionesDeReporte,
	Reporte,
	SolicitudDeEnvio,
	SolicitudDeReporte,
} from '../modelos/reporte';
import { escribir } from './escribir';

// Llamadas a /api/reportes (CU-14). Solo el Administrador llega: el backend lo
// valida en cada petición, la pantalla solo evita mostrarse a otros roles.
@Injectable({ providedIn: 'root' })
export class ReportesService {
	private readonly http = inject(HttpClient);

	// httpResource necesita un contexto de inyección: se llama al construir la pantalla
	opciones(): HttpResourceRef<OpcionesDeReporte | undefined> {
		return httpResource<OpcionesDeReporte>(() => '/api/reportes/opciones');
	}

	// Generar registra el reporte (RF-13), así que es una escritura. Sin
	// resultados rechaza con REPORTE_SIN_RESULTADOS (FA-02)
	generar(solicitud: SolicitudDeReporte): Promise<Reporte> {
		return escribir(this.http.post<Reporte>('/api/reportes', solicitud));
	}

	// Un enlace no puede mandar el token: el archivo se trae como Blob y la
	// pantalla lo descarga con una URL de objeto
	exportar(idReporte: number, formato: FormatoDeArchivo): Promise<Blob> {
		return escribir(this.http.get(`/api/reportes/${idReporte}/${formato}`, { responseType: 'blob' }));
	}

	// El envío sale en segundo plano: la API responde 202 sin esperar al servidor
	// de correo (RF-23, RF-24)
	enviarPorCorreo(idReporte: number, solicitud: SolicitudDeEnvio): Promise<EnvioDeReporte> {
		return escribir(this.http.post<EnvioDeReporte>(`/api/reportes/${idReporte}/correo`, solicitud));
	}
}
