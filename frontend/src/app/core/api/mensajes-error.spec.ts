import { ErrorApi } from '../modelos/error-api';
import { mensajeDeCampo, mensajeDeError, mensajeDelCampo, primerMensaje } from './mensajes-error';

function error(codigo: string | undefined, detail = 'detalle técnico del backend'): ErrorApi {
  return { status: 401, detail, codigo };
}

describe('mensajeDeError', () => {
  // CU-09: cada operación rechazada explica su motivo, no el mensaje genérico
  it.each([
    ['ROL_PROPIO_NO_MODIFICABLE', 'propio rol'],
    ['CUENTA_PROPIA_NO_DESACTIVABLE', 'propia cuenta'],
    ['ULTIMO_ADMINISTRADOR_ACTIVO', 'administrador activo'],
    ['FECHA_NACIMIENTO_REQUERIDA', 'fecha de nacimiento'],
  ])('traduce %s', (codigo, motivo) => {
    expect(mensajeDeError(error(codigo))).toContain(motivo);
  });

  it('traduce las credenciales incorrectas', () => {
    expect(mensajeDeError(error('CREDENCIALES_INCORRECTAS'))).toBe(
      'El correo o la contraseña no son correctos.',
    );
  });

  it('traduce la cuenta desactivada', () => {
    expect(mensajeDeError(error('CUENTA_DESACTIVADA'))).toContain('desactivada');
  });

  it('traduce el bloqueo por intentos fallidos', () => {
    expect(mensajeDeError(error('INTENTOS_EXCEDIDOS'))).toContain('15 minutos');
  });

  it('traduce el conflicto por operación simultánea', () => {
    expect(mensajeDeError(error('OPERACION_SIMULTANEA'))).toContain('Vuelve a intentar');
  });

  // Desde Gestión de Eventos el bloqueo también protege eventos y tipos de
  // entrada: el mensaje no puede seguir hablando de una cuenta
  it('el conflicto por operación simultánea no habla de una cuenta', () => {
    expect(mensajeDeError(error('OPERACION_SIMULTANEA'))).not.toContain('cuenta');
  });

  // CU-10, CU-11 y CU-12: cada rechazo explica su motivo
  it.each([
    ['EVENTO_NO_ENCONTRADO', 'evento'],
    ['EVENTO_CANCELADO', 'cancelado'],
    ['FECHA_NO_MODIFICABLE', 'postérgalo'],
    ['IMAGEN_INVALIDA', 'JPG, PNG o WebP'],
    ['IMAGEN_DEMASIADO_GRANDE', '2 MB'],
    ['IMAGEN_NO_ENCONTRADA', 'imagen'],
    ['TIPO_ENTRADA_NO_ENCONTRADO', 'tipo de entrada'],
    ['TIPO_ENTRADA_DUPLICADO', 'ese nombre'],
    ['EVENTO_SIN_TIPOS_DE_ENTRADA', 'tipo de entrada'],
    ['CAMBIO_DE_ESTADO_NO_PERMITIDO', 'estado'],
    ['VENTA_YA_CERRADA', 'cerrada'],
  ])('traduce %s', (codigo, motivo) => {
    const mensaje = mensajeDeError(error(codigo));

    expect(mensaje).toContain(motivo);
    expect(mensaje).not.toBe(mensajeDeError(error('ALGO_RARO')));
  });

  it('usa un mensaje propio para un código desconocido, sin mostrar el del backend', () => {
    const mensaje = mensajeDeError(error('ALGO_RARO'));

    expect(mensaje).not.toContain('detalle técnico');
    expect(mensaje).not.toBe('');
  });

  // Un 500 llega sin la extensión `codigo`
  it('un error inesperado también tiene mensaje', () => {
    expect(mensajeDeError({ status: 500, detail: 'Ocurrió un error inesperado.' })).not.toBe('');
  });

  it('sin error devuelve cadena vacía', () => {
    expect(mensajeDeError(null)).toBe('');
  });
});

describe('mensajeDeError de la compra (CU-06)', () => {
  it.each([
    ['VENTA_CERRADA', 'La venta de este evento está cerrada.'],
    ['ENTRADAS_NO_DISPONIBLES', 'No quedan suficientes entradas de este tipo para esa cantidad. Prueba con una cantidad menor.'],
    ['EDAD_NO_PERMITIDA', 'No cumples la edad mínima de este evento a la fecha en que se realiza.'],
    ['LIMITE_COMPRA_EXCEDIDO', 'Con esa cantidad superas el límite de entradas por cliente de este evento.'],
    ['RESERVA_NO_ENCONTRADA', 'No encontramos esa reserva.'],
  ])('traduce %s', (codigo, frase) => {
    expect(mensajeDeError(error(codigo))).toBe(frase);
  });

  // FA-03 vuelve al paso 1: se le dice cuántas puede agregar todavía
  it('al límite excedido le suma cuántas puede agregar', () => {
    expect(mensajeDeError({ ...error('LIMITE_COMPRA_EXCEDIDO'), limite: 4, puedeAgregar: 1 })).toBe(
      'Con esa cantidad superas el límite de entradas por cliente de este evento. Puedes agregar 1 más.',
    );
    expect(mensajeDeError({ ...error('LIMITE_COMPRA_EXCEDIDO'), limite: 4, puedeAgregar: 0 })).toBe(
      'Con esa cantidad superas el límite de entradas por cliente de este evento. Ya llegaste al límite de este evento.',
    );
  });

  it('a la edad no permitida le suma la edad mínima', () => {
    expect(mensajeDeError({ ...error('EDAD_NO_PERMITIDA'), edadMinima: 18 })).toBe(
      'No cumples la edad mínima de este evento a la fecha en que se realiza. Es para mayores de 18 años.',
    );
  });

  it('nombra los campos de la reserva', () => {
    expect(mensajeDeCampo('cantidad', 'debe ser de al menos 1')).toBe('La cantidad debe ser de al menos 1.');
    expect(mensajeDeCampo('idTipoEntrada', 'es obligatorio')).toBe('El tipo de entrada es obligatorio.');
  });
});

describe('mensajeDeError del pago (CU-07)', () => {
  it.each([
    ['RESERVA_NO_VIGENTE', 'Tu reserva ya venció. Vuelve al evento para reservar de nuevo.'],
    ['ERROR_SISTEMA_PAGO', 'No pudimos generar el cobro y liberamos tus entradas. Intenta reservar de nuevo.'],
    ['PAGO_NO_ENCONTRADO', 'Todavía no generaste el cobro de esta reserva.'],
  ])('traduce %s', (codigo, frase) => {
    expect(mensajeDeError(error(codigo))).toBe(frase);
  });
});

describe('mensajeDeError de la validación (CU-15)', () => {
  it('traduce EVENTO_NO_DISPONIBLE', () => {
    expect(mensajeDeError(error('EVENTO_NO_DISPONIBLE'))).toBe('Este evento ya no admite validar entradas. Elige otro de la lista.');
  });
});

describe('mensajeDeError de las devoluciones (CU-13)', () => {
  it.each([
    ['EVENTO_NO_CANCELADO', 'Ese evento no está cancelado.'],
    ['ENTRADA_YA_DEVUELTA', 'Esa devolución ya estaba registrada.'],
    ['ENTRADA_NO_DEVOLVIBLE', 'Una entrada utilizada no se devuelve.'],
    ['COMPRA_NO_ENCONTRADA', 'No encontramos esa compra.'],
  ])('traduce %s', (codigo, frase) => {
    expect(mensajeDeError(error(codigo))).toBe(frase);
  });
});

describe('mensajeDeError de los reportes (CU-14)', () => {
  it.each([
    ['REPORTE_SIN_RESULTADOS', 'No hay datos que cumplan esos criterios. Prueba con otros filtros.'],
    ['REPORTE_NO_ENCONTRADO', 'No encontramos ese reporte. Vuelve a generarlo.'],
  ])('traduce %s', (codigo, frase) => {
    expect(mensajeDeError(error(codigo))).toBe(frase);
  });

  // RF-4: los campos de los filtros tienen nombre
  it('nombra los campos de los filtros', () => {
    expect(mensajeDeCampo('estado', 'no corresponde a este reporte')).toBe(
      'El estado no corresponde a este reporte.',
    );
    expect(mensajeDeCampo('idEvento', 'es obligatorio')).toBe('El evento es obligatorio.');
    expect(mensajeDeCampo('idPersonal', 'no corresponde a este reporte')).toBe(
      'El personal no corresponde a este reporte.',
    );
  });
});

describe('mensajeDeError de las entradas (CU-08)', () => {
  it('traduce ENTRADA_NO_ENCONTRADA', () => {
    expect(mensajeDeError(error('ENTRADA_NO_ENCONTRADA'))).toBe('No encontramos esa entrada.');
  });
});

// El backend manda el mensaje del campo como predicado, para leerse detrás del
// nombre del dato: "es obligatorio", "admite hasta 80 caracteres"
describe('mensajeDeCampo', () => {
  // Los filtros del catálogo (CU-05 RF-10)
  it('nombra los campos de búsqueda del catálogo', () => {
    expect(mensajeDeCampo('texto', 'admite hasta 200 caracteres')).toBe('La búsqueda admite hasta 200 caracteres.');
    expect(mensajeDeCampo('desde', 'no tiene un formato válido')).toBe('La fecha desde no tiene un formato válido.');
    expect(mensajeDeCampo('hasta', 'no puede ser anterior a la fecha desde')).toBe(
      'La fecha hasta no puede ser anterior a la fecha desde.',
    );
  });

  it('arma una frase con el nombre del campo', () => {
    expect(mensajeDeCampo('nombre', 'es obligatorio')).toBe('El nombre es obligatorio.');
    expect(mensajeDeCampo('contrasena', 'admite hasta 72 bytes')).toBe(
      'La contraseña admite hasta 72 bytes.',
    );
    expect(mensajeDeCampo('fechaNacimiento', 'no puede ser una fecha futura')).toBe(
      'La fecha de nacimiento no puede ser una fecha futura.',
    );
  });

  // CU-04: los dos campos del cambio de contraseña
  it('nombra las contraseñas actual y nueva', () => {
    expect(mensajeDeCampo('contrasenaActual', 'es obligatoria')).toBe(
      'La contraseña actual es obligatoria.',
    );
    expect(mensajeDeCampo('contrasenaNueva', 'debe tener al menos 8 caracteres')).toBe(
      'La contraseña nueva debe tener al menos 8 caracteres.',
    );
  });

  // Los campos de CU-10 y CU-11, con los predicados que manda el backend
  it('nombra los campos de un evento y de un tipo de entrada', () => {
    expect(mensajeDeCampo('fecha', 'no puede ser anterior a hoy')).toBe(
      'La fecha no puede ser anterior a hoy.',
    );
    expect(mensajeDeCampo('horaFin', 'debe ser distinta de la hora de inicio')).toBe(
      'La hora de fin debe ser distinta de la hora de inicio.',
    );
    expect(mensajeDeCampo('horaInicio', 'es obligatoria')).toBe('La hora de inicio es obligatoria.');
    expect(mensajeDeCampo('lugar', 'admite hasta 200 caracteres')).toBe(
      'El lugar admite hasta 200 caracteres.',
    );
    expect(mensajeDeCampo('descripcion', 'es demasiado larga')).toBe('La descripción es demasiado larga.');
    expect(mensajeDeCampo('restriccionEdad', 'debe estar entre 1 y 99')).toBe(
      'La edad mínima debe estar entre 1 y 99.',
    );
    expect(mensajeDeCampo('limiteCompra', 'debe ser de al menos 1')).toBe(
      'El límite por cliente debe ser de al menos 1.',
    );
    expect(mensajeDeCampo('imagen', 'es obligatoria')).toBe('La imagen es obligatoria.');
    expect(mensajeDeCampo('precio', 'debe ser mayor que 0')).toBe('El precio debe ser mayor que 0.');
    expect(
      mensajeDeCampo('cantidadTotal', 'no puede ser menor que 30, lo ya vendido y reservado'),
    ).toBe('La cantidad no puede ser menor que 30, lo ya vendido y reservado.');
  });

  it('no duplica el punto final', () => {
    expect(mensajeDeCampo('correo', 'no tiene formato de correo.')).toBe(
      'El correo no tiene formato de correo.',
    );
  });

  // Un campo que todavía no esté en la tabla no puede quedarse sin mensaje
  it('un campo desconocido se muestra tal como vino', () => {
    expect(mensajeDeCampo('otroCampo', 'no es válido')).toBe('no es válido.');
  });
});

// El mensaje que se muestra bajo un campo: primero lo que detectó la pantalla y,
// si ahí no hay nada, lo que respondió el backend para ese campo
describe('mensajeDelCampo', () => {
  function estado(tocado: boolean, errores: string[]) {
    return {
      touched: () => tocado,
      invalid: () => errores.length > 0,
      errors: () => errores.map((message) => ({ message })),
    };
  }

  const RECHAZO: ErrorApi = {
    status: 400,
    detail: 'Hay campos a corregir.',
    codigo: 'DATOS_INVALIDOS',
    errores: [{ campo: 'lugar', mensaje: 'admite hasta 200 caracteres' }],
  };

  it('muestra el error de la pantalla cuando el campo ya se tocó', () => {
    expect(mensajeDelCampo(estado(true, ['Escribe el lugar.']), 'lugar', RECHAZO)).toBe('Escribe el lugar.');
  });

  it('sin tocar, el error de la pantalla todavía no se muestra', () => {
    expect(mensajeDelCampo(estado(false, ['Escribe el lugar.']), 'lugar', null)).toBe('');
  });

  it('si la pantalla no ve nada, muestra lo que rechazó el backend', () => {
    expect(mensajeDelCampo(estado(true, []), 'lugar', RECHAZO)).toBe('El lugar admite hasta 200 caracteres.');
  });

  it('un campo sin problemas no muestra nada', () => {
    expect(mensajeDelCampo(estado(true, []), 'nombre', RECHAZO)).toBe('');
  });
});

describe('primerMensaje', () => {
  it('devuelve el primer error que trae mensaje', () => {
    expect(primerMensaje([{}, { message: 'Escribe tu correo.' }, { message: 'otro' }])).toBe('Escribe tu correo.');
  });

  it('sin mensajes devuelve vacío', () => {
    expect(primerMensaje([])).toBe('');
    expect(primerMensaje([{}])).toBe('');
  });
});
