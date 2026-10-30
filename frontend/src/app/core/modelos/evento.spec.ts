import { EstadoEvento, terminaAlDiaSiguiente, yaTermino } from './evento';

// decisiones.md → Fecha y horario: si la hora de fin es menor que la de inicio,
// el evento termina al día siguiente
describe('terminaAlDiaSiguiente', () => {
  it('un evento de 22:00 a 03:00 termina al día siguiente', () => {
    expect(terminaAlDiaSiguiente('22:00:00', '03:00:00')).toBe(true);
  });

  it('un evento de 10:00 a 18:00 termina el mismo día', () => {
    expect(terminaAlDiaSiguiente('10:00:00', '18:00:00')).toBe(false);
  });

  // El backend manda HH:mm:ss y el formulario HH:mm: se comparan igual
  it('compara una hora con segundos y otra sin ellos', () => {
    expect(terminaAlDiaSiguiente('22:00', '03:00:00')).toBe(true);
    expect(terminaAlDiaSiguiente('10:00:00', '18:00')).toBe(false);
  });

  // Horas incompletas mientras se llena el formulario: todavía no hay aviso
  it('sin alguna de las dos horas no avisa', () => {
    expect(terminaAlDiaSiguiente('', '03:00')).toBe(false);
    expect(terminaAlDiaSiguiente('22:00', '')).toBe(false);
  });
});

// Espejo de Evento.seMuestraEnElCatalogo: la administración marca «Terminado» a
// los que el catálogo ya no muestra (decisiones.md → Catálogo de eventos)
describe('yaTermino', () => {
  function evento(estado: EstadoEvento, horaInicio: string, horaFin: string) {
    return { estado, fecha: '2026-10-04', horaInicio, horaFin };
  }

  // Hora local, como la ve el administrador
  function el(dia: number, hora: number, minuto: number): Date {
    return new Date(2026, 9, dia, hora, minuto);
  }

  it('termina al llegar su hora de fin, no antes', () => {
    const deTarde = evento('ACTIVO', '18:00:00', '20:00:00');

    expect(yaTermino(deTarde, el(4, 19, 59))).toBe(false);
    expect(yaTermino(deTarde, el(4, 20, 0))).toBe(true);
  });

  it('uno de 22:00 a 03:00 termina al día siguiente', () => {
    const nocturno = evento('POSTERGADO', '22:00:00', '03:00:00');

    expect(yaTermino(nocturno, el(5, 2, 59))).toBe(false);
    expect(yaTermino(nocturno, el(5, 3, 0))).toBe(true);
  });

  it('uno futuro no terminó', () => {
    expect(yaTermino(evento('ACTIVO', '10:00:00', '12:00:00'), el(1, 12, 0))).toBe(false);
  });

  // Un borrador o un cancelado no están a la venta: no «terminan»
  it('un borrador o un cancelado con fecha pasada no se marcan', () => {
    expect(yaTermino(evento('BORRADOR', '10:00:00', '12:00:00'), el(20, 12, 0))).toBe(false);
    expect(yaTermino(evento('CANCELADO', '10:00:00', '12:00:00'), el(20, 12, 0))).toBe(false);
  });

  // El día siguiente al último del mes
  it('uno que cruza la medianoche a fin de mes termina el 1 del siguiente', () => {
    const fin = { estado: 'ACTIVO' as const, fecha: '2026-10-31', horaInicio: '22:00:00', horaFin: '03:00:00' };

    expect(yaTermino(fin, new Date(2026, 10, 1, 2, 59))).toBe(false);
    expect(yaTermino(fin, new Date(2026, 10, 1, 3, 0))).toBe(true);
  });
});
