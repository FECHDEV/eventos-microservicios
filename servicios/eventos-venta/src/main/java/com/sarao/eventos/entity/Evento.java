package com.sarao.eventos.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Tabla evento (modelo-diseno-y-datos.md 2.2), más la columna imagen (decisiones.md → Imagen del evento)
@Entity
@Table(name = "evento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Evento {

	// La última que admite DATE en MySQL: una posterior fallaría al guardarse
	public static final LocalDate FECHA_MAXIMA = LocalDate.of(9999, 12, 31);

	public static final Set<EstadoEvento> ESTADOS_EN_CATALOGO = Set.of(EstadoEvento.ACTIVO, EstadoEvento.POSTERGADO);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_evento")
	private Long idEvento;

	// El Administrador que lo creó (decisiones.md → evento.id_usuario). Es un id: la cuenta vive en Seguridad
	@Column(name = "id_usuario", nullable = false)
	private Long idCreador;

	@Column(nullable = false, length = 150)
	private String nombre;

	@Column(columnDefinition = "TEXT")
	private String descripcion;

	@Column(nullable = false)
	private LocalDate fecha;

	@Column(name = "fecha_original")
	private LocalDate fechaOriginal;

	@Column(name = "hora_inicio", nullable = false)
	private LocalTime horaInicio;

	// Si es menor que la de inicio, el evento termina al día siguiente (decisiones.md → Fecha y horario)
	@Column(name = "hora_fin", nullable = false)
	private LocalTime horaFin;

	@Column(nullable = false, length = 200)
	private String lugar;

	// null: sin restricción de edad (CU-10 FA-03)
	@Column(name = "restriccion_edad")
	private Integer restriccionEdad;

	// null: sin límite de compra (CU-10 FA-02)
	@Column(name = "limite_compra")
	private Integer limiteCompra;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EstadoEvento estado;

	@Column(name = "venta_abierta", nullable = false)
	private boolean ventaAbierta;

	// Nombre del archivo, generado por el sistema (AlmacenImagenes)
	@Column(length = 100)
	private String imagen;

	public static Evento registrar(Long idCreador, String nombre, String descripcion, LocalDate fecha,
			LocalTime horaInicio, LocalTime horaFin, String lugar, Integer restriccionEdad, Integer limiteCompra) {
		Evento evento = new Evento();
		evento.idCreador = Objects.requireNonNull(idCreador);
		evento.estado = EstadoEvento.BORRADOR;
		evento.ventaAbierta = false;
		evento.actualizar(nombre, descripcion, fecha, horaInicio, horaFin, lugar, restriccionEdad, limiteCompra);
		return evento;
	}

	public void actualizar(String nombre, String descripcion, LocalDate fecha, LocalTime horaInicio,
			LocalTime horaFin, String lugar, Integer restriccionEdad, Integer limiteCompra) {
		this.nombre = Objects.requireNonNull(nombre);
		this.descripcion = descripcion == null || descripcion.isBlank() ? null : descripcion;
		this.fecha = Objects.requireNonNull(fecha);
		this.horaInicio = Objects.requireNonNull(horaInicio);
		this.horaFin = Objects.requireNonNull(horaFin);
		this.lugar = Objects.requireNonNull(lugar);
		this.restriccionEdad = restriccionEdad;
		this.limiteCompra = limiteCompra;
	}

	public boolean cambiaElHorario(LocalDate fecha, LocalTime horaInicio, LocalTime horaFin) {
		return !this.fecha.equals(fecha) || !this.horaInicio.equals(horaInicio) || !this.horaFin.equals(horaFin);
	}

	// Cambiar la fecha de un evento que ya vende es postergarlo, y eso es CU-12
	public boolean permiteCambiarElHorario() {
		return esBorrador();
	}

	// CU-12. Activar abre la venta (decisiones.md → La venta se abre al activar y se cierra al cancelar)
	public void activar() {
		estado = EstadoEvento.ACTIVO;
		ventaAbierta = true;
	}

	// La fecha anterior se guarda solo la primera vez: fecha_original es la fecha con la que se vendió
	public void postergar(LocalDate nuevaFecha) {
		if (fechaOriginal == null) {
			fechaOriginal = fecha;
		}
		fecha = Objects.requireNonNull(nuevaFecha);
		estado = EstadoEvento.POSTERGADO;
	}

	public void cancelar() {
		estado = EstadoEvento.CANCELADO;
		ventaAbierta = false;
	}

	public void cerrarVenta() {
		ventaAbierta = false;
	}

	public boolean puedeActivarse() {
		return estado == EstadoEvento.BORRADOR || estado == EstadoEvento.POSTERGADO;
	}

	// Un borrador cambia su fecha editándose (CU-10), no postergándose
	public boolean puedePostergarse() {
		return estado == EstadoEvento.ACTIVO || estado == EstadoEvento.POSTERGADO;
	}

	public boolean esBorrador() {
		return estado == EstadoEvento.BORRADOR;
	}

	// Si la hora de fin es menor que la de inicio, el evento termina al día siguiente
	public LocalDateTime finDeLaVenta() {
		LocalDate diaDelFin = horaFin.isBefore(horaInicio) ? fecha.plusDays(1) : fecha;
		return LocalDateTime.of(diaDelFin, horaFin);
	}

	// CU-05 y CU-08: el evento terminó al llegar su hora de fin, al día siguiente si cruza la medianoche
	public boolean termino(LocalDateTime ahora) {
		return !ahora.isBefore(finDeLaVenta());
	}

	// CU-05: el catálogo muestra los activos y postergados que todavía no terminaron (decisiones.md → Catálogo de
	// eventos). EventoRepository.buscarEnElCatalogo repite esta regla en JPQL para la lista
	public boolean seMuestraEnElCatalogo(LocalDateTime ahora) {
		return ESTADOS_EN_CATALOGO.contains(estado) && !termino(ahora);
	}

	// CU-06 paso 3, a la fecha del evento: si se postergó, la nueva
	public boolean verificarRestriccionEdad(LocalDate fechaNacimiento) {
		return restriccionEdad == null || Period.between(fechaNacimiento, fecha).getYears() >= restriccionEdad;
	}

	// CU-06 paso 4. El modelo recibe el cliente; acá llega lo que ya tiene comprometido en el evento, que se cuenta en
	// la base (decisiones.md → Límite de compra acumulado por cliente y evento)
	public boolean verificarLimiteCompra(int yaComprometidas, int cantidad) {
		return limiteCompra == null || yaComprometidas + cantidad <= limiteCompra;
	}

	// Para el aviso de FA-03. Solo tiene sentido con límite
	public int cuantasPuedeAgregar(int yaComprometidas) {
		return Math.max(0, limiteCompra - yaComprometidas);
	}

	public boolean estaCancelado() {
		return estado == EstadoEvento.CANCELADO;
	}

	public boolean tieneImagen() {
		return imagen != null;
	}

	public String cambiarImagen(String nueva) {
		String anterior = imagen;
		imagen = Objects.requireNonNull(nueva);
		return anterior;
	}

	public String quitarImagen() {
		String anterior = imagen;
		imagen = null;
		return anterior;
	}
}
