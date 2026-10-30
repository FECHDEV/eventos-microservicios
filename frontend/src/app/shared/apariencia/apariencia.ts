// Lo que elige la persona en el menú «Apariencia» (decisiones.md → «Tres estilos
// y modo día/noche»). Vive en shared porque el menú es una pieza visual sin
// negocio; core/tema lo guarda y lo aplica
export type Estilo = 'ninos' | 'jovenes' | 'adultos';
export type Modo = 'automatico' | 'dia' | 'noche';

export const ESTILOS: readonly { valor: Estilo; nombre: string }[] = [
	{ valor: 'ninos', nombre: 'Niños' },
	{ valor: 'jovenes', nombre: 'Jóvenes' },
	{ valor: 'adultos', nombre: 'Adultos' },
];

export const MODOS: readonly { valor: Modo; nombre: string }[] = [
	{ valor: 'automatico', nombre: 'Automático, según la hora' },
	{ valor: 'dia', nombre: 'Día' },
	{ valor: 'noche', nombre: 'Noche' },
];
