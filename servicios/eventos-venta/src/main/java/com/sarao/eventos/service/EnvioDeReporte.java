package com.sarao.eventos.service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.sarao.eventos.exception.CamposInvalidosException;
import com.sarao.eventos.exception.CamposInvalidosException.CampoInvalido;
import com.sarao.eventos.service.reporte.ExportadorExcel;
import com.sarao.eventos.service.reporte.ExportadorPdf;
import com.sarao.eventos.service.reporte.FormatoDeEnvio;
import com.sarao.eventos.service.reporte.ReporteGenerado;
import com.sarao.eventos.service.reporte.Textos;
import lombok.extern.slf4j.Slf4j;

// CU-14 RF-19 a RF-24: el Administrador manda un reporte ya generado a varios buzones del dominio propio, en un solo
// correo. El archivo se arma con ReporteService.paraExportar, como en la descarga: los datos son los del momento del
// envío y no queda registrado otro reporte
@Slf4j
@Service
public class EnvioDeReporte {

	private static final String CAMPO = "destinatarios";
	private static final int MAXIMO_DESTINATARIOS = 10;
	// El largo máximo de una dirección de correo (RFC 5321)
	private static final int MAXIMO_LARGO = 254;
	// Ni un «ana» ni un «ana@mail». La parte local solo lleva los caracteres que una dirección admite sin comillas: una
	// coma, un «<» o un «;» pasarían acá y harían fallar después, en segundo plano, el envío a todos. Las partes del
	// dominio no llevan punto: así una dirección armada con muchos puntos no hace probar combinaciones
	private static final Pattern CORREO = Pattern
			.compile("^[a-z0-9!#$%&'*+/=?^_`{|}~.-]+@[a-z0-9-]+(\\.[a-z0-9-]+)+$");

	private final ReporteService reporteService;
	private final ExportadorPdf exportadorPdf;
	private final ExportadorExcel exportadorExcel;
	private final ConsultaDeClientes consultaDeClientes;
	private final PedidoDeCorreoDeReporte pedido;
	private final String dominio;

	public EnvioDeReporte(ReporteService reporteService, ExportadorPdf exportadorPdf, ExportadorExcel exportadorExcel,
			ConsultaDeClientes consultaDeClientes, PedidoDeCorreoDeReporte pedido,
			@Value("${eventos.correo.dominio}") String dominio) {
		this.reporteService = reporteService;
		this.exportadorPdf = exportadorPdf;
		this.exportadorExcel = exportadorExcel;
		this.consultaDeClientes = consultaDeClientes;
		this.pedido = pedido;
		this.dominio = dominio;
	}

	public int enviar(Long idAdministrador, Long idReporte, List<String> destinatarios, FormatoDeEnvio formato) {
		List<String> validos = validar(destinatarios);
		ReporteGenerado generado = reporteService.paraExportar(idReporte);
		String enviadoPor = Textos.nombre(consultaDeClientes.cuentas(List.of(idAdministrador)).get(idAdministrador));
		pedido.pedir(Map.of("titulo", Textos.titulo(generado.reporte().getTipo()), "enviadoPor", enviadoPor,
				"criterios", generado.criterios().stream().map(criterio -> criterio.nombre() + ": " + criterio.valor())
						.toList(),
				"datosAl", generado.exportado(), "destinatarios", validos, "adjuntos", adjuntos(generado, formato)));
		// Sin las direcciones ni el contenido: alcanza para saber después quién mandó qué
		log.info("El usuario {} envió por correo el reporte {} a {} destinatarios", idAdministrador, idReporte,
				validos.size());
		return validos.size();
	}

	// Para que la pantalla diga a qué dominio se puede mandar
	public String dominio() {
		return dominio;
	}

	private List<Map<String, String>> adjuntos(ReporteGenerado generado, FormatoDeEnvio formato) {
		if (formato == FormatoDeEnvio.PDF) {
			return List.of(pdf(generado));
		}
		if (formato == FormatoDeEnvio.EXCEL) {
			return List.of(excel(generado));
		}
		return List.of(pdf(generado), excel(generado));
	}

	// El contenido en base64: viaja dentro del JSON hacia Notificaciones
	private Map<String, String> pdf(ReporteGenerado generado) {
		return adjunto(generado.nombreArchivo("pdf"), exportadorPdf.exportar(generado));
	}

	private Map<String, String> excel(ReporteGenerado generado) {
		return adjunto(generado.nombreArchivo("xlsx"), exportadorExcel.exportar(generado));
	}

	private static Map<String, String> adjunto(String nombre, byte[] contenido) {
		return Map.of("nombre", nombre, "contenido", Base64.getEncoder().encodeToString(contenido));
	}

	// Todo se reporta bajo el mismo campo: la pantalla enseña los errores junto a la lista, nombrando la dirección que
	// falla. El dominio se exige porque el servidor de correo solo entrega al suyo (decisiones.md → 'Envío de reportes
	// por correo'): aceptarlo daría un «enviado» que nunca llega. Los mensajes completan «Los destinatarios …»
	private List<String> validar(List<String> destinatarios) {
		List<String> escritos = destinatarios == null ? List.of()
				: destinatarios.stream().filter(Objects::nonNull).map(String::strip).filter(cada -> !cada.isEmpty())
						.toList();
		if (escritos.isEmpty()) {
			throw rechazo(List.of("son obligatorios"));
		}
		// Antes de mirar cada dirección, para no recorrer una lista enorme
		if (escritos.size() > MAXIMO_DESTINATARIOS) {
			throw rechazo(List.of("admiten hasta " + MAXIMO_DESTINATARIOS + " direcciones"));
		}
		List<String> errores = new ArrayList<>();
		List<String> validos = new ArrayList<>();
		for (String direccion : escritos) {
			String correo = direccion.toLowerCase(Locale.ROOT);
			if (correo.length() > MAXIMO_LARGO) {
				errores.add("incluyen una dirección demasiado larga");
			} else if (!CORREO.matcher(correo).matches()) {
				errores.add("incluyen «" + direccion + "», que no es una dirección de correo válida");
			} else if (!correo.endsWith("@" + dominio)) {
				errores.add("incluyen «" + direccion + "», que no es del dominio " + dominio);
			} else if (!validos.contains(correo)) {
				validos.add(correo);
			}
		}
		if (!errores.isEmpty()) {
			throw rechazo(errores);
		}
		return List.copyOf(validos);
	}

	private static CamposInvalidosException rechazo(List<String> mensajes) {
		return new CamposInvalidosException(mensajes.stream().map(mensaje -> new CampoInvalido(CAMPO, mensaje)).toList());
	}
}
