import { Evento } from '../../../core/modelos/evento';
import { aDatos, aFormulario, FORMULARIO_VACIO } from './formulario-de-evento';

const JAZZ: Evento = {
  idEvento: 5,
  nombre: 'Noche de jazz',
  descripcion: 'Cuarteto',
  fecha: '2026-10-04',
  fechaOriginal: null,
  horaInicio: '22:00:00',
  horaFin: '03:00:00',
  lugar: 'Teatro Municipal',
  restriccionEdad: 18,
  limiteCompra: 4,
  estado: 'BORRADOR',
  ventaAbierta: false,
  tieneImagen: false,
};

describe('aFormulario', () => {
  // Las horas del backend traen segundos; el <input type="time"> usa HH:mm
  it('lleva el evento al formulario', () => {
    expect(aFormulario(JAZZ)).toEqual({
      nombre: 'Noche de jazz',
      descripcion: 'Cuarteto',
      fecha: '2026-10-04',
      horaInicio: '22:00',
      horaFin: '03:00',
      lugar: 'Teatro Municipal',
      restriccionEdad: '18',
      limiteCompra: '4',
    });
  });

  // Sin restricción ni límite, los campos quedan vacíos (CU-10 FA-02 y FA-03)
  it('sin restricción ni límite deja los campos vacíos', () => {
    const formulario = aFormulario({ ...JAZZ, descripcion: null, restriccionEdad: null, limiteCompra: null });

    expect(formulario.descripcion).toBe('');
    expect(formulario.restriccionEdad).toBe('');
    expect(formulario.limiteCompra).toBe('');
  });

  it('sin evento es el formulario vacío', () => {
    expect(aFormulario(undefined)).toEqual(FORMULARIO_VACIO);
  });
});

describe('aDatos', () => {
  it('lleva el formulario a lo que espera el backend', () => {
    expect(aDatos(aFormulario(JAZZ))).toEqual({
      nombre: 'Noche de jazz',
      descripcion: 'Cuarteto',
      fecha: '2026-10-04',
      horaInicio: '22:00',
      horaFin: '03:00',
      lugar: 'Teatro Municipal',
      restriccionEdad: 18,
      limiteCompra: 4,
    });
  });

  // Vacíos significan «sin restricción» y «sin límite»: viajan como null
  it('los campos opcionales vacíos viajan como null', () => {
    const datos = aDatos({ ...aFormulario(JAZZ), descripcion: '   ', restriccionEdad: '', limiteCompra: '' });

    expect(datos.descripcion).toBeNull();
    expect(datos.restriccionEdad).toBeNull();
    expect(datos.limiteCompra).toBeNull();
  });
});
