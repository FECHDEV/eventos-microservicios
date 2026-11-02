package com.sarao.pagos.service.pago;

// No se pudo hablar con el sistema de pago (caído, respuesta inválida). No es un error de negocio: quien llama decide
// qué hacer (CU-07 RF-6 al generar el cobro; al consultar, se reintenta en la próxima verificación)
public class SistemaDePagoNoDisponibleException extends RuntimeException {

	public SistemaDePagoNoDisponibleException(String mensaje, Throwable causa) {
		super(mensaje, causa);
	}
}
