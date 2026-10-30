import { esValida, textoDelResultado } from './acceso';

// CU-15 RF-14: lo que la puerta lee en grande
describe('textoDelResultado', () => {
  it.each([
    ['VALIDA', 'Entrada válida'],
    ['NO_CORRESPONDE', 'No es válida para este evento'],
    ['NO_VIGENTE', 'No es válida'],
    ['YA_UTILIZADA', 'Ya fue utilizada'],
  ] as const)('%s se lee «%s»', (resultado, texto) => {
    expect(textoDelResultado(resultado)).toBe(texto);
  });
});

describe('esValida', () => {
  it('solo VALIDA deja pasar', () => {
    expect(esValida('VALIDA')).toBe(true);
    expect(esValida('NO_CORRESPONDE')).toBe(false);
    expect(esValida('NO_VIGENTE')).toBe(false);
    expect(esValida('YA_UTILIZADA')).toBe(false);
  });
});
