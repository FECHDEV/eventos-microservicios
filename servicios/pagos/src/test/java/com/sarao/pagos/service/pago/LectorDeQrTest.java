package com.sarao.pagos.service.pago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;

// decisiones.md → El QR del proveedor llega como texto o como imagen
class LectorDeQrTest {

	// Del largo de un QR interoperable: el texto real no es corto
	static final String TEXTO = "00020101021226" + "A".repeat(300) + "5303068540515.005802BO6304ABCD";

	static byte[] png(String texto) {
		try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
			MatrixToImageWriter.writeToStream(new QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, 400, 400), "PNG",
					salida);
			return salida.toByteArray();
		} catch (IOException | WriterException error) {
			throw new IllegalStateException(error);
		}
	}

	@Test
	void leeElTextoDeUnQr() {
		assertThat(LectorDeQr.leer(png(TEXTO))).isEqualTo(TEXTO);
	}

	@Test
	void unaImagenSinQrNoSeLee() throws IOException {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB), "PNG", salida);

		assertThatThrownBy(() -> LectorDeQr.leer(salida.toByteArray()))
				.isInstanceOf(SistemaDePagoNoDisponibleException.class);
	}

	@Test
	void unArchivoQueNoEsImagen() {
		assertThatThrownBy(() -> LectorDeQr.leer("<html>error</html>".getBytes(StandardCharsets.UTF_8)))
				.isInstanceOf(SistemaDePagoNoDisponibleException.class);
	}

	@Test
	void unaImagenDemasiadoGrandeOVacia() {
		assertThatThrownBy(() -> LectorDeQr.leer(new byte[LectorDeQr.TAMANO_MAXIMO + 1]))
				.isInstanceOf(SistemaDePagoNoDisponibleException.class);
		assertThatThrownBy(() -> LectorDeQr.leer(new byte[0])).isInstanceOf(SistemaDePagoNoDisponibleException.class);
	}
}
