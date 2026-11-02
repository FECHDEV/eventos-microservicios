package com.sarao.eventos.service.reporte;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.sarao.eventos.entity.TipoReporte;
import com.sarao.eventos.repository.ComprasDelCliente;
import com.sarao.eventos.repository.EntradaRepository;
import com.sarao.eventos.service.ConsultaDeClientes;
import com.sarao.eventos.service.Cuenta;
import lombok.RequiredArgsConstructor;

// CU-14 RF-10. Los que hoy tienen rol CLIENTE; con evento, solo los que tienen entradas de ese evento, y sus compras
// y entradas son las de ese evento. Las cuentas vienen de Seguridad, por fecha de registro
@Component
@RequiredArgsConstructor
public class ReporteDeClientes implements ArmadorDeReporte {

	private static final List<Columna> RESUMEN = List.of(Columna.texto("Estado de la cuenta"),
			Columna.entero("Clientes"), Columna.entero("Compras"), Columna.entero("Entradas"));
	private static final List<Columna> DETALLE = List.of(Columna.texto("Cliente"), Columna.texto("Correo"),
			Columna.texto("Teléfono"), Columna.fecha("Fecha de registro"), Columna.texto("Estado de la cuenta"),
			Columna.entero("Compras"), Columna.entero("Entradas"));
	private static final ComprasDelCliente SIN_COMPRAS = new ComprasDelCliente(null, 0L, 0L);

	private final ConsultaDeClientes consultaDeClientes;
	private final EntradaRepository entradaRepository;

	@Override
	public TipoReporte tipo() {
		return TipoReporte.CLIENTES;
	}

	@Override
	public Set<Criterio> criteriosPermitidos() {
		return EnumSet.of(Criterio.DESDE, Criterio.HASTA, Criterio.EVENTO);
	}

	@Override
	public ContenidoReporte armar(CriteriosDeReporte criterios) {
		Map<Long, ComprasDelCliente> compras = entradaRepository.comprasPorCliente(criterios.idEvento()).stream()
				.collect(Collectors.toMap(ComprasDelCliente::idCliente, Function.identity()));
		List<Cuenta> clientes = consultaDeClientes.clientes(criterios.inicio(), criterios.fin()).stream()
				.filter(cliente -> criterios.idEvento() == null || compras.containsKey(cliente.idUsuario())).toList();
		// Por estado de la cuenta, activas primero: clientes, compras y entradas
		Map<String, long[]> porEstado = new TreeMap<>();
		List<List<Object>> detalle = new ArrayList<>();
		for (Cuenta cliente : clientes) {
			ComprasDelCliente suyas = compras.getOrDefault(cliente.idUsuario(), SIN_COMPRAS);
			long[] cuentas = porEstado.computeIfAbsent(Textos.estadoDeCuenta(cliente.estado()), e -> new long[3]);
			cuentas[0]++;
			cuentas[1] += suyas.compras();
			cuentas[2] += suyas.entradas();
			detalle.add(Arrays.asList(Textos.nombre(cliente), cliente.correo(), cliente.telefono(),
					cliente.fechaRegistro().toLocalDate(), Textos.estadoDeCuenta(cliente.estado()), suyas.compras(),
					suyas.entradas()));
		}
		List<List<Object>> resumen = new ArrayList<>();
		long[] totales = new long[3];
		for (Map.Entry<String, long[]> grupo : porEstado.entrySet()) {
			long[] cuentas = grupo.getValue();
			resumen.add(Arrays.asList(grupo.getKey(), cuentas[0], cuentas[1], cuentas[2]));
			for (int i = 0; i < 3; i++) {
				totales[i] += cuentas[i];
			}
		}
		return new ContenidoReporte(
				new Tabla(RESUMEN, resumen, Arrays.asList("Total", totales[0], totales[1], totales[2])),
				new Tabla(DETALLE, detalle, null));
	}
}
