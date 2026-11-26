#!/usr/bin/env bash
# Llena Sarao como si una empresa ya lo usara: personal, ~60 clientes, 12 eventos con afiche (realizados con sus
# accesos, uno el día de la demostración, próximos, uno postergado, uno cancelado con devoluciones y un borrador),
# cientos de compras repartidas en semanas, reportes del Contador y el contador de visitas.
# Crea antes una cuenta de prueba por rol (scripts/cuentas-de-prueba.sh).
#
#   bash scripts/datos-de-demostracion.sh [DIA]
#
# DIA (AAAA-MM-DD) es el día de la demostración, por defecto hoy: ese día queda un evento para validar en vivo.
# Es para una base nueva: si ya hay eventos de demostración, no carga nada.
# En el servidor (por ssh, en /opt/sarao), con los archivos de la nube:
#   COMPOSE_ARGS="-f docker-compose.yml -f docker-compose.nube.yml" SARAO_URL=https://DOMINIO bash scripts/datos-de-demostracion.sh 2026-12-02
#
# Mientras carga, pagos y correos usan el simulador (no cobra ni manda cientos de correos). Al terminar, pase lo que
# pase, vuelven al proveedor de pago con que estaban, y el script lo comprueba.
set -euo pipefail
cd "$(dirname "$0")/.."

ENVF=${SARAO_ENV:-.env}
[ -f "$ENVF" ] || { echo "Falta $ENVF"; exit 1; }
valor() { { grep "^$1=" "$ENVF" || true; } | tail -1 | cut -d= -f2- | tr -d "\r'\""; }
read -ra C <<<"docker compose ${COMPOSE_ARGS:-}"
DIA=${1:-$(date +%F)}
PUERTO=$(valor SARAO_PUERTO)
A=${SARAO_URL:-http://localhost:${PUERTO:-80}}/api

PY=""
for p in python3 python; do "$p" -c "" >/dev/null 2>&1 && { PY=$p; break; }; done
[ -n "$PY" ] || { echo "Falta python"; exit 1; }

curl -fsS -o /dev/null --max-time 5 "$A/catalogo" 2>/dev/null || { echo "La app no responde en $A. ¿Está encendida?"; exit 1; }

if [ -z "${CUENTAS_CLAVE:-}" ]; then
	read -rsp "Contraseña de las cuentas de prueba y de los clientes (8 o más, mayúscula, minúscula y número): " CUENTAS_CLAVE; echo
fi
export CUENTAS_CLAVE

# El proveedor con que está pagos ahora, para dejarlo igual al terminar
ANTES=$("${C[@]}" exec -T pagos printenv EVENTOS_PAGO_PROVEEDOR | tr -d '\r')
[ -n "$ANTES" ] || { echo "No se pudo leer el proveedor de pago de pagos"; exit 1; }
CORREO_ANTES=$("${C[@]}" exec -T notificaciones printenv EVENTOS_CORREO_PROVEEDOR | tr -d '\r')

restaurar() {
	echo "== Pagos vuelve a «$ANTES» y el correo a «$CORREO_ANTES»"
	EVENTOS_PAGO_PROVEEDOR=$ANTES EVENTOS_CORREO_PROVEEDOR=$CORREO_ANTES "${C[@]}" up -d --wait pagos notificaciones >/dev/null 2>&1
	AHORA=$("${C[@]}" exec -T pagos printenv EVENTOS_PAGO_PROVEEDOR | tr -d '\r')
	if [ "$AHORA" != "$ANTES" ]; then
		echo "  ATENCIÓN: pagos quedó con «$AHORA» y estaba con «$ANTES». Revísalo antes de vender."
		exit 1
	fi
	echo "  ok    pagos con «$AHORA»"
}
trap restaurar EXIT

echo "== Pagos y correo en simulador mientras se carga"
EVENTOS_PAGO_PROVEEDOR=simulador EVENTOS_CORREO_PROVEEDOR=simulador "${C[@]}" up -d --wait pagos notificaciones >/dev/null 2>&1

echo "== Cuentas de prueba"
bash scripts/cuentas-de-prueba.sh

echo "== Datos de demostración para el $DIA"
SARAO_API=$A SARAO_DIA=$DIA SARAO_ADMIN_CORREO=$(valor EVENTOS_ADMIN_CORREO) SARAO_ADMIN_CLAVE=$(valor EVENTOS_ADMIN_PASSWORD) \
	DOMINIO=$(valor EVENTOS_CORREO_DOMINIO) COMPOSE_ARGS=${COMPOSE_ARGS:-} PYTHONIOENCODING=utf-8 \
	"$PY" scripts/demostracion/cargar.py
