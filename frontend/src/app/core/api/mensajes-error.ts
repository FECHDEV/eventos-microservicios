import { ErrorApi, errorDelCampo } from '../modelos/error-api';

// Único lugar donde los códigos del backend se vuelven frases para la persona,
// para que el mismo error diga siempre lo mismo en todas las pantallas
// (docs/decisiones.md → Decisiones técnicas del frontend).
const MENSAJES: Record<string, string> = {
	CREDENCIALES_INCORRECTAS: 'El correo o la contraseña no son correctos.',
	CUENTA_DESACTIVADA: 'Esta cuenta está desactivada. Escríbenos para reactivarla.',
	INTENTOS_EXCEDIDOS: 'Demasiados intentos fallidos. Espera 15 minutos y vuelve a intentar.',
	SOLICITUDES_EXCEDIDAS: 'Ya pediste esto varias veces. Espera unos minutos y vuelve a intentar.',
	DATOS_INVALIDOS: 'Revisa los datos marcados.',
	CUENTA_EXISTENTE: 'Ya existe una cuenta con ese correo.',
	CONTRASENA_ACTUAL_INCORRECTA: 'La contraseña actual no es correcta.',
	TOKEN_RECUPERACION_INVALIDO: 'Este enlace ya no sirve. Pide uno nuevo.',
	USUARIO_NO_ENCONTRADO: 'No encontramos ese usuario.',
	ACCESO_DENEGADO: 'Tu cuenta no tiene acceso a esta parte del sistema.',
	NO_AUTENTICADO: 'Tu sesión terminó. Vuelve a iniciar sesión.',
	ROL_DE_CUENTA_DESACTIVADA: 'No se puede cambiar el rol de una cuenta desactivada. Reactívala primero.',
	ROL_PROPIO_NO_MODIFICABLE: 'No puedes cambiar tu propio rol.',
	CUENTA_PROPIA_NO_DESACTIVABLE: 'No puedes desactivar tu propia cuenta.',
	ULTIMO_ADMINISTRADOR_ACTIVO: 'Debe quedar al menos un administrador activo. Nombra a otro antes de hacer este cambio.',
	FECHA_NACIMIENTO_REQUERIDA: 'Esta cuenta no tiene fecha de nacimiento, que es obligatoria para el tipo Cliente.',
	OPERACION_SIMULTANEA: 'Otra persona está modificando estos datos. Vuelve a intentar.',
	EVENTO_NO_ENCONTRADO: 'No encontramos ese evento.',
	EVENTO_CANCELADO: 'Este evento está cancelado y ya no se puede modificar.',
	FECHA_NO_MODIFICABLE: 'Este evento ya está a la venta: para cambiar su fecha o su horario, postérgalo.',
	IMAGEN_INVALIDA: 'Ese archivo no es una imagen JPG, PNG o WebP.',
	IMAGEN_DEMASIADO_GRANDE: 'La imagen pesa más de 2 MB. Elige una más liviana.',
	IMAGEN_NO_ENCONTRADA: 'Este evento no tiene imagen.',
	TIPO_ENTRADA_NO_ENCONTRADO: 'No encontramos ese tipo de entrada en este evento.',
	TIPO_ENTRADA_DUPLICADO: 'Este evento ya tiene un tipo de entrada con ese nombre.',
	EVENTO_SIN_TIPOS_DE_ENTRADA: 'Agrega al menos un tipo de entrada antes de activar el evento.',
	CAMBIO_DE_ESTADO_NO_PERMITIDO: 'Ese cambio ya no corresponde al estado del evento. Revisa su estado actual.',
	VENTA_YA_CERRADA: 'La venta de este evento ya está cerrada.',
	VENTA_CERRADA: 'La venta de este evento está cerrada.',
	ENTRADAS_NO_DISPONIBLES: 'No quedan suficientes entradas de este tipo para esa cantidad. Prueba con una cantidad menor.',
	EDAD_NO_PERMITIDA: 'No cumples la edad mínima de este evento a la fecha en que se realiza.',
	LIMITE_COMPRA_EXCEDIDO: 'Con esa cantidad superas el límite de entradas por cliente de este evento.',
	RESERVA_NO_ENCONTRADA: 'No encontramos esa reserva.',
	RESERVA_NO_VIGENTE: 'Tu reserva ya venció. Vuelve al evento para reservar de nuevo.',
	ERROR_SISTEMA_PAGO: 'No pudimos generar el cobro y liberamos tus entradas. Intenta reservar de nuevo.',
	PAGO_NO_ENCONTRADO: 'Todavía no generaste el cobro de esta reserva.',
	ENTRADA_NO_ENCONTRADA: 'No encontramos esa entrada.',
	EVENTO_NO_CANCELADO: 'Ese evento no está cancelado.',
	ENTRADA_YA_DEVUELTA: 'Esa devolución ya estaba registrada.',
	ENTRADA_NO_DEVOLVIBLE: 'Una entrada utilizada no se devuelve.',
	COMPRA_NO_ENCONTRADA: 'No encontramos esa compra.',
	EVENTO_NO_DISPONIBLE: 'Este evento ya no admite validar entradas. Elige otro de la lista.',
	REPORTE_SIN_RESULTADOS: 'No hay datos que cumplan esos criterios. Prueba con otros filtros.',
	REPORTE_NO_ENCONTRADO: 'No encontramos ese reporte. Vuelve a generarlo.',
	SIN_CONEXION: 'No pudimos conectar con el servidor. Intenta de nuevo.',
};

const GENERICO = 'No pudimos completar la operación. Intenta de nuevo.';

// El `detail` del backend no se muestra tal cual: puede traer detalle técnico.
// Un error inesperado (500) llega sin `codigo` y cae en el mensaje genérico.
export function mensajeDeError(error: ErrorApi | null): string {
	if (error === null) {
		return '';
	}
	const frase = (error.codigo === undefined ? undefined : MENSAJES[error.codigo]) ?? GENERICO;
	const detalle = detalleDelRechazo(error);
	return detalle === '' ? frase : `${frase} ${detalle}`;
}

// CU-06: algunos rechazos de la compra traen datos para explicarlos. FA-03
// vuelve al paso 1, así que se dice cuántas puede agregar todavía
function detalleDelRechazo(error: ErrorApi): string {
	if (error.codigo === 'LIMITE_COMPRA_EXCEDIDO' && error.puedeAgregar !== undefined) {
		return error.puedeAgregar === 0
			? 'Ya llegaste al límite de este evento.'
			: `Puedes agregar ${error.puedeAgregar} más.`;
	}
	if (error.codigo === 'EDAD_NO_PERMITIDA' && error.edadMinima !== undefined) {
		return `Es para mayores de ${error.edadMinima} años.`;
	}
	return '';
}

// Los errores por campo del backend son predicados pensados para leerse detrás
// del nombre del dato. Se arman acá, que es el único lugar donde la respuesta
// de la API se vuelve una frase (docs/decisiones.md).
const NOMBRES_DE_CAMPO: Record<string, string> = {
	nombre: 'El nombre',
	apellido: 'El apellido',
	correo: 'El correo',
	contrasena: 'La contraseña',
	contrasenaActual: 'La contraseña actual',
	contrasenaNueva: 'La contraseña nueva',
	telefono: 'El teléfono',
	fechaNacimiento: 'La fecha de nacimiento',
	descripcion: 'La descripción',
	fecha: 'La fecha',
	horaInicio: 'La hora de inicio',
	horaFin: 'La hora de fin',
	lugar: 'El lugar',
	restriccionEdad: 'La edad mínima',
	limiteCompra: 'El límite por cliente',
	imagen: 'La imagen',
	precio: 'El precio',
	cantidadTotal: 'La cantidad',
	texto: 'La búsqueda',
	desde: 'La fecha desde',
	hasta: 'La fecha hasta',
	cantidad: 'La cantidad',
	idTipoEntrada: 'El tipo de entrada',
	idEvento: 'El evento',
	estado: 'El estado',
	idPersonal: 'El personal',
	tipo: 'El tipo de reporte',
	destinatarios: 'Los destinatarios',
};

export function mensajeDeCampo(campo: string, mensaje: string): string {
	const nombre = NOMBRES_DE_CAMPO[campo];
	const frase = nombre === undefined ? mensaje : `${nombre} ${mensaje}`;
	return frase.endsWith('.') ? frase : `${frase}.`;
}

// Lo mínimo que hace falta de un campo de Signal Forms para elegir su mensaje
export interface EstadoDelCampo {
	touched(): boolean;
	invalid(): boolean;
	errors(): readonly { message?: string }[];
}

// El mensaje bajo un campo: primero lo que detectó la pantalla (cuando la
// persona ya pasó por el campo) y, si ahí no hay nada, lo que respondió el
// backend para ese campo
export function mensajeDelCampo(estado: EstadoDelCampo, campo: string, error: ErrorApi | null): string {
	if (estado.touched() && estado.invalid()) {
		return primerMensaje(estado.errors());
	}
	const delServidor = errorDelCampo(error, campo);
	return delServidor === null ? '' : mensajeDeCampo(campo, delServidor);
}

// El primer error de un campo que trae mensaje: el que se muestra debajo
export function primerMensaje(errores: readonly { message?: string }[]): string {
	return errores.find((cada) => cada.message !== undefined)?.message ?? '';
}
