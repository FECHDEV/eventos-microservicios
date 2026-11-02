package com.sarao.eventos.service.reporte;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.sarao.eventos.entity.Entrada;
import com.sarao.eventos.entity.TipoEntrada;
import com.sarao.eventos.entity.TipoReporte;
import com.sarao.eventos.repository.EntradaRepository;
import com.sarao.eventos.service.ConsultaDeClientes;
import com.sarao.eventos.service.Cuenta;
import lombok.RequiredArgsConstructor;

// CU-14 RF-9. Sin el código de la entrada: es la credencial de acceso al evento (RNF-03)
@Component
@RequiredArgsConstructor
public class ReporteDeEntradas implements ArmadorDeReporte {

	private static final List<Columna> RESUMEN = List.of(Columna.texto("Evento"), Columna.texto("Tipo de entrada"),
			Columna.entero("Activas"), Columna.entero("Utilizadas"), Columna.entero("Devueltas"),
			Columna.entero("Total"));
	private static final List<Columna> DETALLE = List.of(Columna.texto("Número"), Columna.texto("Evento"),
			Columna.texto("Tipo de entrada"), Columna.texto("Cliente"), Columna.texto("Estado"),
			Columna.fechaHora("Fecha de emisión"));

	private final EntradaRepository entradaRepository;
	private final ConsultaDeClientes consultaDeClientes;

	@Override
	public TipoReporte tipo() {
		return TipoReporte.ENTRADAS;
	}

	@Override
	public Set<Criterio> criteriosPermitidos() {
		return EnumSet.of(Criterio.EVENTO, Criterio.TIPO_ENTRADA, Criterio.ESTADO);
	}

	@Override
	public ContenidoReporte armar(CriteriosDeReporte criterios) {
		List<Entrada> entradas = entradaRepository.paraReporte(criterios.idEvento(), criterios.idTipoEntrada(),
				criterios.estado());
		Map<Long, Cuenta> clientes = consultaDeClientes
				.cuentas(entradas.stream().map(Entrada::getIdCliente).collect(Collectors.toSet()));
		// Por tipo, una cuenta por estado en el orden de EstadoEntrada: activas, utilizadas, devueltas
		Map<TipoEntrada, long[]> porTipo = new TreeMap<>(Textos.POR_EVENTO_Y_TIPO);
		List<List<Object>> detalle = new ArrayList<>();
		for (Entrada entrada : entradas) {
			TipoEntrada tipo = entrada.getTipoEntrada();
			porTipo.computeIfAbsent(tipo, t -> new long[3])[entrada.getEstado().ordinal()]++;
			detalle.add(Arrays.asList(entrada.getIdEntrada(), tipo.getEvento().getNombre(), tipo.getNombre(),
					Textos.nombre(clientes.get(entrada.getIdCliente())), Textos.estado(entrada.getEstado()), entrada.getFechaEmision()));
		}
		List<List<Object>> resumen = new ArrayList<>();
		long[] totales = new long[3];
		for (Map.Entry<TipoEntrada, long[]> grupo : porTipo.entrySet()) {
			long[] cuentas = grupo.getValue();
			resumen.add(Arrays.asList(grupo.getKey().getEvento().getNombre(), grupo.getKey().getNombre(), cuentas[0],
					cuentas[1], cuentas[2], cuentas[0] + cuentas[1] + cuentas[2]));
			for (int i = 0; i < 3; i++) {
				totales[i] += cuentas[i];
			}
		}
		return new ContenidoReporte(new Tabla(RESUMEN, resumen, Arrays.asList("Total", null, totales[0], totales[1],
				totales[2], totales[0] + totales[1] + totales[2])), new Tabla(DETALLE, detalle, null));
	}
}
