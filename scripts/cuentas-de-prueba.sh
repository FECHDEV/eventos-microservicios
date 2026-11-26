#!/usr/bin/env bash
# Crea, con la app encendida, una cuenta de prueba por rol, y su buzón si el servidor de correo está encendido:
#   Administrador         daniela.paz.85@DOMINIO
#   Organizador de eventos rodrigo.vaca.88@DOMINIO
#   Contador              mariela.suarez.90@DOMINIO
#   Control de acceso     jorge.mendez.93@DOMINIO
#   Cliente               lucia.rojas.98@DOMINIO
# DOMINIO es EVENTOS_CORREO_DOMINIO del .env (mail.com si está vacío). El Administrador inicial del .env sigue
# existiendo: es quien da de alta al personal. En Git Bash, desde la carpeta del proyecto:
#   bash scripts/cuentas-de-prueba.sh
# Pide la contraseña de las cuentas nuevas (la misma para el sitio y el buzón); no queda en ningún archivo. Se puede
# volver a correr: una cuenta o un buzón que ya existe se deja como está.
#   SARAO_ENV      otro archivo .env (por defecto .env)
#   SARAO_URL      otra dirección del sitio (por defecto http://localhost:SARAO_PUERTO)
#   CUENTAS_CLAVE  la contraseña, para no escribirla
#   COMPOSE_ARGS   argumentos para docker compose (en el servidor, los -f de la nube)
set -euo pipefail
cd "$(dirname "$0")/.."

ENVF=${SARAO_ENV:-.env}
[ -f "$ENVF" ] || { echo "Falta $ENVF"; exit 1; }
valor() { { grep "^$1=" "$ENVF" || true; } | tail -1 | cut -d= -f2- | tr -d "\r'\""; }
PUERTO=$(valor SARAO_PUERTO)
DOMINIO=$(valor EVENTOS_CORREO_DOMINIO)
DOMINIO=${DOMINIO:-mail.com}
A=${SARAO_URL:-http://localhost:${PUERTO:-80}}/api
read -ra C <<<"docker compose ${COMPOSE_ARGS:-} --profile correo"
J='Content-Type: application/json'

# El primero que corre de verdad: en Windows, python3 puede ser el acceso directo de la tienda
PY=""
for p in python3 python; do "$p" -c "" >/dev/null 2>&1 && { PY=$p; break; }; done
[ -n "$PY" ] || { echo "Falta python"; exit 1; }
json() { "$PY" -c 'import json,sys; a=sys.argv[1:]; print(json.dumps(dict(zip(a[::2], a[1::2]))))' "$@"; }
campo() { "$PY" -c "import json,sys; d=json.load(sys.stdin); print(eval(sys.argv[1]))" "$1" 2>/dev/null; }

curl -fsS -o /dev/null --max-time 5 "$A/catalogo" 2>/dev/null ||
	{ echo "La app no responde en $A. ¿Está encendida?"; exit 1; }

CLAVE=${CUENTAS_CLAVE:-}
while [ -z "$CLAVE" ]; do
	read -rsp "Contraseña de las cuentas de prueba (8 o más, mayúscula, minúscula y número): " UNA; echo
	read -rsp "Repítela: " OTRA; echo
	if [ "$UNA" != "$OTRA" ]; then echo "No coinciden."; continue; fi
	"$PY" -c 'import re,sys; sys.exit(0 if re.fullmatch(r"(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}", sys.argv[1]) else 1)' "$UNA" ||
		{ echo "No cumple la complejidad."; continue; }
	CLAVE=$UNA
done

TOKEN=$(curl -s -X POST "$A/auth/inicio-sesion" -H "$J" \
	-d "$(json correo "$(valor EVENTOS_ADMIN_CORREO)" contrasena "$(valor EVENTOS_ADMIN_PASSWORD)")" | campo 'd["token"]')
[ -n "$TOKEN" ] || { echo "  FALLA el Administrador del $ENVF no pudo iniciar sesión"; exit 1; }
HA="Authorization: Bearer $TOKEN"

informar() { # código correo
	case $1 in
		201) echo "  creada     $2" ;;
		409) echo "  ya existía $2 (se deja como está)" ;;
		*) echo "  FALLA $2: la app respondió $1"; exit 1 ;;
	esac
}

# Al personal lo da de alta el Administrador (decisiones.md → Roles del personal)
alta() { # usuario nombre apellido rol
	informar "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$A/usuarios" -H "$HA" -H "$J" \
		-d "$(json nombre "$2" apellido "$3" correo "$1@$DOMINIO" contrasena "$CLAVE" telefono 70012345 rol "$4")")" "$1@$DOMINIO"
}

echo "Cuentas:"
alta daniela.paz.85 Daniela Paz ADMINISTRADOR
alta rodrigo.vaca.88 Rodrigo Vaca ORGANIZADOR
alta mariela.suarez.90 Mariela Suárez CONTADOR
alta jorge.mendez.93 Jorge Méndez PERSONAL
informar "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$A/auth/registro" -H "$J" \
	-d "$(json nombre Lucía apellido Rojas correo "lucia.rojas.98@$DOMINIO" contrasena "$CLAVE" telefono 71234567 fechaNacimiento 1998-03-14)")" "lucia.rojas.98@$DOMINIO"

# Buzones: solo si el servidor de correo de este proyecto está encendido en esta máquina
if "${C[@]}" ps --status running --services 2>/dev/null | grep -qx correo; then
	echo "Buzones:"
	# Sin ningún buzón todavía, la lista termina con error
	existentes=$({ "${C[@]}" exec -T correo setup email list 2>/dev/null || true; } | tr -d '\r')
	for buzon in daniela.paz.85 rodrigo.vaca.88 mariela.suarez.90 jorge.mendez.93 lucia.rojas.98; do
		if grep -q "$buzon@$DOMINIO" <<<"$existentes"; then
			echo "  ya existía $buzon@$DOMINIO"
		else
			"${C[@]}" exec -T correo setup email add "$buzon@$DOMINIO" "$CLAVE" >/dev/null && echo "  creado     $buzon@$DOMINIO"
		fi
	done
else
	echo "El servidor de correo no está encendido: no se crearon buzones (docker compose --profile correo up -d)."
fi

echo
echo "Listo. Para entrar en ${A%/api} (y al webmail con el mismo correo y contraseña):"
echo "  Administrador           daniela.paz.85@$DOMINIO"
echo "  Organizador de eventos  rodrigo.vaca.88@$DOMINIO"
echo "  Contador                mariela.suarez.90@$DOMINIO"
echo "  Control de acceso       jorge.mendez.93@$DOMINIO"
echo "  Cliente                 lucia.rojas.98@$DOMINIO"
