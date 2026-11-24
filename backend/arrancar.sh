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
#  LA CLAVE NO SE GUARDA EN EL REPOSITORIO. Queda en el llavero de
#  macOS, que es el lugar del sistema para esto: cifrado, atado a tu
#  usuario y fuera de la carpeta del proyecto. La primera vez el
#  script la pide y la guarda; despues ya no vuelve a preguntar.
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

# Nombre con el que la clave queda archivada en el llavero.
LLAVERO_SERVICIO="brevo-alba-smtp"

if [ -z "${MAIL_PASSWORD:-}" ]; then
    # 2>/dev/null porque cuando la clave todavia no esta guardada,
    # 'security' escribe un error que no aporta nada: lo que importa
    # es que devuelve vacio, y de eso se encarga el if de abajo.
    MAIL_PASSWORD="$(security find-generic-password \
                        -s "$LLAVERO_SERVICIO" -a "$MAIL_USER" -w 2>/dev/null || true)"
fi

if [ -z "$MAIL_PASSWORD" ]; then
    echo "No hay clave guardada para $MAIL_USER."
    echo "Clave SMTP de Brevo (Settings -> SMTP & API). No se muestra al escribir."
    read -r -s -p "MAIL_PASSWORD: " MAIL_PASSWORD
    echo
    if [ -z "$MAIL_PASSWORD" ]; then
        echo "Sin clave no se puede mandar ningun comprobante. Cancelado." >&2
        exit 1
    fi
    # -U actualiza la entrada si ya existia con otra clave, en lugar
    # de fallar; asi este mismo camino sirve para corregir una clave
    # vieja despues de regenerarla en Brevo.
    if security add-generic-password -U \
            -s "$LLAVERO_SERVICIO" -a "$MAIL_USER" -w "$MAIL_PASSWORD" 2>/dev/null; then
        echo "Clave guardada en el llavero: la proxima vez no te la pide."
    else
        echo "No se pudo guardar en el llavero; esta vez va a andar igual." >&2
    fi
fi
export MAIL_PASSWORD

# ------------------------------------------------------------
#  CLAVE DE LA API HTTP (opcional, para redes que bloquean SMTP)
#
#  Muchas redes bloquean los puertos de SMTP. Con esta clave puesta,
#  los correos salen por el puerto 443 (el de cualquier pagina web),
#  que ninguna red bloquea. Sin ella, se usa SMTP como siempre.
#
#  NO ES LA MISMA QUE MAIL_PASSWORD: sale de Brevo -> Settings ->
#  SMTP & API -> pestania "API keys", y empieza con "xkeysib-".
#
#  Si no esta guardada no se pregunta nada: el envio por SMTP sigue
#  siendo un camino valido, asi que no tiene sentido frenar el
#  arranque por una clave que quizas no haga falta.
# ------------------------------------------------------------
if [ -z "${BREVO_API_KEY:-}" ]; then
    BREVO_API_KEY="$(security find-generic-password \
                        -s "brevo-alba-api" -a "$MAIL_USER" -w 2>/dev/null || true)"
fi
export BREVO_API_KEY

if [ -n "$BREVO_API_KEY" ]; then
    echo "Correo por la API de Brevo (puerto 443): anda tambien en redes que bloquean SMTP."
else
    echo "Correo por SMTP. Si la red bloquea el puerto 587 los comprobantes no van a salir;"
    echo "para que salgan igual, guardar la clave de API una vez con:"
    echo "  security add-generic-password -U -s brevo-alba-api -a \"$MAIL_USER\" -w"
fi

# ------------------------------------------------------------
#  MERCADO PAGO (opcional)
#
#  Es el unico medio de pago que cobra de verdad. Si el token no esta
#  guardado no se pregunta nada: la tienda arranca igual y no ofrece ese
#  medio. Es lo correcto, porque hay dos medios mas que no dependen de
#  ningun servicio de afuera.
#
#  MIENTRAS ESTO SEA UN TRABAJO DE FACULTAD va el token de PRUEBA, que
#  empieza con "TEST-": el circuito es el real y la plata no se mueve.
#  Se saca de mercadopago.com.ar/developers -> Tus integraciones ->
#  (la aplicacion) -> Credenciales.
#
#  Para guardarlo una vez:
#    security add-generic-password -U -s mercadopago-alba -a access-token -w
# ------------------------------------------------------------
if [ -z "${MP_ACCESS_TOKEN:-}" ]; then
    MP_ACCESS_TOKEN="$(security find-generic-password \
                        -s "mercadopago-alba" -a "access-token" -w 2>/dev/null || true)"
fi
export MP_ACCESS_TOKEN

# La direccion publica para volver de pagar. Vacia mientras el sitio
# corra en localhost: Mercado Pago no acepta esa direccion, y sin ella
# el pago se verifica con el boton "Ya pagué" de la confirmacion.
export MP_URL_PUBLICA="${MP_URL_PUBLICA:-}"

# PRUEBA O PRODUCCION. No se puede deducir del token: desde que Mercado
# Pago unifico los prefijos, las credenciales de prueba y las productivas
# empiezan las dos con "APP_USR-" y lo unico que las separa es de que
# solapa del panel se copiaron. Asi que se declara, y por defecto prueba.
if [ "${MP_MODO:-prueba}" = "produccion" ]; then
    export MP_PRODUCCION=true
else
    export MP_PRODUCCION=false
fi

if [ -n "$MP_ACCESS_TOKEN" ]; then
    if [ "$MP_PRODUCCION" = "true" ]; then
        echo "ATENCION: Mercado Pago en modo PRODUCCION. Las compras COBRAN PLATA DE VERDAD."
    else
        echo "Mercado Pago en modo PRUEBA: no se cobra plata de verdad."
        echo "  (el token tiene que ser el de la solapa 'Prueba' del panel de Mercado Pago)"
    fi
    if [ -z "$MP_URL_PUBLICA" ]; then
        echo "  Sin MP_URL_PUBLICA: despues de pagar hay que apretar 'Ya pagué' en la confirmacion."
    fi
else
    echo "Mercado Pago apagado (no hay token guardado). La tienda ofrece tarjeta y transferencia."
fi

# ------------------------------------------------------------
#  EL LOG VA TAMBIEN A UN ARCHIVO.
#
#  POR QUE: el envio del comprobante es @Async y se traga sus errores
#  para no tirar abajo una compra. El motivo real ("authentication
#  failed", "connect timed out", "quota exceeded") aparece una sola
#  vez, en el log. Si el log vive nada mas que en la terminal, se
#  pierde al cerrarla y la pregunta "por que no llego el mail?" se
#  queda sin respuesta. Con el archivo, la respuesta se puede buscar
#  despues:
#
#    grep -i comprobante backend/arranque.log
# ------------------------------------------------------------
LOG="arranque.log"
echo "Log de esta corrida: $(pwd)/$LOG"

# 'tee -a' agrega al final en vez de pisar, para no perder la corrida
# anterior. 'pipefail' (arriba) hace que si mvnw falla, el script
# tambien falle, aunque tee haya terminado bien.
./mvnw spring-boot:run 2>&1 | tee -a "$LOG"
