#!/bin/sh
# Corre solo en el primer arranque, con el volumen de datos vacío.
# Cada servicio tiene su base y un usuario sin permisos sobre las otras.
# La imagen de MySQL carga este archivo con `source` dentro de su propio script: el subshell evita
# que el `set -eu` le llegue (con MySQL 8.0.46 abortaba su arranque por una variable sin definir).
(
set -eu

export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"

crear() {
	clave=$(printf '%s' "$3" | sed "s/'/''/g")
	mysql --protocol=socket -uroot <<SQL
CREATE DATABASE \`$1\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER '$2'@'%' IDENTIFIED BY '$clave';
GRANT ALL PRIVILEGES ON \`$1\`.* TO '$2'@'%';
SQL
}

crear seguridad_db seguridad_app "$SEGURIDAD_DB_PASSWORD"
crear eventos_db eventos_app "$EVENTOS_DB_PASSWORD"
crear pagos_db pagos_app "$PAGOS_DB_PASSWORD"
)
