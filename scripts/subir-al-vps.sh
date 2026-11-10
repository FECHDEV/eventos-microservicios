#!/usr/bin/env bash
# Despliega Sarao en un VPS Ubuntu nuevo, desde esta máquina:
#   bash scripts/subir-al-vps.sh IP [rama]
# Entra como root por SSH (con la llave que tenga el VPS), clona el repositorio público en
# /opt/sarao, copia .env.servidor y corre scripts/instalar-servidor.sh. La IP no se guarda en
# ningún archivo. Al final pide las contraseñas de los buzones.
set -euo pipefail
cd "$(dirname "$0")/.."

IP=${1:?Uso: bash scripts/subir-al-vps.sh IP [rama]}
RAMA=${2:-main}
REPO=https://github.com/FECHDEV/eventos-microservicios.git
[ -f .env.servidor ] || { echo "Falta .env.servidor (la plantilla está en el repositorio local)"; exit 1; }

# Un VPS nuevo trae otra llave de host con la misma IP: se acepta la primera vez
SSH=(ssh -o StrictHostKeyChecking=accept-new "root@$IP")

"${SSH[@]}" "command -v git >/dev/null || (apt-get update -qq && apt-get install -y -qq git); \
	[ -d /opt/sarao ] || git clone -q --branch $RAMA $REPO /opt/sarao; \
	cd /opt/sarao && git fetch -q && git checkout -q $RAMA && git pull -q"
scp -q -o StrictHostKeyChecking=accept-new .env.servidor "root@$IP:/opt/sarao/.env.servidor"
"${SSH[@]}" "chmod 600 /opt/sarao/.env.servidor"
# -t: los buzones piden su contraseña en la terminal
ssh -t -o StrictHostKeyChecking=accept-new "root@$IP" "bash /opt/sarao/scripts/instalar-servidor.sh"
