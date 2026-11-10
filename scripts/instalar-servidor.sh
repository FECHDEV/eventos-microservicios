#!/usr/bin/env bash
# Deja Sarao funcionando en un servidor Ubuntu nuevo, con HTTPS. Se corre como root desde la
# carpeta del proyecto, con .env.servidor al lado (lo copia scripts/subir-al-vps.sh):
#   bash scripts/instalar-servidor.sh             todo
#   bash scripts/instalar-servidor.sh env         solo arma el .env (no toca nada más)
# El .env sale de .env.example, de .env.servidor (los datos del usuario), de claves nuevas al
# azar y del dominio: la IP pública en sslip.io, salvo que SARAO_DOMINIO venga definida.
# Se puede volver a correr: si el .env ya existe, lo deja como está.
set -euo pipefail
cd "$(dirname "$0")/.."

paso() { printf '\n\033[1;33m== %s\033[0m\n' "$1"; }
# Entre comillas simples: compose no interpreta un $ de una contraseña. Una comilla simple no se admite
poner() { grep -v "^$1=" .env > .env.tmp || true; printf "%s='%s'\n" "$1" "$2" >> .env.tmp; mv .env.tmp .env; }
# Sin el \r de un archivo editado en Windows ni las comillas
valor() { grep "^$1=" "${2:-.env}" | tail -1 | cut -d= -f2- | tr -d "\r'\""; }
COMPOSE=(docker compose -f docker-compose.yml -f docker-compose.nube.yml --profile correo)

armar_env() {
	paso "Configuración (.env)"
	if [ -f .env ]; then
		echo ".env ya existe: se deja como está."
		return
	fi
	[ -f .env.servidor ] || { echo "Falta .env.servidor"; exit 1; }
	for dato in EVENTOS_ADMIN_CORREO EVENTOS_ADMIN_PASSWORD EVENTOS_PAGO_LIBELULA_APPKEY EVENTOS_PAGO_LIBELULA_EMAIL; do
		[ -n "$(valor $dato .env.servidor)" ] || { echo "Falta $dato en .env.servidor"; exit 1; }
	done
	grep -q "^[A-Z_]*=.*'" .env.servidor && { echo "Un valor de .env.servidor tiene comillas simples"; exit 1; }
	IP=${SARAO_IP:-$(curl -fsS https://api.ipify.org)}
	cp .env.example .env
	chmod 600 .env
	for dato in EVENTOS_ADMIN_CORREO EVENTOS_ADMIN_PASSWORD EVENTOS_PAGO_LIBELULA_APPKEY EVENTOS_PAGO_LIBELULA_EMAIL \
		EVENTOS_CORREO_DOMINIO; do
		poner $dato "$(valor $dato .env.servidor)"
	done
	poner MYSQL_ROOT_PASSWORD "$(openssl rand -hex 24)"
	poner EVENTOS_SEGURIDAD_DB_PASSWORD "$(openssl rand -hex 24)"
	poner EVENTOS_EVENTOS_DB_PASSWORD "$(openssl rand -hex 24)"
	poner EVENTOS_PAGOS_DB_PASSWORD "$(openssl rand -hex 24)"
	poner EVENTOS_JWT_SECRETO "$(openssl rand -base64 48 | tr -d '\n')"
	poner EVENTOS_CLAVE_SERVICIO "$(openssl rand -hex 24)"
	poner EVENTOS_PAGO_PROVEEDOR libelula
	poner EVENTOS_CORREO_PROVEEDOR smtp
	poner SARAO_DOMINIO "${SARAO_DOMINIO:-${IP//./-}.sslip.io}"
	echo "Listo: .env con claves nuevas, Libélula, SMTP y el dominio $(valor SARAO_DOMINIO)."
}

preparar() {
	paso "Memoria de intercambio (swap) de 2 GB"
	if ! swapon --show | grep -q .; then
		fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile >/dev/null && swapon /swapfile
		grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
	fi
	swapon --show

	paso "Docker"
	command -v docker >/dev/null || curl -fsSL https://get.docker.com | sh
	docker --version

	paso "Firewall: solo SSH, HTTP y HTTPS"
	ufw allow OpenSSH >/dev/null && ufw allow 80/tcp >/dev/null && ufw allow 443/tcp >/dev/null
	ufw --force enable >/dev/null && ufw status | head -8
}

levantar() {
	paso "Imágenes y contenedores (la primera vez tarda: compila cuatro servicios y el frontend)"
	"${COMPOSE[@]}" up -d --build --wait --wait-timeout 1200

	paso "Esperando al Administrador inicial y al certificado HTTPS"
	# El puerto de Seguridad abre antes de que la carga inicial cree al Administrador. Se mira la
	# base y no se prueba iniciar sesión: los intentos fallidos suman al límite por IP
	for _ in $(seq 1 60); do
		ADMINS=$("${COMPOSE[@]}" exec -T base sh -c \
			"MYSQL_PWD=\$MYSQL_ROOT_PASSWORD mysql -uroot -N -e \"select count(*) from seguridad_db.usuario where rol='ADMINISTRADOR'\"" \
			2>/dev/null | tr -d '\r' || true)
		if [ "${ADMINS:-0}" -gt 0 ] 2>/dev/null && curl -fsS -o /dev/null "https://$(valor SARAO_DOMINIO)/api/catalogo"; then
			echo "Administrador creado y sitio por HTTPS."
			return
		fi
		sleep 5
	done
	echo "No apareció en 5 minutos. Revisa: ${COMPOSE[*]} logs seguridad caddy"
	exit 1
}

buzones() {
	paso "Buzones de correo (cada uno pide su contraseña)"
	DOMINIO=$(valor EVENTOS_CORREO_DOMINIO)
	DOMINIO=${DOMINIO:-mail.com}
	EXISTENTES=$("${COMPOSE[@]}" exec -T correo setup email list 2>/dev/null || true)
	for buzon in no-responder $(valor BUZONES .env.servidor); do
		grep -q "$buzon@$DOMINIO" <<<"$EXISTENTES" && continue
		echo "Buzón $buzon@$DOMINIO:"
		"${COMPOSE[@]}" exec correo setup email add "$buzon@$DOMINIO"
	done
	"${COMPOSE[@]}" exec -T correo setup alias add "postmaster@$DOMINIO" "no-responder@$DOMINIO" 2>/dev/null || true
}

case "${1:-todo}" in
	env) armar_env ;;
	todo)
		armar_env
		preparar
		levantar
		buzones
		paso "Listo"
		echo "Sitio:   https://$(valor SARAO_DOMINIO)"
		echo "Webmail: https://correo.$(valor SARAO_DOMINIO)"
		;;
	*) echo "Uso: $0 [env]"; exit 1 ;;
esac
