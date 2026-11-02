package com.sarao.eventos.service.reporte;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

import com.sarao.eventos.entity.Reserva;
import com.sarao.eventos.entity.TipoEntrada;
import com.sarao.eventos.entity.TipoReporte;
import com.sarao.eventos.service.ConsultaDeClientes;
import com.sarao.eventos.service.ConsultaDePagos;
import com.sarao.eventos.service.Cuenta;
import com.sarao.eventos.service.PagoCobrado;
import lombok.RequiredArgsConstructor;

// CU-14 RF-7. Una venta es una compra confirmada: un pago PAGADO sin requiere_devolucion. Una compra devuelta después
// sigue siendo venta; lo devuelto se ve en ingresos (decisiones.md → Gestionar reportes (CU-14))
@Component
@RequiredArgsConstructor
public class ReporteDeVentas implements ArmadorDeReporte {

	private static final List<Columna> RESUMEN = List.of(Columna.texto("Evento"), Columna.texto("Tipo de entrada"),
			Columna.entero("Compras"), Columna.entero("Entradas"), Columna.monto("Monto"));
	private static final List<Columna> DETALLE = List.of(Columna.fechaHora("Fecha de pago"), Columna.texto("Cliente"),
			Columna.texto("Correo"), Columna.texto("Evento"), Columna.texto("Tipo de entrada"),
			Columna.entero("Cantidad"), Columna.monto("Monto"));

	private final ConsultaDePagos consultaDePagos;
	private final ConsultaDeClientes consultaDeClientes;
	private final ComprasDeLosPagos comprasDeLosPagos;

	@Override
	public TipoReporte tipo() {
		return TipoReporte.VENTAS;
	}

	@Override
	public Set<Criterio> criteriosPermitidos() {
		return EnumSet.of(Criterio.DESDE, Criterio.HASTA, Criterio.EVENTO, Criterio.TIPO_ENTRADA);
	}

	@Override
	public ContenidoReporte armar(CriteriosDeReporte criterios) {
		List<ComprasDeLosPagos.Compra> ventas = comprasDeLosPagos.filtrar(
				consultaDePagos.cobrados(false, criterios.inicio(), criterios.fin()), criterios.idEvento(),
				criterios.idTipoEntrada());
		Map<Long, Cuenta> clientes = consultaDeClientes.cuentas(ComprasDeLosPagos.clientes(ventas));
		Map<TipoEntrada, List<ComprasDeLosPagos.Compra>> porTipo = new TreeMap<>(Textos.POR_EVENTO_Y_TIPO);
		List<List<Object>> detalle = new ArrayList<>();
		for (ComprasDeLosPagos.Compra venta : ventas) {
			PagoCobrado pago = venta.pago();
			Reserva reserva = venta.reserva();
			Cuenta cliente = clientes.get(reserva.getIdCliente());
			TipoEntrada tipo = reserva.getTipoEntrada();
			porTipo.computeIfAbsent(tipo, t -> new ArrayList<>()).add(venta);
			detalle.add(Arrays.asList(pago.fechaPago(), Textos.nombre(cliente), cliente == null ? null : cliente.correo(),
					tipo.getEvento().getNombre(), tipo.getNombre(), (long) reserva.getCantidad(), pago.monto()));
		}
		List<List<Object>> resumen = new ArrayList<>();
		long compras = 0;
		long entradas = 0;
		BigDecimal monto = BigDecimal.ZERO;
		for (Map.Entry<TipoEntrada, List<ComprasDeLosPagos.Compra>> grupo : porTipo.entrySet()) {
			List<ComprasDeLosPagos.Compra> suyas = grupo.getValue();
			long cantidad = suyas.stream().mapToLong(venta -> venta.reserva().getCantidad()).sum();
			BigDecimal suMonto = suyas.stream().map(venta -> venta.pago().monto()).reduce(BigDecimal.ZERO,
					BigDecimal::add);
			resumen.add(Arrays.asList(grupo.getKey().getEvento().getNombre(), grupo.getKey().getNombre(),
					(long) suyas.size(), cantidad, suMonto));
			compras += suyas.size();
			entradas += cantidad;
			monto = monto.add(suMonto);
		}
		return new ContenidoReporte(new Tabla(RESUMEN, resumen, Arrays.asList("Total", null, compras, entradas, monto)),
				new Tabla(DETALLE, detalle, null));
	}
}
