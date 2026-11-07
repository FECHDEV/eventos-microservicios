#!/usr/bin/env bash
# Prueba de punta a punta de Sarao en Docker, con datos temporales, y lo borra todo al terminar.
#
#   bash scripts/prueba-completa.sh
#
# Levanta su propio proyecto de compose (sarao-completa, en el puerto 18090), con
# claves generadas al azar, el simulador de pago y el servidor de correo real, y
# recorre el sistema como lo usan las personas: registro, recuperación de la
# contraseña, compra y pago, correos, entradas, validación en la puerta,
# cancelación y devolución, y reportes. No toca el .env ni los datos de otro
# proyecto. Necesita bash, curl, openssl y python.
#
# Termina con 0 si todo pasó, o con la cantidad de fallas (hasta 125).

set -u
cd "$(dirname "$0")/.."

P=sarao-completa
PUERTO=18090
TEMPORAL="$(mktemp -d)"
ENVF="$TEMPORAL/prueba.env"
ADMIN_CLAVE="$(openssl rand -hex 8)Aa1"
CLAVE="$(openssl rand -hex 8)Aa1"
# El primero que corre de verdad: en Windows, python3 puede ser el acceso directo de la tienda
PY=""
for p in python3 python; do "$p" -c "" >/dev/null 2>&1 && { PY=$p; break; }; done
[ -n "$PY" ] || { echo "Falta python"; exit 1; }
{
	echo "MYSQL_ROOT_PASSWORD=$(openssl rand -hex 16)"
	echo "EVENTOS_SEGURIDAD_DB_PASSWORD=$(openssl rand -hex 16)"
	echo "EVENTOS_EVENTOS_DB_PASSWORD=$(openssl rand -hex 16)"
	echo "EVENTOS_PAGOS_DB_PASSWORD=$(openssl rand -hex 16)"
	echo "EVENTOS_JWT_SECRETO=$(openssl rand -base64 48 | tr -d '\n')"
	echo "EVENTOS_CLAVE_SERVICIO=$(openssl rand -hex 24)"
	echo "EVENTOS_ADMIN_CORREO=admin@mail.com"
	echo "EVENTOS_ADMIN_PASSWORD=$ADMIN_CLAVE"
	echo "SARAO_PUERTO=$PUERTO"
	echo "SARAO_WEBMAIL_PUERTO=18091"
	echo "EVENTOS_PAGO_PROVEEDOR=simulador"
	echo "EVENTOS_CORREO_PROVEEDOR=smtp"
	echo "EVENTOS_SITIO_URL=http://localhost:$PUERTO"
} > "$ENVF"
C="docker compose -p $P --env-file $ENVF --profile correo -f docker-compose.yml"
A="http://localhost:$PUERTO/api"
J='Content-Type: application/json'
FALLAS=0

limpiar() {
	$C down -v --rmi local >/dev/null 2>&1
	rm -rf "$TEMPORAL"
}
trap limpiar EXIT

# Compara lo obtenido con lo esperado y lo anota
espera() {
	if [ "$2" = "$3" ]; then
		echo "  ok    $1"
	else
		echo "  FALLA $1: se esperaba «$3» y llegó «$2»"
		FALLAS=$((FALLAS + 1))
	fi
}
js() { "$PY" -c "import json,sys; d=json.load(sys.stdin); print(eval(sys.argv[1]))" "$1" 2>/dev/null; }
cod() { curl -s -o /dev/null -w "%{http_code}" --max-time 30 "$@"; }
base() { $C exec -T base sh -c "MYSQL_PWD=\$MYSQL_ROOT_PASSWORD mysql -uroot -N -e \"$1\"" 2>/dev/null | tr -d '\r'; }
buzon() { $C exec -T correo sh -c "ls /var/mail/mail.com/$1/new 2>/dev/null | wc -l" | tr -d '\r '; }
esperarCorreos() {
	for _ in $(seq 1 30); do [ "$(buzon "$1")" -ge "$2" ] 2>/dev/null && return; sleep 2; done
}
token() { curl -s -X POST $A/auth/inicio-sesion -H "$J" -d "{\"correo\":\"$1\",\"contrasena\":\"$2\"}" | js 'd["token"]'; }

echo "== Levantar (espera a que cada servicio esté sano)"
if ! $C up -d --build --wait --wait-timeout 600 >/dev/null 2>&1; then
	echo "  FALLA no arrancó"
	$C ps
	$C logs --tail 40
	exit 1
fi
echo "  ok    todos los servicios sanos"
for cuenta in ana pedro gerencia no-responder; do
	$C exec -T correo setup email add "$cuenta@mail.com" "$(openssl rand -hex 12)" >/dev/null 2>&1
done
# El servidor de correo carga las cuentas nuevas unos segundos después; antes, un correo a ellas rebota
for _ in $(seq 1 60); do
	$C exec -T correo sh -c "grep -q no-responder@mail.com /etc/postfix/vmailbox && grep -q no-responder@mail.com /etc/dovecot/userdb" 2>/dev/null && break
	sleep 2
done
sleep 5

echo "== Cuentas"
for persona in "ana Ana" "pedro Pedro"; do
	set -- $persona
	espera "registro de $2" "$(cod -X POST $A/auth/registro -H "$J" -d "{\"nombre\":\"$2\",\"apellido\":\"Rojas\",\"correo\":\"$1@mail.com\",\"contrasena\":\"$CLAVE\",\"fechaNacimiento\":\"2000-05-20\"}")" 201
done
# El puerto de Seguridad abre antes de que la carga inicial cree al Administrador
for _ in $(seq 1 30); do TA=$(token admin@mail.com "$ADMIN_CLAVE"); [ -n "$TA" ] && break; sleep 2; done
espera "el Administrador inicial entra" "$([ -n "$TA" ] && echo si)" si
HA="Authorization: Bearer $TA"
espera "recuperación pedida" "$(cod -X POST $A/auth/recuperacion -H "$J" -d '{"correo":"ana@mail.com"}')" 202
esperarCorreos ana 1
espera "correo de recuperación en el buzón" "$(buzon ana)" 1
ID_PEDRO=$(base "select id_usuario from seguridad_db.usuario where correo='pedro@mail.com'")
espera "Pedro pasa a Personal" "$(cod -X PUT $A/usuarios/$ID_PEDRO/rol -H "$HA" -H "$J" -d '{"rol":"PERSONAL"}')" 200
HANA="Authorization: Bearer $(token ana@mail.com "$CLAVE")"
HPEDRO="Authorization: Bearer $(token pedro@mail.com "$CLAVE")"

echo "== Evento y compra"
FECHA=$(date -d "+5 days" +%F)
EV=$(curl -s -X POST $A/eventos -H "$HA" -H "$J" -d "{\"nombre\":\"Noche de jazz\",\"fecha\":\"$FECHA\",\"horaInicio\":\"20:00\",\"horaFin\":\"23:00\",\"lugar\":\"Teatro\"}" | js 'd["idEvento"]')
TIPO=$(curl -s -X POST $A/eventos/$EV/tipos-entrada -H "$HA" -H "$J" -d '{"nombre":"General","precio":50.00,"cantidadTotal":10}' | js 'd["idTipoEntrada"]')
espera "activar el evento" "$(cod -X POST $A/eventos/$EV/activacion -H "$HA")" 200
espera "aparece en el catálogo" "$(curl -s $A/catalogo | js 'd["contenido"][0]["nombre"]')" "Noche de jazz"
RESERVA=$(curl -s -X POST $A/reservas -H "$HANA" -H "$J" -d "{\"idTipoEntrada\":$TIPO,\"cantidad\":2}" | js 'd["idReserva"]')
espera "cobro pendiente" "$(curl -s -X POST $A/reservas/$RESERVA/pago -H "$HANA" | js 'd["estado"]')" PENDIENTE
cod -X POST $A/reservas/$RESERVA/pago/simulacion -H "$HANA" -H "$J" -d '{"resultado":"PAGADO"}' >/dev/null
espera "compra confirmada" "$(curl -s $A/reservas/$RESERVA/pago -H "$HANA" | js 'd["estadoReserva"]')" CONFIRMADA
espera "dos entradas emitidas" "$(base "select count(*) from eventos_db.entrada where id_reserva=$RESERVA")" 2
esperarCorreos ana 3
espera "detalle de compra y entradas en el buzón" "$(buzon ana)" 3
espera "Mis entradas" "$(curl -s $A/entradas -H "$HANA" | js 'len(d["proximas"])')" 1

echo "== Puerta"
base "update eventos_db.evento set fecha=curdate(), hora_inicio='23:59:00', hora_fin='23:58:00' where id_evento=$EV"
CODIGO=$(base "select codigo_qr from eventos_db.entrada where id_reserva=$RESERVA order by id_entrada limit 1")
validar() { curl -s -X POST $A/accesos -H "$HPEDRO" -H "$J" -d "{\"idEvento\":$EV,\"codigo\":\"$1\"}" | js 'd["resultado"]'; }
espera "entrada válida" "$(validar "$CODIGO")" VALIDA
espera "la segunda vez, ya utilizada" "$(validar "$CODIGO")" YA_UTILIZADA
espera "un código inventado no corresponde" "$(validar no-existe)" NO_CORRESPONDE

echo "== Reportes"
gen() { curl -s -X POST $A/reportes -H "$HA" -H "$J" -d "{\"tipo\":\"$1\"}"; }
espera "ventas" "$(gen VENTAS | js 'd["resumen"]["totales"][4]')" 100.0
espera "accesos" "$(gen ACCESOS | js 'd["detalle"]["filas"][0][4]')" "Pedro Rojas"
ID_REPORTE=$(base "select max(id_reporte) from eventos_db.reporte")
espera "PDF" "$(curl -s $A/reportes/$ID_REPORTE/pdf -H "$HA" | head -c 5)" "%PDF-"
espera "envío por correo" "$(cod -X POST $A/reportes/$ID_REPORTE/correo -H "$HA" -H "$J" -d '{"destinatarios":["gerencia@mail.com"],"formato":"AMBOS"}')" 202
esperarCorreos gerencia 1
espera "reporte en el buzón" "$(buzon gerencia)" 1

echo "== Cancelación y devolución"
espera "cancelar el evento" "$(cod -X POST $A/eventos/$EV/cancelacion -H "$HA")" 200
espera "una entrada pendiente de devolución" "$(curl -s $A/devoluciones -H "$HA" | js 'd[0]["pendientes"]')" 1
espera "devolver la compra" "$(curl -s -X POST $A/devoluciones/compras/$RESERVA -H "$HA" | js 'd["registradas"]')" 1
espera "ingresos con lo devuelto" "$(gen INGRESOS | js 'd["resumen"]["totales"][2]')" 50.0

echo "== Accesos que no corresponden"
espera "un Cliente no ve reportes" "$(cod $A/reportes/opciones -H "$HANA")" 403
espera "sin sesión no se compra" "$(cod -X POST $A/reservas -H "$J" -d '{}')" 401
espera "/interno no sale por el gateway" "$(cod -X POST $A/interno/reservas/1/confirmacion -H "$J" -d '{"idPago":1}')" 404

echo "== Logs"
for servicio in seguridad eventos-venta pagos notificaciones gateway; do
	espera "sin errores en $servicio" "$($C logs $servicio 2>&1 | grep -c ' ERROR ')" 0
done

echo
[ $FALLAS -eq 0 ] && echo "Todo pasó." || echo "$FALLAS fallas."
# Acotado: el código de salida da la vuelta en 256
exit $((FALLAS > 125 ? 125 : FALLAS))
