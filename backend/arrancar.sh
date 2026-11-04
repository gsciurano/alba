#!/usr/bin/env bash
# ------------------------------------------------------------
#  Arranca el backend con el correo apuntando a Brevo.
#
#  POR QUE EXISTE: la configuracion del correo son seis variables de
#  entorno, y si una sola se olvida el sintoma es malisimo: la tienda
#  anda, las compras se registran, y los comprobantes no llegan a
#  nadie sin que nadie se entere. Con este script no hay nada que
#  recordar.
#
#  LA CLAVE NO SE GUARDA EN NINGUN LADO. Si no esta en el entorno, se
#  pide por teclado y no se escribe en pantalla ni en el historial.
#  Por eso el script puede estar en el repositorio sin problema.
#
#  Uso:
#    ./arrancar.sh
# ------------------------------------------------------------
set -euo pipefail
cd "$(dirname "$0")"

export MAIL_HOST="${MAIL_HOST:-smtp-relay.brevo.com}"
export MAIL_PORT="${MAIL_PORT:-587}"
export MAIL_USER="${MAIL_USER:-c1542c001@smtp-brevo.com}"
export MAIL_FROM="${MAIL_FROM:-theolambert.ar@gmail.com}"
export MAIL_AUTH=true
export MAIL_TLS=true

if [ -z "${MAIL_PASSWORD:-}" ]; then
    echo "Clave SMTP de Brevo (Settings -> SMTP & API). No se muestra al escribir."
    read -r -s -p "MAIL_PASSWORD: " MAIL_PASSWORD
    echo
    export MAIL_PASSWORD
fi

if [ -z "$MAIL_PASSWORD" ]; then
    echo "Sin clave no se puede mandar ningun comprobante. Cancelado." >&2
    exit 1
fi

# DB_PASSWORD ya tiene valor por defecto en application.properties
# ("root"), asi que solo se pisa si quien arranca la definio.
exec ./mvnw spring-boot:run
