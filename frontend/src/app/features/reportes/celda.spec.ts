import { formatearCelda } from './celda';

// Intl separa «Bs» del número con un espacio que no corta (U+00A0)
const ESPACIO = ' ';

describe('formatearCelda', () => {
  // Como se leen en Bolivia, igual que el PDF (FormatoDeCeldas)
  it.each([
    [1240, 'MONTO', `Bs${ESPACIO}1.240,00`],
    [5000, 'ENTERO', '5.000'],
    ['2026-09-01', 'FECHA', '01/09/2026'],
    ['2026-09-02T10:14:00', 'FECHA_HORA', '02/09/2026 10:14'],
    ['Ana Rojas', 'TEXTO', 'Ana Rojas'],
  ] as const)('%s como %s dice «%s»', (valor, tipo, texto) => {
    expect(formatearCelda(valor, tipo)).toBe(texto);
  });

  it('un valor vacío se muestra como raya', () => {
    expect(formatearCelda(null, 'TEXTO')).toBe('—');
    expect(formatearCelda(null, 'MONTO')).toBe('—');
  });

  // «Total» va en la primera columna aunque sea de otro tipo
  it('un texto en una columna de otro tipo va tal cual', () => {
    expect(formatearCelda('Total', 'FECHA_HORA')).toBe('Total');
    expect(formatearCelda('Total', 'MONTO')).toBe('Total');
  });

  it('un número en una columna de texto va sin formato', () => {
    expect(formatearCelda(1240, 'TEXTO')).toBe('1240');
  });
});
