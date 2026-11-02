package com.sarao.eventos.service.reporte;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

import com.sarao.eventos.entity.Acceso;
import com.sarao.eventos.entity.Entrada;
import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.entity.TipoReporte;
import com.sarao.eventos.repository.AccesoRepository;
import com.sarao.eventos.service.ConsultaDeClientes;
import com.sarao.eventos.service.Cuenta;
import lombok.RequiredArgsConstructor;

// CU-14 RF-11. Una entrada reactivada tras una postergación tiene dos accesos y los dos aparecen
@Component
@RequiredArgsConstructor
public class ReporteDeAccesos implements ArmadorDeReporte {

	private static final List<Columna> RESUMEN = List.of(Columna.texto("Evento"), Columna.texto("Personal"),
			Columna.entero("Accesos"));
	private static final List<Columna> DETALLE = List.of(Columna.fechaHora("Fecha y hora"), Columna.texto("Evento"),
			Columna.texto("Tipo de entrada"), Columna.texto("Cliente"), Columna.texto("Personal"));

	// Un grupo del resumen: en el orden de los eventos y, dentro de cada uno, por nombre del Personal
	private record Grupo(Evento evento, String personal, Long idPersonal) {
		static final Comparator<Grupo> ORDEN = Comparator.comparing(Grupo::evento, Textos.POR_EVENTO)
				.thenComparing(Grupo::personal).thenComparing(Grupo::idPersonal);
	}

	private final AccesoRepository accesoRepository;
	private final ConsultaDeClientes consultaDeClientes;

	@Override
	public TipoReporte tipo() {
		return TipoReporte.ACCESOS;
	}

	@Override
	public Set<Criterio> criteriosPermitidos() {
		return EnumSet.of(Criterio.DESDE, Criterio.HASTA, Criterio.EVENTO, Criterio.PERSONAL);
	}

	@Override
	public ContenidoReporte armar(CriteriosDeReporte criterios) {
		List<Acceso> accesos = accesoRepository.paraReporte(criterios.idEvento(), criterios.inicio(), criterios.fin(),
				criterios.idPersonal());
		Set<Long> ids = new HashSet<>();
		accesos.forEach(acceso -> {
			ids.add(acceso.getIdPersonal());
			ids.add(acceso.getEntrada().getIdCliente());
		});
		Map<Long, Cuenta> cuentas = consultaDeClientes.cuentas(ids);
		Map<Grupo, Long> porGrupo = new TreeMap<>(Grupo.ORDEN);
		List<List<Object>> detalle = new ArrayList<>();
		for (Acceso acceso : accesos) {
			Entrada entrada = acceso.getEntrada();
			Evento evento = entrada.getTipoEntrada().getEvento();
			String personal = Textos.nombre(cuentas.get(acceso.getIdPersonal()));
			porGrupo.merge(new Grupo(evento, personal, acceso.getIdPersonal()), 1L, Long::sum);
			detalle.add(Arrays.asList(acceso.getFechaHora(), evento.getNombre(), entrada.getTipoEntrada().getNombre(),
					Textos.nombre(cuentas.get(entrada.getIdCliente())), personal));
		}
		List<List<Object>> resumen = new ArrayList<>();
		for (Map.Entry<Grupo, Long> grupo : porGrupo.entrySet()) {
			resumen.add(Arrays.asList(grupo.getKey().evento().getNombre(), grupo.getKey().personal(),
					grupo.getValue()));
		}
		return new ContenidoReporte(new Tabla(RESUMEN, resumen, Arrays.asList("Total", null, (long) accesos.size())),
				new Tabla(DETALLE, detalle, null));
	}
}
