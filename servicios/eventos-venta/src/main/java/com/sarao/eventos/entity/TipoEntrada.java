package com.sarao.eventos.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Tabla tipo_entrada (modelo-diseno-y-datos.md 2.3). Reglas en decisiones.md → Gestión de Eventos
@Entity
@Table(name = "tipo_entrada")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TipoEntrada {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_tipo_entrada")
	private Long idTipoEntrada;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_evento", nullable = false)
	private Evento evento;

	@Column(nullable = false, length = 80)
	private String nombre;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal precio;

	@Column(name = "cantidad_total", nullable = false)
	private int cantidadTotal;

	// Se descuenta al reservar (CU-06) y vuelve al liberar una vencida (TipoEntradaRepository.devolverDisponibilidad)
	@Column(name = "cantidad_disponible", nullable = false)
	private int cantidadDisponible;

	public static TipoEntrada registrar(Evento evento, String nombre, BigDecimal precio, int cantidadTotal) {
		TipoEntrada tipo = new TipoEntrada();
		tipo.evento = Objects.requireNonNull(evento);
		tipo.nombre = nombre.strip();
		tipo.actualizarPrecio(precio);
		tipo.cantidadTotal = cantidadTotal;
		tipo.cantidadDisponible = cantidadTotal;
		return tipo;
	}

	public void actualizar(String nombre, BigDecimal precio, int cantidadTotal) {
		this.nombre = nombre.strip();
		actualizarPrecio(precio);
		cambiarCantidadTotal(cantidadTotal);
	}

	// Las reservas ya hechas no cambian: copian el precio al reservar (decisiones.md → Precio congelado)
	public void actualizarPrecio(BigDecimal precio) {
		this.precio = precio.setScale(2, RoundingMode.UNNECESSARY);
	}

	// Lo vendido y lo reservado (decisiones.md → Cambiar la cantidad total de un tipo)
	public int cantidadComprometida() {
		return cantidadTotal - cantidadDisponible;
	}

	// CU-06 paso 2
	public boolean verificarDisponibilidad(int cantidad) {
		return cantidad <= cantidadDisponible;
	}

	// CU-06 paso 5 (decisiones.md → cantidad_disponible se descuenta al reservar)
	public void reducirDisponibilidad(int cantidad) {
		if (cantidad < 1 || !verificarDisponibilidad(cantidad)) {
			throw new IllegalArgumentException("No hay " + cantidad + " entradas disponibles");
		}
		cantidadDisponible -= cantidad;
	}

	private void cambiarCantidadTotal(int nueva) {
		if (nueva < cantidadComprometida()) {
			throw new IllegalArgumentException("La cantidad total no puede quedar por debajo de lo vendido y reservado");
		}
		cantidadDisponible += nueva - cantidadTotal;
		cantidadTotal = nueva;
	}
}
