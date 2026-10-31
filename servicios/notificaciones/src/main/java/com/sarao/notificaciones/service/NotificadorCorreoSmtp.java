package com.sarao.notificaciones.service;

import static org.springframework.web.util.HtmlUtils.htmlEscape;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.sarao.notificaciones.config.CorreoProperties;
import com.sarao.notificaciones.dto.CorreoDeCompra;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Envía los correos por SMTP al servidor de correo propio (decisiones.md → Notificaciones). En segundo plano y sin
// propagar los fallos. Cada correo va en texto plano y en HTML; en el HTML, todo dato que escribió un usuario se escapa
@Slf4j
@Service
@ConditionalOnProperty(name = "eventos.correo.proveedor", havingValue = "smtp")
@RequiredArgsConstructor
public class NotificadorCorreoSmtp implements NotificadorCorreo {

	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

	private final JavaMailSender mailSender;
	private final CorreoProperties propiedades;

	@Async
	@Override
	public void enviarRecuperacion(String correo, String nombre, String token) {
		// El token es base64 para URL (TokenRecuperacion): va tal cual en el enlace
		String enlace = propiedades.urlDelSitio() + "/restablecer?token=" + token;
		String texto = """
				Hola, %s:

				Recibimos una solicitud para recuperar la contraseña de tu cuenta de Sarao. Para elegir una \
				contraseña nueva, abre este enlace:

				%s

				El enlace vence en 1 hora y sirve una sola vez. Si no pediste recuperar tu contraseña, ignora este \
				correo: tu contraseña sigue siendo la misma.
				""".formatted(nombre, enlace);
		String html = """
				<p>Hola, %s:</p>
				<p>Recibimos una solicitud para recuperar la contraseña de tu cuenta de Sarao.</p>
				%s
				<p>O copia este enlace en tu navegador:<br><a href="%s" style="color:#0b1020;word-break:break-all">%s</a></p>
				<p style="color:#5b6480">El enlace vence en 1 hora y sirve una sola vez. Si no pediste recuperar tu \
				contraseña, ignora este correo: tu contraseña sigue siendo la misma.</p>
				""".formatted(htmlEscape(nombre), boton("Elegir una contraseña nueva", enlace), enlace, enlace);
		enviar("recuperación", correo, "Recupera tu contraseña de Sarao", texto, html);
	}

	@Async
	@Override
	public void enviarDetalleDeCompra(CorreoDeCompra compra) {
		String texto = """
				Hola, %s:

				Tu compra está confirmada. Este es el detalle:

				Evento: %s
				Fecha: %s, %s
				Lugar: %s
				Entrada: %s
				Cantidad: %d
				Precio unitario: Bs %s
				Total: Bs %s

				En otro correo te enviamos las entradas.
				""".formatted(compra.nombre(), compra.evento(), compra.fecha().format(FECHA),
				compra.horaInicio().format(HORA), compra.lugar(), compra.tipo(), compra.cantidad(),
				compra.precioUnitario().toPlainString(), compra.total().toPlainString());
		String html = """
				<p>Hola, %s:</p>
				<p>Tu compra está confirmada. Este es el detalle:</p>
				<table style="border-collapse:collapse;width:100%%">
				%s%s%s%s%s%s%s
				</table>
				<p>En otro correo te enviamos las entradas.</p>
				""".formatted(htmlEscape(compra.nombre()), fila("Evento", compra.evento()),
				fila("Fecha", compra.fecha().format(FECHA) + ", " + compra.horaInicio().format(HORA)),
				fila("Lugar", compra.lugar()), fila("Entrada", compra.tipo()),
				fila("Cantidad", String.valueOf(compra.cantidad())),
				fila("Precio unitario", "Bs " + compra.precioUnitario().toPlainString()),
				fila("Total", "Bs " + compra.total().toPlainString()));
		enviar("detalle de compra", compra.correo(), "Detalle de tu compra en Sarao", texto, html);
	}

	@Async
	@Override
	public void enviarEntradas(CorreoDeCompra compra, List<String> codigos) {
		String enlace = propiedades.urlDelSitio() + "/mis-entradas";
		String texto = """
				Hola, %s:

				Estas son tus entradas para %s (%s, %s, %s):

				%s

				Para entrar, muestra el código QR de cada entrada. Los encuentras en Mis entradas:
				%s
				""".formatted(compra.nombre(), compra.evento(), compra.fecha().format(FECHA),
				compra.horaInicio().format(HORA), compra.lugar(),
				codigos.stream().map(codigo -> "- " + codigo).collect(Collectors.joining("\n")), enlace);
		String html = """
				<p>Hola, %s:</p>
				<p>Estas son tus entradas para <strong>%s</strong> (%s, %s, %s):</p>
				<ul style="padding-left:20px;font-family:monospace">%s</ul>
				<p>Para entrar, muestra el código QR de cada entrada. Los encuentras en Mis entradas.</p>
				%s
				""".formatted(htmlEscape(compra.nombre()), htmlEscape(compra.evento()), compra.fecha().format(FECHA),
				compra.horaInicio().format(HORA), htmlEscape(compra.lugar()),
				codigos.stream().map(codigo -> "<li>" + htmlEscape(codigo) + "</li>").collect(Collectors.joining()),
				boton("Ver mis entradas", enlace));
		enviar("entradas", compra.correo(), "Tus entradas de Sarao", texto, html);
	}

	private void enviar(String cual, String para, String asunto, String texto, String cuerpoHtml) {
		try {
			MimeMessage mensaje = mailSender.createMimeMessage();
			MimeMessageHelper ayudante = new MimeMessageHelper(mensaje, true, StandardCharsets.UTF_8.name());
			ayudante.setFrom(propiedades.remitente());
			ayudante.setTo(para);
			ayudante.setSubject(asunto);
			ayudante.setText(texto, plantilla(cuerpoHtml));
			mailSender.send(mensaje);
		} catch (MessagingException | RuntimeException error) {
			// Cualquier fallo, también uno inesperado: en segundo plano no hay quién lo atrape. Sin el contenido del
			// correo: el de recuperación lleva el token
			log.error("No se pudo enviar el correo de {} a {}: {}", cual, para, error.getMessage());
		}
	}

	private static String plantilla(String cuerpo) {
		return """
				<!DOCTYPE html>
				<html lang="es"><body style="margin:0;padding:24px;background:#f4f6ff;font-family:Arial,sans-serif;color:#151b2e">
				<div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:10px;overflow:hidden">
				<div style="background:#0b1020;padding:20px 24px;color:#f5b301;font-size:22px;font-weight:bold">Sarao</div>
				<div style="padding:24px;font-size:15px;line-height:1.5">%s</div>
				</div>
				</body></html>
				""".formatted(cuerpo);
	}

	private static String boton(String texto, String enlace) {
		return """
				<p><a href="%s" style="display:inline-block;padding:12px 20px;background:#f5b301;color:#0b1020;\
				text-decoration:none;border-radius:8px;font-weight:bold">%s</a></p>
				""".formatted(enlace, texto);
	}

	private static String fila(String dato, String valor) {
		return """
				<tr><td style="padding:6px 0;color:#5b6480">%s</td>\
				<td style="padding:6px 0;text-align:right;font-weight:bold">%s</td></tr>
				""".formatted(dato, htmlEscape(valor));
	}
}
