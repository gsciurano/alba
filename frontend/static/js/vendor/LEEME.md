# Código de terceros

Acá va código que **no escribimos nosotros**. Está separado del resto a
propósito: si algo de esta carpeta falla, no se arregla editándolo, se
cambia de versión o se reemplaza la librería.

## Por qué existe esta carpeta

El proyecto es HTML + CSS + JavaScript sin npm. Eso no significa "sin
dependencias": significa **sin gestor de paquetes y sin compilación**. Una
librería que necesitamos se baja una vez, se guarda acá versionada a mano,
y se carga con un `<script>` como cualquier archivo nuestro.

Conviene decirlo así y no fingir que el proyecto no usa nada de afuera.

## qrcode.js

| | |
|---|---|
| **Qué es** | Generador de códigos QR |
| **Autor** | Kazuhiko Arase |
| **Versión** | 1.4.4 |
| **Licencia** | MIT (la cabecera del archivo la incluye) |
| **Origen** | `https://cdnjs.cloudflare.com/ajax/libs/qrcode-generator/1.4.4/qrcode.js` |
| **SHA-256** | `18ae399f81182bc9de916e9c77b195df20cc58d6f2d55a62b085a299f1bf1780` |
| **Bajado el** | 2026-11-03 |

El archivo está **sin minificar y sin modificar**, tal cual se bajó. Se
eligió la versión legible en vez de la comprimida por dos razones: la
cabecera con la licencia queda a la vista, y cualquiera puede leer qué
hace en lugar de confiar en una sola línea de 20 KB.

Para comprobar que nadie lo tocó:

    shasum -a 256 frontend/js/vendor/qrcode.js

### Por qué una librería y no código propio

Un QR no es un dibujo: lleva corrección de errores Reed-Solomon sobre
campos finitos. Son unas 400 líneas de matemática donde un error no se ve
—el QR se dibuja igual— pero el teléfono no lo lee. No es el tipo de
código que conviene escribir a mano para mostrar un alias.

### Qué se revisó antes de incorporarlo

Se buscaron las APIs con las que un archivo de terceros podría hacer daño:
`eval`, `new Function`, `fetch`, `XMLHttpRequest`, `import()`,
`document.write`, `localStorage`, `sessionStorage`, `WebSocket`,
`innerHTML` y asignaciones a `.src`. **No aparece ninguna.** La librería
recibe texto y devuelve una matriz de puntos: no toca la red, ni el
almacenamiento, ni el documento.

### Cómo se carga

No es un módulo ES, define una variable global `qrcode`. Por eso va con un
`<script>` normal **antes** del módulo que lo usa, y no con `import`:

```html
<script src="js/vendor/qrcode.js"></script>
<script type="module" src="js/tienda.js"></script>
```

Se dejó el archivo sin tocar a propósito. Agregarle un `export` al final
lo volvería importable, pero dejaría de ser idéntico al original y el
SHA-256 de arriba ya no serviría para verificarlo.

---

## htmx.min.js

| | |
|---|---|
| **Qué es** | Librería de hipermedia: permite que cualquier etiqueta HTML haga peticiones HTTP y reemplace un pedazo del DOM con la respuesta, usando atributos `hx-*` |
| **Autor** | Big Sky Software (Carson Gross) |
| **Versión** | 1.9.10 |
| **Licencia** | BSD de 2 cláusulas (Zero-Clause BSD en el repositorio) |
| **Origen** | `https://unpkg.com/htmx.org@1.9.10/dist/htmx.min.js` |
| **SHA-256** | `b3bdcf5c741897a53648b1207fff0469a0d61901429ba1f6e88f98ebd84e669e` |
| **Tamaño** | 47.755 bytes sin comprimir (~14 kB con gzip, que es el número que se suele citar) |
| **Bajado el** | 2026-11-11 |

Para comprobar que nadie lo tocó:

    shasum -a 256 backend/src/main/resources/static/js/vendor/htmx.min.js

### Por qué local y no por CDN

La presentación de la cátedra ofrece las dos opciones (CDN con `unpkg` o
archivo en `static/`), pero la diapositiva de organización de carpetas dice
que la librería va en `static/`, y ese es además el criterio que este
proyecto ya seguía con `qrcode.js`: una dependencia de terceros se baja una
vez, se versiona a mano y queda anotada acá con su huella.

La diferencia práctica: por CDN el sitio deja de funcionar si `unpkg` se
cae o si bloquean el dominio, y la versión puede cambiar debajo de los pies
sin que nadie lo decida. Local, el archivo es el mismo siempre y el proyecto
se puede ejecutar sin internet.

### Por qué esta versión y no la última

1.9.10 es la que nombra la presentación. Existe la rama 2.x, que cambia
algunos comportamientos por defecto. Mientras la fuente normativa de la
materia sea esa diapositiva, conviene usar exactamente esa versión: si el
profesor abre el proyecto y compara, coincide.

### Cómo se carga

Con una sola etiqueta en el fragmento de `<head>`
(`templates/fragmentos/comunes.html`), con `defer` para que no frene el
dibujado:

```html
<script th:src="@{/js/vendor/htmx.min.js}" defer></script>
```

Es la **única** etiqueta de script que necesita toda la parte migrada del
sitio: a partir de ahí, las peticiones se declaran con atributos `hx-*` en
el propio HTML y no se escribe ni un `fetch`.

### Esta sí toca la red, y es su trabajo

A diferencia de `qrcode.js`, htmx **sí** usa `XMLHttpRequest`: es
literalmente para lo que existe. Lo que hay que saber es qué puede pedir:
solo las direcciones que estén escritas en los atributos `hx-*` de nuestras
propias plantillas. No descubre endpoints por su cuenta ni manda nada a
terceros.
