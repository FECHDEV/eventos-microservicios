"""Carga los datos de demostración: Sarao como si una empresa ya lo usara.

No se corre solo: lo llama scripts/datos-de-demostracion.sh, que deja pagos y correos en simulador mientras corre.

Usa la API para todo lo que tiene reglas (cuentas del personal, eventos, imágenes, compras con su QR, postergación,
cancelación, devoluciones y reportes) y SQL solo para lo que la API no deja hacer: mover al pasado las fechas de lo
ya ocurrido, los accesos de los eventos realizados, la fecha de alta de las cuentas y el contador de visitas. Los
clientes inventados entran por SQL con la misma contraseña cifrada que el primero, que sí se registra por la API: el
registro tiene un límite por hora y conexión.

Variables: SARAO_API, SARAO_DIA (AAAA-MM-DD, el día de la demostración), SARAO_ADMIN_CORREO, SARAO_ADMIN_CLAVE,
CUENTAS_CLAVE (la de las cuentas de prueba y los clientes) y DOMINIO.
"""
import json
import os
import random
import subprocess
import time as time_
import sys
import unicodedata
import urllib.error
import urllib.request
import uuid
from datetime import date, datetime, time, timedelta
from pathlib import Path

AQUI = Path(__file__).parent
API = os.environ['SARAO_API']
DIA = date.fromisoformat(os.environ['SARAO_DIA'])
CLAVE = os.environ['CUENTAS_CLAVE']
DOMINIO = os.environ.get('DOMINIO') or 'mail.com'
AHORA = datetime.now().replace(microsecond=0)
rnd = random.Random(2026)

NOMBRES = ['Valeria', 'Camila', 'Andrea', 'Gabriela', 'Fernanda', 'Natalia', 'Carla', 'Mariana', 'Sofía', 'Ximena',
           'Patricia', 'Rocío', 'Alejandra', 'Melany', 'Luis', 'Carlos', 'José', 'Miguel', 'Diego', 'Mauricio',
           'Marcelo', 'Javier', 'Sergio', 'Fernando', 'Ronald', 'Gustavo', 'Erick', 'Bruno', 'Henry', 'Álvaro']
APELLIDOS = ['Antelo', 'Saucedo', 'Ribera', 'Montaño', 'Egüez', 'Chávez', 'Áñez', 'Roca', 'Moreno', 'Flores', 'Vargas',
             'Salvatierra', 'Cuéllar', 'Arteaga', 'Zabala', 'Ortiz', 'Gutiérrez', 'Rocha', 'Melgar', 'Paz', 'Soliz',
             'Aguilera', 'Barba', 'Durán', 'Parada']
CLIENTES = 60

# Personal además de las cuentas de prueba de scripts/cuentas-de-prueba.sh
PERSONAL_EXTRA = [('kevin.rocha.95', 'Kevin', 'Rocha', 'PERSONAL'), ('paola.justiniano.92', 'Paola', 'Justiniano', 'PERSONAL'),
                  ('andres.melgar.87', 'Andrés', 'Melgar', 'ORGANIZADOR')]
CONTROL = ['jorge.mendez.93', 'kevin.rocha.95', 'paola.justiniano.92']


def correo(usuario):
    return f'{usuario}@{DOMINIO}'


def sin_tildes(texto):
    return ''.join(c for c in unicodedata.normalize('NFD', texto) if unicodedata.category(c) != 'Mn')


# --- HTTP ---

def pedir(metodo, ruta, cuerpo=None, token=None, esperado=(200, 201, 202, 204), multipart=None):
    cabeceras = {}
    datos = None
    if token:
        cabeceras['Authorization'] = 'Bearer ' + token
    if multipart:
        limite = uuid.uuid4().hex
        nombre, contenido = multipart
        datos = (f'--{limite}\r\nContent-Disposition: form-data; name="imagen"; filename="{nombre}"\r\n'
                 f'Content-Type: image/jpeg\r\n\r\n').encode() + contenido + f'\r\n--{limite}--\r\n'.encode()
        cabeceras['Content-Type'] = f'multipart/form-data; boundary={limite}'
    elif cuerpo is not None:
        datos = json.dumps(cuerpo).encode()
        cabeceras['Content-Type'] = 'application/json'
    peticion = urllib.request.Request(API + ruta, data=datos, method=metodo, headers=cabeceras)
    try:
        with urllib.request.urlopen(peticion, timeout=60) as respuesta:
            codigo, texto = respuesta.status, respuesta.read()
    except urllib.error.HTTPError as e:
        codigo, texto = e.code, e.read()
    if codigo not in esperado:
        raise SystemExit(f'FALLA {metodo} {ruta}: {codigo} {texto[:300]!r}')
    return json.loads(texto) if texto else None


_tokens = {}


def token(usuario_o_correo, clave=CLAVE):
    direccion = usuario_o_correo if '@' in usuario_o_correo else correo(usuario_o_correo)
    if direccion not in _tokens:
        _tokens[direccion] = pedir('POST', '/auth/inicio-sesion', {'correo': direccion, 'contrasena': clave})['token']
    return _tokens[direccion]


# --- SQL, solo para lo que la API no hace ---

COMPOSE = ['docker', 'compose']


def sql(sentencias):
    resultado = subprocess.run(
        COMPOSE + ['exec', '-T', 'base', 'sh', '-c', 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql -uroot -N --default-character-set=utf8mb4'],
        input=sentencias.encode('utf-8'), capture_output=True)
    if resultado.returncode != 0:
        raise SystemExit('FALLA SQL: ' + resultado.stderr.decode('utf-8', 'replace')[:500])
    return [linea.split('\t') for linea in resultado.stdout.decode('utf-8').splitlines() if linea]


def texto_sql(valor):
    return "'" + str(valor).replace('\\', '\\\\').replace("'", "''") + "'"


def momento(valor):
    return "'" + valor.strftime('%Y-%m-%d %H:%M:%S') + "'"


def al_azar_entre(desde, hasta):
    return desde + timedelta(seconds=rnd.randint(0, max(1, int((hasta - desde).total_seconds()))))


# --- Pasos ---

def personal(admin):
    for usuario, nombre, apellido, rol in PERSONAL_EXTRA:
        pedir('POST', '/usuarios', {'nombre': nombre, 'apellido': apellido, 'correo': correo(usuario), 'contrasena': CLAVE,
                                    'telefono': f'7{rnd.randint(1000000, 9999999)}', 'rol': rol},
              token=admin, esperado=(201, 409))


def clientes():
    nombres = set()
    while len(nombres) < CLIENTES:
        nombres.add((rnd.choice(NOMBRES), rnd.choice(APELLIDOS), rnd.randint(1978, 2005)))
    nombres = sorted(nombres)
    primero, resto = nombres[0], nombres[1:]

    def datos(nombre, apellido, anio):
        usuario = sin_tildes(f'{nombre}.{apellido}.{anio % 100:02d}').lower()
        nacimiento = date(anio, rnd.randint(1, 12), rnd.randint(1, 28))
        return usuario, nacimiento

    usuario, nacimiento = datos(*primero)
    pedir('POST', '/auth/registro', {'nombre': primero[0], 'apellido': primero[1], 'correo': correo(usuario),
                                     'contrasena': CLAVE, 'telefono': f'7{rnd.randint(1000000, 9999999)}',
                                     'fechaNacimiento': nacimiento.isoformat()}, esperado=(201, 409))
    filas = []
    for nombre, apellido, anio in resto:
        usuario, nacimiento = datos(nombre, apellido, anio)
        alta = al_azar_entre(datetime.combine(DIA - timedelta(days=170), time(8)), datetime.combine(DIA - timedelta(days=95), time(22)))
        filas.append(f"({texto_sql(nombre)}, {texto_sql(apellido)}, {texto_sql(correo(usuario))}, @hash, "
                     f"'7{rnd.randint(1000000, 9999999)}', 'CLIENTE', 'ACTIVA', '{nacimiento}', {momento(alta)})")
    sql(f"""
        SET @hash = (SELECT contrasena FROM seguridad_db.usuario WHERE correo = {texto_sql(correo(datos(*primero)[0]))});
        INSERT IGNORE INTO seguridad_db.usuario (nombre, apellido, correo, contrasena, telefono, rol, estado,
            fecha_nacimiento, fecha_registro) VALUES {', '.join(filas)};""")
    filas = sql("SELECT correo FROM seguridad_db.usuario WHERE rol = 'CLIENTE'")
    return [fila[0] for fila in filas]


def crear_eventos(organizador):
    eventos = json.loads((AQUI / 'eventos.json').read_text(encoding='utf-8'))
    ya = {e['nombre'] for e in pedir('GET', '/eventos?tamano=100', token=organizador)['contenido']}
    if any(evento['nombre'] in ya for evento in eventos):
        raise SystemExit('Ya hay datos de demostración en esta base: no se vuelven a cargar.')
    manana = date.today() + timedelta(days=2)
    for evento in eventos:
        # Lo ya ocurrido nace en el futuro para poder vender; al final se mueve a su fecha
        final = DIA + timedelta(days=evento['dias'])
        inicial = DIA + timedelta(days=evento.get('fechaOriginal', evento['dias']))
        evento['fechaFinal'] = final
        creado = pedir('POST', '/eventos', {
            'nombre': evento['nombre'], 'descripcion': evento['descripcion'], 'fecha': max(inicial, manana).isoformat(),
            'horaInicio': evento['horaInicio'], 'horaFin': evento['horaFin'], 'lugar': evento['lugar'],
            'restriccionEdad': evento['restriccionEdad'], 'limiteCompra': evento['limiteCompra']}, token=organizador)
        evento['id'] = creado['idEvento']
        imagen = AQUI / 'imagenes' / f"{evento['slug']}.jpg"
        pedir('POST', f"/eventos/{evento['id']}/imagen", token=organizador, multipart=(imagen.name, imagen.read_bytes()))
        for tipo in evento['tipos']:
            tipo['id'] = pedir('POST', f"/eventos/{evento['id']}/tipos-entrada",
                               {'nombre': tipo['nombre'], 'precio': tipo['precio'], 'cantidadTotal': tipo['total']},
                               token=organizador)['idTipoEntrada']
        if evento['final'] != 'borrador':
            pedir('POST', f"/eventos/{evento['id']}/activacion", token=organizador)
        print(f"  evento    {evento['nombre']}")
    return eventos


def comprar(cliente, tipo, cantidad):
    t = token(cliente)
    reserva = pedir('POST', '/reservas', {'idTipoEntrada': tipo, 'cantidad': cantidad}, token=t)['idReserva']
    pedir('POST', f'/reservas/{reserva}/pago', token=t)
    pedir('POST', f'/reservas/{reserva}/pago/simulacion', {'resultado': 'PAGADO'}, token=t)
    # Como la pantalla de la reserva: consultar el pago es lo que lo verifica y confirma la compra
    for _ in range(20):
        if pedir('GET', f'/reservas/{reserva}/pago', token=t)['estadoReserva'] == 'CONFIRMADA':
            return reserva
        time_.sleep(0.5)
    raise SystemExit(f'FALLA la compra {reserva} no se confirmó')


def ventas(eventos, clientes_):
    compras = []  # (idReserva, evento, momento de la compra)
    prueba = correo('lucia.rojas.98')
    for evento in eventos:
        if evento['ocupacion'] == 0:
            continue
        final = datetime.combine(evento['fechaFinal'], time(12))
        desde = final - timedelta(days=40)
        hasta = min(final - timedelta(days=1), AHORA - timedelta(minutes=30))
        desde = min(desde, hasta - timedelta(days=1))
        comprado = {}
        # La clienta de prueba tiene entradas de lo pasado, de hoy y de lo que viene, para mostrarlas en la demo
        if evento['slug'] in ('rock-del-oriente', 'jazz-bajo-las-estrellas', 'fiesta-neon'):
            compras.append((comprar(prueba, evento['tipos'][0]['id'], 2), evento, al_azar_entre(desde, hasta)))
            comprado[prueba] = 2
        for tipo in evento['tipos']:
            meta = int(tipo['total'] * evento['ocupacion'])
            vendidas = 2 if prueba in comprado and tipo is evento['tipos'][0] else 0
            intentos = 0
            while vendidas < meta and intentos < 400:
                intentos += 1
                cliente = rnd.choice(clientes_)
                cantidad = min(rnd.choices([1, 2, 3, 4], [35, 35, 15, 15])[0], meta - vendidas,
                               evento['limiteCompra'] - comprado.get(cliente, 0))
                if cantidad < 1:
                    continue
                compras.append((comprar(cliente, tipo['id'], cantidad), evento, al_azar_entre(desde, hasta)))
                comprado[cliente] = comprado.get(cliente, 0) + cantidad
                vendidas += cantidad
        print(f"  ventas    {evento['nombre']}: {sum(comprado.values())} entradas")
    return compras


def cambios_de_estado(eventos, compras, organizador, admin):
    for evento in eventos:
        if evento['final'] == 'postergado':
            pedir('POST', f"/eventos/{evento['id']}/postergacion", {'fecha': evento['fechaFinal'].isoformat()},
                  token=organizador)
        elif evento['final'] == 'cancelado':
            pedir('POST', f"/eventos/{evento['id']}/cancelacion", token=organizador)
            # La mayoría ya pasó a buscar su devolución; el resto sigue pendiente
            for reserva, de, _ in compras:
                if de is evento and rnd.random() < 0.65:
                    pedir('POST', f'/devoluciones/compras/{reserva}', token=admin)


def historia(eventos, compras):
    sentencias = []
    for reserva, _, cuando in compras:
        sentencias.append(
            f"UPDATE eventos_db.reserva SET fecha_creacion = {momento(cuando)}, "
            f"fecha_expiracion = {momento(cuando + timedelta(minutes=10))} WHERE id_reserva = {reserva};"
            f"UPDATE eventos_db.entrada SET fecha_emision = {momento(cuando + timedelta(minutes=2))} WHERE id_reserva = {reserva};"
            f"UPDATE pagos_db.pago SET fecha_pago = {momento(cuando + timedelta(minutes=2))}, "
            f"fecha_expiracion = {momento(cuando + timedelta(minutes=10))} WHERE id_reserva = {reserva};")
    for evento in eventos:
        if evento['final'] in ('realizado', 'activo') and evento['dias'] <= 0:
            abierta = "b'0'" if evento['final'] == 'realizado' else "b'1'"
            sentencias.append(f"UPDATE eventos_db.evento SET fecha = '{evento['fechaFinal']}', venta_abierta = {abierta} "
                              f"WHERE id_evento = {evento['id']};")
    sql('\n'.join(sentencias))

    # Accesos de lo ya realizado: casi todos llegaron, repartidos en la primera hora y media
    ids = dict(sql("SELECT correo, id_usuario FROM seguridad_db.usuario WHERE correo IN ("
                   + ', '.join(texto_sql(correo(u)) for u in CONTROL) + ')'))
    personal_ = list(ids.values())
    accesos = []
    for evento in eventos:
        if evento['final'] != 'realizado':
            continue
        entradas = sql("SELECT e.id_entrada FROM eventos_db.entrada e JOIN eventos_db.tipo_entrada t "
                       f"ON t.id_tipo_entrada = e.id_tipo_entrada WHERE t.id_evento = {evento['id']}")
        inicio = datetime.combine(evento['fechaFinal'], time.fromisoformat(evento['horaInicio']))
        for (entrada,) in entradas:
            if rnd.random() < 0.92:
                cuando = inicio - timedelta(minutes=30) + timedelta(seconds=rnd.randint(0, 120 * 60))
                accesos.append(f"({momento(cuando)}, {rnd.choice(personal_)}, {entrada})")
    if accesos:
        sql(f"INSERT INTO eventos_db.acceso (fecha_hora, id_usuario, id_entrada) VALUES {', '.join(accesos)};"
            "UPDATE eventos_db.entrada SET estado = 'UTILIZADA' WHERE id_entrada IN "
            "(SELECT id_entrada FROM (SELECT id_entrada FROM eventos_db.acceso) a);")
    print(f'  accesos   {len(accesos)} en los eventos realizados')

    # El personal llegó antes que los clientes; el contador de visitas, el de un sitio con meses de uso
    sql(f"UPDATE seguridad_db.usuario SET fecha_registro = {momento(datetime.combine(DIA - timedelta(days=200), time(9)))} "
        "WHERE rol <> 'CLIENTE';"
        # Los clientes que se registraron por la API (el primero y la de prueba) también son de hace meses
        f"UPDATE seguridad_db.usuario SET fecha_registro = {momento(datetime.combine(DIA - timedelta(days=120), time(19)))} "
        f"WHERE rol = 'CLIENTE' AND fecha_registro > {momento(datetime.combine(DIA - timedelta(days=60), time(0)))};")
    visitas = {'PORTADA': 8412, 'DETALLE_DE_EVENTO': 5233, 'SELECCION_DE_ENTRADAS': 1876, 'RESERVA': 1204,
               'MIS_ENTRADAS': 934, 'DETALLE_DE_ENTRADA': 711, 'INICIO_SESION': 2318, 'REGISTRO': 402,
               'RECUPERACION': 57, 'RESTABLECIMIENTO': 41, 'PANEL': 388, 'MIS_DATOS': 126, 'USUARIOS': 97,
               'EVENTOS': 264, 'NUEVO_EVENTO': 31, 'FICHA_DE_EVENTO': 219, 'DEVOLUCIONES': 22,
               'DEVOLUCIONES_DEL_EVENTO': 14, 'REPORTES': 143, 'VALIDACION': 176}
    sql('INSERT INTO eventos_db.visita_pagina (pagina, total) VALUES '
        + ', '.join(f"('{p}', {t})" for p, t in visitas.items())
        + ' ON DUPLICATE KEY UPDATE total = total + VALUES(total);')


def reportes(contador):
    for tipo in ('VENTAS', 'INGRESOS', 'ENTRADAS', 'CLIENTES', 'ACCESOS', 'VENTAS', 'INGRESOS'):
        pedir('POST', '/reportes', {'tipo': tipo}, token=contador)
    filas = sql('SELECT id_reporte FROM eventos_db.reporte ORDER BY id_reporte')
    sql('\n'.join(f"UPDATE eventos_db.reporte SET fecha_generacion = "
                  f"{momento(al_azar_entre(AHORA - timedelta(days=30), AHORA - timedelta(hours=2)))} "
                  f"WHERE id_reporte = {fila[0]};" for fila in filas))
    print(f'  reportes  {len(filas)} generados por el Contador')


def main():
    if os.environ.get('COMPOSE_ARGS'):
        COMPOSE.extend(os.environ['COMPOSE_ARGS'].split())
    admin = token(os.environ['SARAO_ADMIN_CORREO'], os.environ['SARAO_ADMIN_CLAVE'])
    personal(admin)
    clientes_ = clientes()
    print(f'  clientes  {len(clientes_)}')
    organizador = token('rodrigo.vaca.88')
    eventos = crear_eventos(organizador)
    compras = ventas(eventos, clientes_)
    cambios_de_estado(eventos, compras, organizador, token('daniela.paz.85'))
    historia(eventos, compras)
    reportes(token('mariela.suarez.90'))
    print(f'  compras   {len(compras)}')


if __name__ == '__main__':
    sys.exit(main())
