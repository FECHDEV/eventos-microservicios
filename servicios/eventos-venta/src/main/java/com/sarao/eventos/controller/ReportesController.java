package com.sarao.eventos.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sarao.eventos.dto.EnvioDeReporteRequest;
import com.sarao.eventos.dto.EnvioDeReporteResponse;
import com.sarao.eventos.dto.OpcionesDeReporteResponse;
import com.sarao.eventos.dto.ReporteRequest;
import com.sarao.eventos.dto.ReporteResponse;
import com.sarao.eventos.service.EnvioDeReporte;
import com.sarao.eventos.service.ReporteService;
import com.sarao.eventos.service.reporte.ExportadorExcel;
import com.sarao.eventos.service.reporte.ExportadorPdf;
import com.sarao.eventos.service.reporte.ReporteGenerado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// CU-14. Solo el Administrador llega acá: /api/reportes/** en SecurityConfig
@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
public class ReportesController {

	private static final MediaType EXCEL = MediaType
			.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

	private final ReporteService reporteService;
	private final ExportadorPdf exportadorPdf;
	private final ExportadorExcel exportadorExcel;
	private final EnvioDeReporte envioDeReporte;

	@GetMapping("/opciones")
	public OpcionesDeReporteResponse opciones() {
		return OpcionesDeReporteResponse.from(reporteService.opciones(), envioDeReporte.dominio());
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ReporteResponse generar(@AuthenticationPrincipal Long idAdministrador,
			@Valid @RequestBody ReporteRequest solicitud) {
		return ReporteResponse.from(reporteService.generar(idAdministrador, solicitud.tipo(), solicitud.criterios()));
	}

	@GetMapping("/{idReporte}/pdf")
	public ResponseEntity<byte[]> pdf(@PathVariable Long idReporte) {
		ReporteGenerado generado = reporteService.paraExportar(idReporte);
		return archivo(exportadorPdf.exportar(generado), MediaType.APPLICATION_PDF, generado.nombreArchivo("pdf"));
	}

	@GetMapping("/{idReporte}/excel")
	public ResponseEntity<byte[]> excel(@PathVariable Long idReporte) {
		ReporteGenerado generado = reporteService.paraExportar(idReporte);
		return archivo(exportadorExcel.exportar(generado), EXCEL, generado.nombreArchivo("xlsx"));
	}

	// CU-14 RF-19 y RF-24: 202 Accepted. El correo sale en segundo plano, así que su resultado no cambia esta respuesta
	// (RF-23)
	@PostMapping("/{idReporte}/correo")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public EnvioDeReporteResponse enviarPorCorreo(@AuthenticationPrincipal Long idAdministrador,
			@PathVariable Long idReporte, @Valid @RequestBody EnvioDeReporteRequest solicitud) {
		return new EnvioDeReporteResponse(envioDeReporte.enviar(idAdministrador, idReporte, solicitud.destinatarios(),
				solicitud.formato()));
	}

	private static ResponseEntity<byte[]> archivo(byte[] contenido, MediaType tipo, String nombre) {
		return ResponseEntity.ok().contentType(tipo)
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(nombre).build().toString())
				.body(contenido);
	}
}
