package com.sarao.eventos.service.reporte;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.entity.Reserva;
import com.sarao.eventos.entity.TipoReporte;
import com.sarao.eventos.repository.CantidadPorId;
import com.sarao.eventos.repository.EntradaRepository;
import com.sarao.eventos.service.ConsultaDeClientes;
import com.sarao.eventos.service.ConsultaDePagos;
import com.sarao.eventos.service.Cuenta;
import com.sarao.eventos.service.PagoCobrado;
import lombok.RequiredArgsConstructor;

// CU-14 RF-8 (decisiones.md → Ingresos: bruto, devuelto y neto). El pago de una entrada devuelta sigue PAGADO, así que
// sin separar los montos se contaría como ingreso dinero que se reintegró. Lo devuelto se ubica en la fecha del pago:
// la devolución no tiene fecha propia
@Component
@RequiredArgsConstructor
public class ReporteDeIngresos implements ArmadorDeReporte {

	private static final List<Columna> RESUMEN = List.of(Columna.texto("Evento"), Columna.monto("Bruto"),
			Columna.monto("Devuelto"), Columna.monto("Neto"));
	private static final List<Columna> DETALLE = List.of(Columna.fechaHora("Fecha de pago"), Columna.texto("Cliente"),
			Columna.texto("Evento"), Columna.texto("Tipo de entrada"), Columna.monto("Bruto"),
			Columna.monto("Devuelto"), Columna.monto("Neto"));

	private final ConsultaDePagos consultaDePagos;
	private final ConsultaDeClientes consultaDeClientes;
	private final ComprasDeLosPagos comprasDeLosPagos;
	private final EntradaRepository entradaRepository;

	@Override
	public TipoReporte tipo() {
		return TipoReporte.INGRESOS;
	}

	@Override
	public Set<Criterio> criteriosPermitidos() {
		return EnumSet.of(Criterio.DESDE, Criterio.HASTA, Criterio.EVENTO);
	}

	@Override
	public ContenidoReporte armar(CriteriosDeReporte criterios) {
		List<ComprasDeLosPagos.Compra> pagos = comprasDeLosPagos.filtrar(
				consultaDePagos.cobrados(true, criterios.inicio(), criterios.fin()), criterios.idEvento(), null);
		Map<Long, Long> devueltas = devueltasPorPago(pagos);
		Map<Long, Cuenta> clientes = consultaDeClientes.cuentas(ComprasDeLosPagos.clientes(pagos));
		Map<Evento, BigDecimal[]> porEvento = new TreeMap<>(Textos.POR_EVENTO);
		List<List<Object>> detalle = new ArrayList<>();
		for (ComprasDeLosPagos.Compra compra : pagos) {
			PagoCobrado pago = compra.pago();
			Reserva reserva = compra.reserva();
			Evento evento = reserva.getTipoEntrada().getEvento();
			BigDecimal devuelto = devuelto(compra, devueltas.getOrDefault(pago.idPago(), 0L));
			detalle.add(Arrays.asList(pago.fechaPago(), Textos.nombre(clientes.get(reserva.getIdCliente())),
					evento.getNombre(), reserva.getTipoEntrada().getNombre(), pago.monto(), devuelto,
					pago.monto().subtract(devuelto)));
			BigDecimal[] montos = porEvento.computeIfAbsent(evento,
					e -> new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });
			montos[0] = montos[0].add(pago.monto());
			montos[1] = montos[1].add(devuelto);
		}
		List<List<Object>> resumen = new ArrayList<>();
		BigDecimal bruto = BigDecimal.ZERO;
		BigDecimal devuelto = BigDecimal.ZERO;
		for (Map.Entry<Evento, BigDecimal[]> grupo : porEvento.entrySet()) {
			BigDecimal[] montos = grupo.getValue();
			resumen.add(Arrays.asList(grupo.getKey().getNombre(), montos[0], montos[1], montos[0].subtract(montos[1])));
			bruto = bruto.add(montos[0]);
			devuelto = devuelto.add(montos[1]);
		}
		return new ContenidoReporte(
				new Tabla(RESUMEN, resumen, Arrays.asList("Total", bruto, devuelto, bruto.subtract(devuelto))),
				new Tabla(DETALLE, detalle, null));
	}

	private Map<Long, Long> devueltasPorPago(List<ComprasDeLosPagos.Compra> pagos) {
		if (pagos.isEmpty()) {
			return Map.of();
		}
		return entradaRepository.devueltasPorPago(pagos.stream().map(compra -> compra.pago().idPago()).toList())
				.stream().collect(Collectors.toMap(CantidadPorId::id, CantidadPorId::cantidad));
	}

	// Un pago tardío sin cupo se devuelve entero; si no, el precio congelado de cada entrada devuelta
	private static BigDecimal devuelto(ComprasDeLosPagos.Compra compra, long entradasDevueltas) {
		return compra.pago().requiereDevolucion() ? compra.pago().monto()
				: compra.reserva().getPrecioUnitario().multiply(BigDecimal.valueOf(entradasDevueltas));
	}
}
