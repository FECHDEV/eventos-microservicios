package com.sarao.pagos.service.pago;

import java.math.BigDecimal;

// El sistema de pago detrás de una interfaz propia (decisiones.md → Pago: interfaz propia con dos implementaciones).
// Sigue el circuito del proveedor: crear el cobro (la deuda, con su QR) y consultar su resultado. Solo este paquete y
// la configuración de cada adaptador conocen al proveedor (CLAUDE.md → Pago; CU-07 RF-23)
public interface IPago {

	CobroGenerado generarCobro(Long idReserva, BigDecimal monto, String concepto);

	ResultadoDelCobro consultar(String referencia);
}
