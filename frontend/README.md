# El frontend

Todo lo que ve el cliente: las páginas, los estilos, las imágenes y los pocos
JavaScript que hacen falta.

```
frontend/
├── templates/   LAS PÁGINAS. Las arma el servidor con Thymeleaf
└── static/      Lo que el navegador baja tal cual: css, js, imágenes
```

**El detalle archivo por archivo está en
[`../docs/ESTRUCTURA.md`](../docs/ESTRUCTURA.md).** Acá está lo otro: cómo
funciona y qué reglas sigue.

---

## Las páginas las arma el servidor

Una dirección → un controlador de `backend/.../web/` → una plantilla de
`templates/`. El servidor llena la plantilla con los datos de MySQL y manda el
HTML terminado. El navegador recibe una página completa, no un esqueleto que se
rellena después.

```
GET /vinos/3   →   VinosController.vino()   →   templates/publico/vino.html
```

Esto no es un detalle técnico: cambia quién manda. El total de una compra, el
stock, los precios y quién está ingresado los decide el servidor. El navegador
no tiene con qué discutirlos.

### El encabezado y el pie están escritos una sola vez

En `templates/fragmentos/comunes.html`. Cada página los trae con
`th:replace`. Lo mismo la tarjeta de un vino (`fragmentos/vino.html`, usada por
tres pantallas) y el desglose del total (`tienda/cuentas.html`, usado por el
carrito, el pago y la confirmación).

---

## La regla: el sitio funciona sin JavaScript

Todo es formularios y enlaces normales. Los tres archivos de `static/js/` son
**agregados**, y son exactamente las cosas que el servidor no puede hacer solo:

| Archivo | Por qué no puede ser del servidor |
|---|---|
| `ingreso-google.js` | El botón de Google lo dibuja Google adentro de un iframe; hay que pedírselo |
| `casilleros.js` | Saltar de casillero al tipear y pegar el código son cosas del teclado |
| `tienda-tarjeta.js` | La tarjeta simulada y el QR se dibujan en el navegador |

Si alguno no carga, esa comodidad se pierde y el resto sigue funcionando: el
recupero se completa con el tabulador, se ingresa con email y contraseña, y la
compra se termina igual.

### HTMX, para los pedazos que se actualizan solos

`static/js/vendor/htmx.min.js` permite pedirle al servidor **un pedazo** de
página en vez de recargarla entera: los filtros de la tienda, el carrito, el
menú de Mi cuenta. El servidor devuelve ese fragmento ya dibujado.

Dónde se ve: `hx-get` / `hx-post` en las plantillas, y los métodos que devuelven
`"plantilla :: fragmento"` en los controladores.

Las librerías de terceros van **locales y versionadas a mano**, nunca por CDN:
de dónde salió cada una y su SHA-256 están en
[`static/js/vendor/LEEME.md`](static/js/vendor/LEEME.md). Así el sitio no
depende de que un servidor ajeno siga en pie, ni cambia de versión sin que
nadie lo haya decidido.

---

## Los estilos

| Archivo | Qué tiene |
|---|---|
| `static/css/estilos.css` | El sistema de diseño: colores, tipografías, botones, formularios, tarjetas |
| `static/css/sitio.css` | El sitio: encabezado, portadas, tienda, Mi cuenta, recupero |
| `static/css/admin.css` | El panel viejo, y nada más |

Los colores están todos como variables en `:root`, arriba de `estilos.css`.
Cambiar el vino de la marca es cambiar una línea.

---

## El panel de administración todavía es el viejo

`static/admin.html` + `static/js/panel/` es la única parte que funciona a la
vieja: una página que se dibuja entera con JavaScript y le pega a `/api/**`. Está
agrupada y nombrada aparte para que se vea que es lo que falta migrar, y es el
único motivo por el que siguen vivos `static/js/panel/api.js`,
`store.js`, `util.js`, `validacion.js`, `graficos.js` y `static/data/productos.js`.

Quién está ingresado ya no lo decide él: se lo pregunta al servidor con
`GET /cuenta/quien-soy` (`SesionController`), porque el ingreso pasa por Spring
Security y el `localStorage` que leía antes quedó siempre vacío. El `localStorage`
que sigue usando es nada más que una copia para no repetir el pedido: editarlo a
mano no habilita nada, cada pedido lo revisa `SecurityConfig` contra la sesión.

El día que el panel pase a Thymeleaf, esa carpeta entera se borra, y con ella
`/cuenta/quien-soy`. Está anotado
en [`../docs/MEJORAS-PENDIENTES.md`](../docs/MEJORAS-PENDIENTES.md).

---

## Cómo ver un cambio

El frontend vive acá, pero Spring lo busca adentro del jar, así que Maven lo
copia al compilar (el bloque `<resources>` de `backend/pom.xml`). En la
práctica: después de tocar una plantilla, un CSS o un JavaScript, hay que volver
a arrancar.

```bash
cd ../backend && ./arrancar.sh
```

Y el sitio queda en **http://localhost:8080**. No hace falta ningún servidor
aparte para el frontend.
