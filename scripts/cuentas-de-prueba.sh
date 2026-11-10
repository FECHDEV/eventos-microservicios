#!/usr/bin/env bash
# Crea, con la app encendida, las cuentas para probarla a mano: un Cliente y un Personal autorizado.
# El Administrador ya existe: es el del .env (EVENTOS_ADMIN_CORREO). Con Seguridad, el gateway y el
# frontend alcanza (no hacen falta Pagos ni Notificaciones). En Git Bash, desde la carpeta del proyecto:
#   bash scripts/cuentas-de-prueba.sh
# Pide la contraseña de las dos cuentas nuevas; no queda en ningún archivo. Se puede volver a correr:
# una cuenta que ya existe se deja como está.
#   SARAO_ENV   otro archivo .env (por defecto .env)
#   SARAO_URL   otra dirección del sitio (por defecto http://localhost:SARAO_PUERTO)
set -euo pipefail
cd "$(dirname "$0")/.."

ENVF=${SARAO_ENV:-.env}
[ -f "$ENVF" ] || { echo "Falta $ENVF"; exit 1; }
valor() { grep "^$1=" "$ENVF" | tail -1 | cut -d= -f2- | tr -d "\r'\""; }
PUERTO=$(valor SARAO_PUERTO)
A=${SARAO_URL:-http://localhost:${PUERTO:-80}}/api
J='Content-Type: application/json'

# El primero que corre de verdad: en Windows, python3 puede ser el acceso directo de la tienda
PY=""
for p in python3 python; do "$p" -c "" >/dev/null 2>&1 && { PY=$p; break; }; done
[ -n "$PY" ] || { echo "Falta python"; exit 1; }
json() { "$PY" -c 'import json,sys; a=sys.argv[1:]; print(json.dumps(dict(zip(a[::2], a[1::2]))))' "$@"; }
campo() { "$PY" -c "import json,sys; d=json.load(sys.stdin); print(eval(sys.argv[1]))" "$1" 2>/dev/null; }

curl -fsS -o /dev/null --max-time 5 "$A/catalogo" 2>/dev/null ||
	{ echo "La app no responde en $A. ¿Está encendida?"; exit 1; }

# Contraseña de las cuentas nuevas: CUENTAS_CLAVE si viene definida, si no se pide dos veces
CLAVE=${CUENTAS_CLAVE:-}
while [ -z "$CLAVE" ]; do
	read -rsp "Contraseña para el Cliente y el Personal (8 o más, mayúscula, minúscula y número): " UNA; echo
	read -rsp "Repítela: " OTRA; echo
	if [ "$UNA" != "$OTRA" ]; then echo "No coinciden."; continue; fi
	"$PY" -c 'import re,sys; sys.exit(0 if re.fullmatch(r"(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}", sys.argv[1]) else 1)' "$UNA" ||
		{ echo "No cumple la complejidad."; continue; }
	CLAVE=$UNA
done

registrar() { # correo nombre apellido
	local codigo
	codigo=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$A/auth/registro" -H "$J" \
		-d "$(json nombre "$2" apellido "$3" correo "$1" contrasena "$CLAVE" fechaNacimiento 1995-05-20)")
	case $codigo in
		201) echo "  creada     $1" ;;
		409) echo "  ya existía $1 (se deja como está)" ;;
		*) echo "  FALLA $1: la app respondió $codigo"; exit 1 ;;
	esac
}

echo "Cuentas:"
registrar cliente@mail.com Carla Cliente
registrar personal@mail.com Pablo Personal

TOKEN=$(curl -s -X POST "$A/auth/inicio-sesion" -H "$J" \
	-d "$(json correo "$(valor EVENTOS_ADMIN_CORREO)" contrasena "$(valor EVENTOS_ADMIN_PASSWORD)")" | campo 'd["token"]')
[ -n "$TOKEN" ] || { echo "  FALLA el Administrador del $ENVF no pudo iniciar sesión"; exit 1; }
HA="Authorization: Bearer $TOKEN"
ID=$(curl -s "$A/usuarios?texto=personal@mail.com" -H "$HA" |
	campo '[u["idUsuario"] for u in d["contenido"] if u["correo"] == "personal@mail.com"][0]')
codigo=$(curl -s -o /dev/null -w '%{http_code}' -X PUT "$A/usuarios/$ID/rol" -H "$HA" -H "$J" -d '{"rol":"PERSONAL"}')
case $codigo in
	200 | 409) echo "  rol        personal@mail.com es Personal autorizado" ;;
	*) echo "  FALLA el cambio de rol respondió $codigo"; exit 1 ;;
esac

echo
echo "Listo. Para entrar en ${A%/api}:"
echo "  Administrador        $(valor EVENTOS_ADMIN_CORREO)  (la contraseña del .env)"
echo "  Cliente              cliente@mail.com   (la contraseña que escribiste)"
echo "  Personal autorizado  personal@mail.com  (la contraseña que escribiste)"
