package com.sarao.eventos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.sarao.eventos.entity.Reserva;
import com.sarao.eventos.service.CompraParaDevolucion;
import com.sarao.eventos.service.Cuenta;

// CU-13 RF-4: lo que el Administrador necesita para devolver el dinero fuera del sistema. La cantidad y el monto son
// los de toda la compra; las entradas, solo las del estado de la lista en la que aparece. El precio por entrada es el
// congelado al reservar: es lo que se devuelve por cada una. La fecha de pago es la de emisión de las entradas, que
// se emiten al confirmarse el pago: el pago vive en Pagos
public record CompraParaDevolucionResponse(Long idReserva, String cliente, String correo, String telefono,
		String tipoEntrada, int cantidad, BigDecimal monto, BigDecimal precioUnitario, LocalDateTime fechaPago,
		List<EntradaParaDevolucionResponse> entradas) {

	public static CompraParaDevolucionResponse from(CompraParaDevolucion compra) {
		Reserva reserva = compra.reserva();
		Cuenta cliente = compra.cliente();
		return new CompraParaDevolucionResponse(reserva.getIdReserva(),
				cliente == null ? "—" : cliente.nombre() + " " + cliente.apellido(),
				cliente == null ? null : cliente.correo(), cliente == null ? null : cliente.telefono(),
				compra.primera().getTipoEntrada().getNombre(), compra.cantidad(), reserva.total(),
				reserva.getPrecioUnitario(), compra.primera().getFechaEmision(),
				compra.entradas().stream()
						.map(entrada -> new EntradaParaDevolucionResponse(entrada.entrada().getIdEntrada(),
								entrada.numero()))
						.toList());
	}
}
