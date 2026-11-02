package com.sarao.eventos.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sarao.eventos.entity.EstadoEvento;
import com.sarao.eventos.entity.Evento;
import com.sarao.eventos.entity.Reporte;
import com.sarao.eventos.entity.TipoEntrada;
import com.sarao.eventos.entity.TipoReporte;
import com.sarao.eventos.exception.CamposInvalidosException;
import com.sarao.eventos.exception.CamposInvalidosException.CampoInvalido;
import com.sarao.eventos.exception.EventoNoEncontradoException;
import com.sarao.eventos.exception.ReporteNoEncontradoException;
import com.sarao.eventos.exception.ReporteSinResultadosException;
import com.sarao.eventos.exception.TipoEntradaNoEncontradoException;
import com.sarao.eventos.repository.AccesoRepository;
import com.sarao.eventos.repository.EventoRepository;
import com.sarao.eventos.repository.ReporteRepository;
import com.sarao.eventos.repository.TipoEntradaRepository;
import com.sarao.eventos.service.reporte.ArmadorDeReporte;
import com.sarao.eventos.service.reporte.ContenidoReporte;
import com.sarao.eventos.service.reporte.Criterio;
import com.sarao.eventos.service.reporte.CriterioAplicado;
import com.sarao.eventos.service.reporte.CriteriosDeReporte;
import com.sarao.eventos.service.reporte.EventoConTipos;
import com.sarao.eventos.service.reporte.OpcionesDeReporte;
import com.sarao.eventos.service.reporte.ReporteGenerado;
import com.sarao.eventos.service.reporte.Textos;

// CU-14 (Administrador.generarReporte del modelo; decisiones.md → Usuario es una sola clase). Solo el Administrador
// llega acá (SecurityConfig). Generar arma el reporte y lo registra si tuvo resultados; exportar lo vuelve a armar con
// los criterios registrados, sin registrar otro (decisiones.md → Gestionar reportes (CU-14))
@Service
public class ReporteService {

	private final Map<TipoReporte, ArmadorDeReporte> armadores;
	private final ReporteRepository reporteRepository;
	private final ConsultaDeClientes consultaDeClientes;
	private final AccesoRepository accesoRepository;
	private final EventoRepository eventoRepository;
	private final TipoEntradaRepository tipoEntradaRepository;
	private final Clock clock;

	public ReporteService(List<ArmadorDeReporte> armadores, ReporteRepository reporteRepository,
			ConsultaDeClientes consultaDeClientes, AccesoRepository accesoRepository, EventoRepository eventoRepository,
			TipoEntradaRepository tipoEntradaRepository, Clock clock) {
		this.armadores = armadores.stream().collect(Collectors.toMap(ArmadorDeReporte::tipo, Function.identity()));
		this.reporteRepository = reporteRepository;
		this.consultaDeClientes = consultaDeClientes;
		this.accesoRepository = accesoRepository;
		this.eventoRepository = eventoRepository;
		this.tipoEntradaRepository = tipoEntradaRepository;
		this.clock = clock;
	}

	// Pasos 4 y 5, y RF-13
	@Transactional
	public ReporteGenerado generar(Long idAdministrador, TipoReporte tipo, CriteriosDeReporte criterios) {
		ArmadorDeReporte armador = armadores.get(tipo);
		validar(armador, criterios);
		ContenidoReporte contenido = armador.armar(criterios);
		if (contenido.sinResultados()) {
			throw new ReporteSinResultadosException();
		}
		Reporte reporte = reporteRepository.save(Reporte.registrar(idAdministrador,
				tipo, criterios.serializar(), LocalDateTime.now(clock)));
		return new ReporteGenerado(reporte, describir(criterios), contenido, null);
	}

	// Pasos 6 y 7 (RF-14): con los datos de ahora y los criterios del reporte registrado. El archivo dice cuándo se
	// generó y cuándo se exportó, para que se note si los datos cambiaron en el medio
	@Transactional(readOnly = true)
	public ReporteGenerado paraExportar(Long idReporte) {
		Reporte reporte = reporteRepository.findById(idReporte).orElseThrow(ReporteNoEncontradoException::new);
		CriteriosDeReporte criterios = CriteriosDeReporte.leer(reporte.getCriteriosFiltro());
		return new ReporteGenerado(reporte, describir(criterios), armadores.get(reporte.getTipo()).armar(criterios),
				LocalDateTime.now(clock));
	}

	// RF-2: un borrador no vende, así que no aparece en los filtros
	@Transactional(readOnly = true)
	public OpcionesDeReporte opciones() {
		List<Evento> eventos = eventoRepository.findByEstadoNotOrderByFechaDescHoraInicioDesc(EstadoEvento.BORRADOR);
		Map<Long, List<TipoEntrada>> tipos = eventos.isEmpty() ? Map.of()
				: tipoEntradaRepository.findByEvento_IdEventoInOrderByIdTipoEntrada(
						eventos.stream().map(Evento::getIdEvento).toList()).stream()
						.collect(Collectors.groupingBy(tipo -> tipo.getEvento().getIdEvento()));
		return new OpcionesDeReporte(eventos.stream()
				.map(evento -> new EventoConTipos(evento, tipos.getOrDefault(evento.getIdEvento(), List.of())))
				.toList(), consultaDeClientes.cuentas(accesoRepository.idsDelPersonal()).values().stream()
						.sorted(Comparator.comparing(Textos::nombre).thenComparing(Cuenta::idUsuario)).toList());
	}

	// RF-4. Primero los errores de campo, todos juntos; después lo que hay que buscar en la base
	private void validar(ArmadorDeReporte armador, CriteriosDeReporte criterios) {
		List<CampoInvalido> errores = new ArrayList<>();
		for (Criterio criterio : criterios.presentes()) {
			if (!armador.criteriosPermitidos().contains(criterio)) {
				errores.add(new CampoInvalido(criterio.campo(), "no corresponde a este reporte"));
			}
		}
		if (fueraDeRango(criterios.desde())) {
			errores.add(new CampoInvalido("desde", "no es una fecha válida"));
		}
		if (fueraDeRango(criterios.hasta())) {
			errores.add(new CampoInvalido("hasta", "no es una fecha válida"));
		}
		if (criterios.desde() != null && criterios.hasta() != null && criterios.hasta().isBefore(criterios.desde())) {
			errores.add(new CampoInvalido("hasta", "no puede ser anterior a la fecha desde"));
		}
		if (criterios.idTipoEntrada() != null && criterios.idEvento() == null) {
			errores.add(new CampoInvalido("idTipoEntrada", "requiere elegir un evento"));
		}
		if (!errores.isEmpty()) {
			throw new CamposInvalidosException(errores);
		}
		if (criterios.idEvento() != null && !eventoRepository.existsById(criterios.idEvento())) {
			throw new EventoNoEncontradoException();
		}
		if (criterios.idTipoEntrada() != null && tipoEntradaRepository
				.findByIdTipoEntradaAndEvento_IdEvento(criterios.idTipoEntrada(), criterios.idEvento()).isEmpty()) {
			throw new TipoEntradaNoEncontradoException();
		}
	}

	// Lo que admite una columna DATETIME de MySQL. Fuera de eso la consulta no se puede armar (LocalDate.MAX no tiene
	// día siguiente para el fin del rango)
	private static boolean fueraDeRango(LocalDate fecha) {
		return fecha != null && (fecha.getYear() < 1000 || fecha.getYear() > 9998);
	}

	// RF-6: los criterios como se leen en la pantalla y en los archivos
	private List<CriterioAplicado> describir(CriteriosDeReporte criterios) {
		List<CriterioAplicado> descritos = new ArrayList<>();
		if (criterios.desde() != null) {
			descritos.add(new CriterioAplicado("Desde", criterios.desde().format(Textos.DIA)));
		}
		if (criterios.hasta() != null) {
			descritos.add(new CriterioAplicado("Hasta", criterios.hasta().format(Textos.DIA)));
		}
		if (criterios.idEvento() != null) {
			eventoRepository.findById(criterios.idEvento()).ifPresent(evento -> descritos.add(new CriterioAplicado(
					"Evento", evento.getNombre() + " (" + evento.getFecha().format(Textos.DIA) + ")")));
		}
		if (criterios.idTipoEntrada() != null) {
			tipoEntradaRepository.findById(criterios.idTipoEntrada())
					.ifPresent(tipo -> descritos.add(new CriterioAplicado("Tipo de entrada", tipo.getNombre())));
		}
		if (criterios.estado() != null) {
			descritos.add(new CriterioAplicado("Estado", Textos.estado(criterios.estado())));
		}
		if (criterios.idPersonal() != null) {
			Cuenta personal = consultaDeClientes.cuentas(List.of(criterios.idPersonal())).get(criterios.idPersonal());
			descritos.add(new CriterioAplicado("Personal", Textos.nombre(personal)));
		}
		return descritos;
	}
}
