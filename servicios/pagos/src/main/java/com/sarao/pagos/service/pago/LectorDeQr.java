package com.sarao.pagos.service.pago;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

// Saca el texto del QR que el proveedor entrega solo como imagen (decisiones.md → El QR del proveedor llega como texto o
// como imagen). Si no se puede leer, el cobro no se genera: la misma salida que cualquier otra falla del proveedor
final class LectorDeQr {

	// Un PNG de QR pesa unos pocos KB: algo mucho más grande no es lo que se espera
	static final int TAMANO_MAXIMO = 1024 * 1024;

	private static final Map<DecodeHintType, Object> PISTAS = Map.of(
			DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE),
			DecodeHintType.TRY_HARDER, Boolean.TRUE);

	private LectorDeQr() {
	}

	static String leer(byte[] imagen) {
		if (imagen == null || imagen.length == 0 || imagen.length > TAMANO_MAXIMO) {
			throw new SistemaDePagoNoDisponibleException("La imagen del QR falta o es demasiado grande", null);
		}
		BufferedImage leida;
		try {
			leida = ImageIO.read(new ByteArrayInputStream(imagen));
		} catch (IOException error) {
			throw new SistemaDePagoNoDisponibleException("La imagen del QR no se pudo abrir", error);
		}
		if (leida == null) {
			throw new SistemaDePagoNoDisponibleException("El QR no llegó como una imagen", null);
		}
		try {
			BinaryBitmap mapa = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(leida)));
			return new MultiFormatReader().decode(mapa, PISTAS).getText();
		} catch (NotFoundException error) {
			throw new SistemaDePagoNoDisponibleException("La imagen no tiene un QR legible", error);
		}
	}
}
