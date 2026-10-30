import { Entrada, etiquetaDelEvento, motivoSinQr, textoDelEstado } from './entrada';

// Copia de lo que responde el backend en EntradasControllerTest
const ACTIVA: Entrada = {
  idEntrada: 81,
  numero: 1,
  cantidad: 2,
  estado: 'ACTIVA',
  fechaEmision: '2026-09-22T20:00:00',
  codigoQr: 'codigo-81',
  idEvento: 5,
  nombreEvento: 'Noche de jazz',
  fecha: '2026-10-04',
  horaInicio: '22:00:00',
  horaFin: '03:00:00',
  lugar: 'Teatro Municipal',
  estadoEvento: 'ACTIVO',
  tipoEntrada: 'General',
};

describe('textoDelEstado', () => {
  it.each([
    ['ACTIVA', 'Activa'],
    ['UTILIZADA', 'Utilizada'],
    ['DEVUELTA', 'Devuelta'],
  ] as const)('%s se lee «%s»', (estado, texto) => {
    expect(textoDelEstado(estado)).toBe(texto);
  });
});

// CU-08 RF-6: solo el postergado y el cancelado llevan etiqueta
describe('etiquetaDelEvento', () => {
  it('un evento activo no lleva etiqueta', () => {
    expect(etiquetaDelEvento('ACTIVO')).toBeNull();
  });

  it('el postergado y el cancelado sí', () => {
    expect(etiquetaDelEvento('POSTERGADO')).toBe('Postergado');
    expect(etiquetaDelEvento('CANCELADO')).toBe('Cancelado');
  });
});

// CU-08 RF-9: por qué una entrada no muestra su QR. El estado de la entrada va primero
describe('motivoSinQr', () => {
  it('con código no hay motivo', () => {
    expect(motivoSinQr(ACTIVA)).toBeNull();
  });

  it('una devuelta lo dice aunque el evento esté cancelado', () => {
    expect(motivoSinQr({ ...ACTIVA, estado: 'DEVUELTA', estadoEvento: 'CANCELADO', codigoQr: null })).toBe(
      'Esta entrada fue devuelta.',
    );
  });

  it('una utilizada dice que ya se usó', () => {
    expect(motivoSinQr({ ...ACTIVA, estado: 'UTILIZADA', codigoQr: null })).toBe('Esta entrada ya se usó.');
  });

  it('una activa de un evento cancelado dice que se canceló', () => {
    expect(motivoSinQr({ ...ACTIVA, estadoEvento: 'CANCELADO', codigoQr: null })).toBe('El evento se canceló.');
  });

  // Si el backend ocultara el código por otro motivo, la pantalla no puede decir que el evento se canceló
  it('sin código por otro motivo no inventa uno', () => {
    expect(motivoSinQr({ ...ACTIVA, codigoQr: null })).toBe('Esta entrada no tiene un código para mostrar.');
    expect(motivoSinQr({ ...ACTIVA, estadoEvento: 'POSTERGADO', codigoQr: null })).toBe(
      'Esta entrada no tiene un código para mostrar.',
    );
  });
});
