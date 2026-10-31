package com.sarao.eventos.service;

import java.util.List;

// CU-08 RF-5: las compras de eventos que no terminaron, del más cercano al más lejano, y las de los terminados, del
// más reciente al más antiguo. Las de eventos cancelados van aparte, hayan terminado o no, del más reciente al más
// antiguo (pedido en la prueba manual)
public record EntradasDelCliente(List<CompraDelCliente> proximas, List<CompraDelCliente> pasadas,
		List<CompraDelCliente> canceladas) {
}
