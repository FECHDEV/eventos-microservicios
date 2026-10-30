import { TipoEntrada } from '../../../core/modelos/tipo-entrada';
import { aDatosDeTipo, aFormularioDeTipo, esPrecioValido, TIPO_VACIO } from './formulario-de-tipo';

const GENERAL: TipoEntrada = {
  idTipoEntrada: 3,
  nombre: 'General',
  precio: 50,
  cantidadTotal: 100,
  cantidadDisponible: 70,
};

describe('aFormularioDeTipo', () => {
  // El precio se escribe como se lee en Bolivia: con coma y dos decimales
  it('lleva el tipo al formulario', () => {
    expect(aFormularioDeTipo(GENERAL)).toEqual({ nombre: 'General', precio: '50,00', cantidadTotal: '100' });
  });

  it('sin tipo es el formulario vacío', () => {
    expect(aFormularioDeTipo(undefined)).toEqual(TIPO_VACIO);
  });
});

describe('aDatosDeTipo', () => {
  it('acepta el precio con coma o con punto', () => {
    expect(aDatosDeTipo({ nombre: 'VIP', precio: '120,5', cantidadTotal: '40' })).toEqual({
      nombre: 'VIP',
      precio: 120.5,
      cantidadTotal: 40,
    });
    expect(aDatosDeTipo({ nombre: 'VIP', precio: '120.50', cantidadTotal: '40' }).precio).toBe(120.5);
  });
});

// decisiones.md → Precio de un tipo de entrada: mayor que 0, hasta 2 decimales
// y hasta 99.999.999,99 (DECIMAL(10,2))
describe('esPrecioValido', () => {
  it.each(['0,01', '10', '10,5', '10.50', '99999999,99'])('«%s» es un precio válido', (precio) => {
    expect(esPrecioValido(precio)).toBe(true);
  });

  it.each(['', '0', '0,00', '-1', '10,999', '100000000', 'abc', '1.000,50', '10,'])(
    '«%s» no es un precio válido',
    (precio) => {
      expect(esPrecioValido(precio)).toBe(false);
    },
  );
});
