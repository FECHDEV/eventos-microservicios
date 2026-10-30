import { vendidasOReservadas } from './tipo-entrada';

// decisiones.md → Cambiar la cantidad total de un tipo: lo vendido y lo
// reservado es la total menos la disponible, y es el mínimo al que se puede bajar
describe('vendidasOReservadas', () => {
  it('es la cantidad total menos la disponible', () => {
    expect(
      vendidasOReservadas({ idTipoEntrada: 3, nombre: 'General', precio: 50, cantidadTotal: 100, cantidadDisponible: 70 }),
    ).toBe(30);
  });

  it('un tipo recién creado no tiene ninguna', () => {
    expect(
      vendidasOReservadas({ idTipoEntrada: 3, nombre: 'General', precio: 50, cantidadTotal: 100, cantidadDisponible: 100 }),
    ).toBe(0);
  });
});
