package com.sarao.notificaciones.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import com.sarao.notificaciones.config.CorreoProperties;
import com.sarao.notificaciones.dto.CorreoDeCompra;
import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

@ExtendWith(OutputCaptureExtension.class)
class NotificadorCorreoSmtpTest {

	private final JavaMailSender mailSender = mock(JavaMailSender.class);
	private final NotificadorCorreo notificador = new NotificadorCorreoSmtp(mailSender,
			new CorreoProperties("Sarao <no-responder@mail.com>", "https://sarao.bo/"));

	private final CorreoDeCompra compra = new CorreoDeCompra("ana@mail.com", "Ana", "Noche de jazz",
			LocalDate.of(2026, 10, 4), LocalTime.of(22, 0), "Teatro Municipal", "General", 2, new BigDecimal("50.00"),
			new BigDecimal("100.00"));

	@BeforeEach
	void mensajesReales() {
		given(mailSender.createMimeMessage()).willAnswer(invocacion -> new MimeMessage((Session) null));
	}

	@Test
	void laRecuperacionVaAlUsuarioDesdeElRemitenteDelSitio() throws Exception {
		notificador.enviarRecuperacion("ana@mail.com", "Ana", "token-de-prueba");

		MimeMessage mensaje = enviado();
		assertThat(mensaje.getAllRecipients()).containsExactly(new InternetAddress("ana@mail.com"));
		assertThat(((InternetAddress) mensaje.getFrom()[0]).getAddress()).isEqualTo("no-responder@mail.com");
		assertThat(((InternetAddress) mensaje.getFrom()[0]).getPersonal()).isEqualTo("Sarao");
		assertThat(mensaje.getSubject()).isEqualTo("Recupera tu contraseña de Sarao");
	}

	@Test
	void laRecuperacionLlevaElEnlaceConElTokenEnTextoYEnHtml() throws Exception {
		notificador.enviarRecuperacion("ana@mail.com", "Ana", "token-de-prueba");

		List<String> partes = partes(enviado());
		assertThat(partes).hasSize(2)
				.allSatisfy(parte -> assertThat(parte)
						.contains("https://sarao.bo/restablecer?token=token-de-prueba")
						.contains("1 hora"));
	}

	@Test
	void elNombreDelUsuarioSeEscapaEnElHtml() throws Exception {
		notificador.enviarRecuperacion("ana@mail.com", "<script>alert(1)</script>", "token-de-prueba");

		String html = partes(enviado()).get(1);
		assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;");
	}

	@Test
	void elDetalleDeCompraLlevaLosDatosDeLaVenta() throws Exception {
		notificador.enviarDetalleDeCompra(compra);

		MimeMessage mensaje = enviado();
		assertThat(mensaje.getAllRecipients()).containsExactly(new InternetAddress("ana@mail.com"));
		assertThat(mensaje.getSubject()).isEqualTo("Detalle de tu compra en Sarao");
		assertThat(partes(mensaje)).allSatisfy(parte -> assertThat(parte).contains("Noche de jazz")
				.contains("04/10/2026").contains("22:00").contains("Teatro Municipal").contains("General")
				.contains("Bs 50.00").contains("Bs 100.00"));
	}

	@Test
	void losDatosDelEventoSeEscapanEnElHtml() throws Exception {
		notificador.enviarDetalleDeCompra(new CorreoDeCompra("ana@mail.com", "Ana", "Jazz & <b>blues</b>",
				LocalDate.of(2026, 10, 4), LocalTime.of(22, 0), "Teatro", "General", 1, BigDecimal.TEN,
				BigDecimal.TEN));

		String html = partes(enviado()).get(1);
		assertThat(html).doesNotContain("<b>blues</b>").contains("Jazz &amp; &lt;b&gt;blues&lt;/b&gt;");
	}

	@Test
	void lasEntradasLlevanCadaCodigoYElEnlaceAMisEntradas() throws Exception {
		notificador.enviarEntradas(compra, List.of("codigo-a", "codigo-b"));

		MimeMessage mensaje = enviado();
		assertThat(mensaje.getSubject()).isEqualTo("Tus entradas de Sarao");
		assertThat(partes(mensaje)).allSatisfy(parte -> assertThat(parte).contains("Noche de jazz")
				.contains("codigo-a").contains("codigo-b").contains("https://sarao.bo/mis-entradas"));
	}

	@Test
	void unFalloDelEnvioSeRegistraSinPropagarseNiMostrarElToken(CapturedOutput salida) {
		willThrow(new MailSendException("servidor caído")).given(mailSender).send(any(MimeMessage.class));

		assertThatCode(() -> notificador.enviarRecuperacion("ana@mail.com", "Ana", "token-secreto"))
				.doesNotThrowAnyException();
		assertThat(salida.getOut()).contains("No se pudo enviar el correo de recuperación")
				.doesNotContain("token-secreto");
	}

	@Test
	void unErrorInesperadoTambienSeRegistraSinPropagarse(CapturedOutput salida) {
		willThrow(new IllegalStateException("sesión de correo rota")).given(mailSender).send(any(MimeMessage.class));

		assertThatCode(() -> notificador.enviarEntradas(compra, List.of("codigo-a"))).doesNotThrowAnyException();
		assertThat(salida.getOut()).contains("No se pudo enviar el correo de entradas");
	}

	private MimeMessage enviado() {
		ArgumentCaptor<MimeMessage> mensaje = ArgumentCaptor.forClass(MimeMessage.class);
		verify(mailSender).send(mensaje.capture());
		return mensaje.getValue();
	}

	// Las partes de texto del mensaje, en orden: primero el texto plano y después el HTML
	private static List<String> partes(Part parte) throws MessagingException, IOException {
		List<String> textos = new ArrayList<>();
		if (parte.getContent() instanceof Multipart multipart) {
			for (int i = 0; i < multipart.getCount(); i++) {
				BodyPart hija = multipart.getBodyPart(i);
				textos.addAll(partes(hija));
			}
		} else if (parte.getContent() instanceof String texto) {
			textos.add(texto);
		}
		return textos;
	}
}
