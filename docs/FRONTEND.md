# El frontend de Alba en los Andes, explicado

Documento de referencia del front-end: qué hay, qué hace cada archivo, cómo se
conectan entre sí y qué decisiones hay detrás. Está escrito para alguien que
llega al proyecto sin haberlo escrito: un docente que lo evalúa, o alguien que
va a tocar el código y necesita saber dónde meter mano.

Describe el estado del código al **3 de noviembre de 2026**.

---

## 1. Qué es

La cara visible de la bodega **Alba en los Andes**: un sitio público donde se
miran y se compran vinos, y un panel de gestión donde la bodega opera el
negocio. Son nueve páginas HTML servidas como archivos estáticos.

**HTML5 + CSS3 + JavaScript puro, con módulos ES.** Sin React, sin Vue, sin
Tailwind, sin npm, sin paso de compilación. Lo único de terceros es un
generador de códigos QR, versionado a mano en `js/vendor/`.

No tiene datos propios. **Todo lo que se ve sale de MySQL** a través de la API
REST de Spring Boot que corre en `localhost:8080`. No hay catálogo duplicado ni
datos de mentira: si el back-end está apagado, la pantalla lo dice en vez de
mostrarse vacía.

### Cómo se ejecuta

Hacen falta las dos cosas a la vez:

```bash
# 1) El back-end, en una terminal
cd backend && ./arrancar.sh            # pide la clave SMTP y levanta Spring Boot en :8080

# 2) El frontend, en otra
cd frontend && python3 -m http.server 8099
```

Después: la tienda en `http://localhost:8099/index.html` y el panel en
`http://localhost:8099/admin.html`.

**Por qué hace falta un servidor y no alcanza con abrir el archivo:** los
módulos ES (`import` / `export`) no funcionan bajo el protocolo `file://`. El
navegador los bloquea por política de origen. Doble clic en `index.html` da una
página en blanco con errores de CORS en la consola.

---

## 2. El mapa

```
frontend/
├── index.html        Inicio: portada, la finca, la región, vinos destacados
├── vinos.html        Catálogo informativo, agrupado por línea
├── vino.html         Ficha completa de un vino (se abre con ?id=N)
├── tienda.html       Compra: filtros, carrito y checkout
├── finca.html        La finca (texto fijo)
├── nosotros.html     La bodega y el equipo (texto fijo)
├── contacto.html     Formulario de contacto
├── cuenta.html       Ingreso, registro y el perfil del cliente
├── admin.html        Panel de gestión (8 secciones)
│
├── css/
│   ├── estilos.css   Sistema de diseño: variables, botones, tarjetas, modales
│   ├── sitio.css     El sitio público: encabezado, portadas, fichas, pie
│   └── admin.css     El panel: barra lateral, tablas, tablero, gráficos
│
├── js/
│   ├── inicio.js     Los destacados del inicio
│   ├── vinos.js      El catálogo por línea
│   ├── vino.js       La ficha de un vino
│   ├── tienda.js     Catálogo, carrito y checkout        (el archivo más denso)
│   ├── cuenta.js     Ingreso, registro y las 5 secciones del perfil
│   ├── contacto.js   Validación del formulario
│   ├── pagina.js     Páginas de solo texto (finca, nosotros)
│   ├── admin.js      El panel entero                     (el archivo más grande)
│   │
│   ├── modules/      LO COMPARTIDO
│   │   ├── api.js            Cliente HTTP + traducción entre los dos modelos
│   │   ├── store.js          Persistencia: caché, sesión y carrito
│   │   ├── sitio.js          Encabezado y pie de todas las páginas públicas
│   │   ├── vino.js           La tarjeta de vino, que usan tres pantallas
│   │   ├── transferencia.js  Datos bancarios, botones de copiar y el QR
│   │   ├── validacion.js     Reglas de email y contraseña
│   │   ├── graficos.js       Los gráficos del tablero, en SVG a mano
│   │   └── util.js           Formato, placeholder de imagen, ayudas de DOM
│   │
│   └── vendor/
│       ├── qrcode.js   Generador de QR (Kazuhiko Arase, MIT) — de terceros
│       └── LEEME.md    De dónde salió, versión, SHA-256 y qué se revisó
│
├── data/
│   ├── productos.js     Listas fijas de los filtros (varietales, líneas)
│   └── fichas-vinos.js  La ficha de cata de cada vino (texto editorial)
│
└── assets/img/
    ├── vinos/     Fotos de las botellas
    ├── sitio/     Logo y fotos de la finca
    └── equipo/    Fotos de los integrantes
```

**La regla de nombres:** cada página pública tiene un `.js` con su mismo
nombre. `tienda.html` carga `js/tienda.js`, `cuenta.html` carga `js/cuenta.js`.
Sin excepciones, salvo las páginas de solo texto, que comparten `pagina.js`.

---

## 3. Cómo se apilan las capas

```
         ┌──────────────────────────────────────────────────┐
         │  PÁGINAS     inicio · vinos · vino · tienda       │
         │              cuenta · contacto · admin           │
         └──────────────────────┬───────────────────────────┘
                                │   piden datos y dibujan
         ┌──────────────────────┴───────────────────────────┐
         │  COMPARTIDO  sitio.js · vino.js · util.js         │
         │              validacion.js · transferencia.js     │
         │              graficos.js                          │
         └──────────────────────┬───────────────────────────┘
                                │
         ┌──────────────────────┴───────────────────────────┐
         │  store.js      caché en memoria + sesión + carrito│
         └──────────────────────┬───────────────────────────┘
                                │
         ┌──────────────────────┴───────────────────────────┐
         │  api.js        fetch + traducción de modelos      │
         └──────────────────────┬───────────────────────────┘
                                │  HTTP
                       Spring Boot en :8080  →  MySQL
```

Lo importante de este dibujo son las dos reglas que impone:

**Una sola puerta de salida.** `api.js` es el único archivo del frontend que
sabe que existe un servidor en `localhost:8080`. Si el back-end cambia de
puerto, se toca una línea. Si cambia el formato de un campo, se toca una
función.

**Ninguna pantalla habla con la API directamente.** Las páginas le piden los
datos a `store.js`, que les responde desde la caché. La única excepción es
`admin.js`, que para sus datos exclusivos (movimientos, usuarios, solicitudes y
preguntas frecuentes) llama a `api.js` sin pasar por la caché, porque son datos
que solo él usa y que mira de a una sección por vez.

---

## 4. Los módulos compartidos

### `modules/util.js` — lo que usa todo el mundo

El archivo más humilde y el más usado. Cinco cosas:

- **`pesos(n)` y las fechas.** Formato argentino en un solo lugar:
  `$ 25.000`, `03/11/2026, 16:42`. Si cada pantalla formateara por su cuenta,
  tarde o temprano una mostraría `25000.00`.
- **`crear(tag, attrs, ...hijos)`.** El reemplazo de `innerHTML` en todo el
  proyecto. Crea un elemento con sus atributos, sus clases y sus eventos en una
  llamada. **Por qué importa:** `innerHTML` con datos de la base abre la puerta
  a inyección de HTML; `crear()` usa `createTextNode`, que escapa solo.
- **`placeholderVino(nombre)`.** Dibuja una botella en SVG cuando la foto no
  existe. Devuelve un data URI listo para poner en un `src`, así que no hace
  falta tener un archivo de imagen de respaldo.
- **`$` y `$$`.** Atajos de `querySelector` y `querySelectorAll`.
- **`toast(mensaje)`.** El aviso flotante que se va solo a los 3 segundos.

### `modules/api.js` — la puerta al servidor

Hace dos trabajos distintos que vale la pena separar mentalmente.

**Uno: habla HTTP.** Una función `pedir()` que envuelve `fetch`, y cuatro
atajos (`get`, `post`, `put`, `borrar`). Todo el manejo de errores está ahí:

- Si el servidor no contesta, `fetch` falla de una forma particular que se
  traduce a un mensaje entendible: *"No se puede contactar al servidor.
  Verifica que Spring Boot esté corriendo en localhost:8080"*.
- Si contesta con error, se lee el cuerpo que arma el `GlobalExceptionHandler`
  del back-end, que ya viene en castellano y explicado, y se lanza un
  `ErrorApi` con ese texto. **Las pantallas muestran el mensaje del servidor
  tal cual**, en vez de inventar uno genérico.
- Un `204 No Content` (lo que devuelve DELETE) no se intenta parsear.

**Dos: traduce entre los dos modelos.** El back-end y el frontend se
escribieron por separado y nombran las cosas distinto. En vez de renombrar
campos en cada pantalla, la traducción vive acá concentrada:

| Back-end (MySQL / JSON) | Frontend          |
|-------------------------|-------------------|
| `idProducto`            | `id`              |
| `anada`                 | `cosecha`         |
| `descripcion`           | `notasCata`       |
| `stockActual`           | `stock`           |
| `linea: "GRAN_RESERVA"` | `linea: "Gran Reserva"` |
| `estado: "CONFIRMADO"`  | `estado: "confirmado"`  |
| `imagenUrl` (remota)    | `imagen` (archivo local) |

Las cuatro funciones que lo hacen son `productoDesdeApi`, `productoHaciaApi`,
`pedidoDesdeApi` y `pedidoHaciaApi`.

Dos detalles que se vuelven a encontrar en el resto del código:

- **La ruta de la imagen se arma, no se guarda.** `imagenLocal()` convierte
  "Gran Reserva Malbec" + 2021 en `assets/img/vinos/gran-reserva-malbec-2021.jpg`,
  sacando acentos y espacios. Si el archivo no existe, la tarjeta cae sola al
  placeholder SVG, así que acá no hace falta comprobar nada.
- **`pedidoDesdeApi` deduce `esperaComprobante`** en vez de pedírselo al
  servidor: es `medioPago === TRANSFERENCIA && !pagoConfirmado`. Por eso no hizo
  falta inventar un estado de pedido nuevo en la base: el pago es un eje
  distinto del estado.

Después de eso, el objeto `api` con una operación por endpoint, agrupadas en
`productos`, `pedidos`, `usuarios`, `movimientos`, `faq` y `solicitudes`.

### `modules/store.js` — la caché, la sesión y el carrito

El archivo con más decisiones de diseño adentro.

**Qué problema resuelve.** Antes los datos vivían en `localStorage` y leerlos
era instantáneo. Al pasar a HTTP, cada lectura pasa a tardar. Poner un `await`
en las cuarenta líneas que dibujan tarjetas y filas habría sido cambiar todas
las pantallas.

**Cómo lo resuelve:** una caché en memoria.

- `cargar()` pide productos y pedidos **una sola vez** al abrir la página, los
  dos en paralelo con `Promise.all`.
- Las **lecturas** (`getAll`, `getById`, `getActivos`) leen de esa copia y
  siguen siendo instantáneas. Las pantallas no cambiaron.
- Las **escrituras** (`create`, `update`, `remove`, `cambiarEstado`) sí van al
  servidor, y cuando contesta refrescan la copia. Son las únicas que esperan.

Expone una interfaz de repositorio — `getAll`, `getById`, `create`, `update`,
`remove` — que es la misma forma que tendría contra una base de datos. **Ese es
el motivo de que exista:** cuando los datos vivían en `localStorage` las
pantallas ya lo usaban así, y al conectar la API REST se reemplazó solo este
archivo. Ninguna pantalla cambió su forma de pedir datos.

Tres cosas más que viven acá:

**La sesión.** `sesion.login()` llama a `POST /api/usuarios/login` y guarda la
respuesta en `localStorage`. **El rol lo decide el servidor**, no el navegador.
La contraseña viaja una vez y se descarta. También está `loginGoogle()`, que
hace lo mismo con el token de Google. Hay un detalle cuidado en
`sesion.actualizar()`: al editar el perfil se refresca la copia local del
nombre y el email, pero **nunca el rol**, para que nada de lo que pase en el
navegador pueda autoascender a nadie.

**El carrito.** Vive en `localStorage` y no en la base, a propósito: un carrito
a medio armar es un borrador del cliente, no una venta. Recién al confirmar se
convierte en pedido y viaja al servidor. `carrito.agregar()` controla el stock
contra la caché, pero ese control **es por comodidad, para avisar al
instante**: el que de verdad cuenta es el del servidor al confirmar, porque
entre que alguien mira el catálogo y paga, otro pudo comprar la última botella.

**`resolverUsuario()`**, la función incómoda. La tabla `pedidos` no guarda
datos del comprador: guarda un `id_usuario`. Así que al comprar hay que
convertir nombre, email y teléfono en un usuario de la base: si el email ya
existe se reutiliza, y si no se da de alta como CLIENTE. Como
`UsuarioService.registrar()` exige una contraseña válida, se genera
`"SinCuenta1-" + crypto.randomUUID()`. El prefijo no es decorativo: un UUID
solo no tiene mayúscula, y sin ella el back-end rechazaba el alta y **la compra
se caía entera** para cualquiera con un email nuevo.

### `modules/sitio.js` — el encabezado y el pie

Seis páginas públicas comparten barra de navegación y pie. Si estuvieran
copiados en cada HTML, agregar un enlace serían seis ediciones y cualquier
olvido dejaría una página distinta.

Además el encabezado cambia según quién mire: un visitante ve "Ingresar", un
cliente ve su nombre y sus secciones, un administrador ve además el acceso al
panel. Esa decisión vive en un solo lugar.

Lo que arma:

- **La cinta superior** con la ubicación y el bloque de cuenta.
- **La barra de navegación**: Inicio · Vinos · Comprar · Finca · Nosotros ·
  Contacto, con la página actual marcada con `aria-current="page"`.
- **El botón del carrito** con su burbuja de cantidad.
- **El pie**, con cuatro columnas y una franja legal.

Dos cosas que no son obvias:

**La columna legal apunta a los textos oficiales en argentina.gob.ar**, no a
resúmenes propios: la Ley 24.788 (venta de alcohol), la Resolución 424/2020
(botón de arrepentimiento) y la Ley 24.240 (defensa del consumidor). Quien
quiera verificar qué dice la ley tiene que poder leerla en la fuente. Se abren
en pestaña nueva con `rel="noopener"`, que es obligatorio con `target="_blank"`:
sin eso, la página que se abre puede manipular la nuestra vía `window.opener`.

**`activarRevelado()`** hace que cada sección aparezca al llegar con el scroll,
usando `IntersectionObserver`. La clase la pone el JavaScript y no el HTML a
propósito: si estuviera escrita en el HTML y el JavaScript fallara, las
secciones quedarían invisibles para siempre. Puesta desde acá, el peor caso es
que no haya animación. Y no se aplica si el sistema operativo pide menos
movimiento (`prefers-reduced-motion`).

También exporta `avisoSinServidor()`, la pantalla de "No se puede conectar con
el servidor" con botón de reintentar.

### `modules/vino.js` — la tarjeta

Una sola función, `tarjetaVino(producto, opciones)`, que usan tres pantallas:
el inicio, el catálogo informativo y la tienda. Lo que cambia entre ellas se
pasa por parámetro: si lleva botón de compra, qué hacer al tocarla, y si suma
los botones de editar y dar de baja.

Esos dos últimos **solo se pasan cuando quien mira es administrador**, así que
para el resto ni se dibujan. Es un control de pantalla, no de seguridad: los
endpoints siguen abiertos a quien llame a la API directamente.

La tarjeta muestra "Agotado" con stock 0 y "Últimas botellas" cuando el stock
cae por debajo del mínimo, es navegable por teclado (`tabindex`, Enter) y cae
al placeholder SVG si la foto no carga.

### `modules/validacion.js` — las reglas de email y contraseña

Las mismas reglas hacen falta en tres pantallas: el registro, la sección
Seguridad del perfil y el alta de usuarios del panel. Copiadas en tres lados,
tarde o temprano dicen cosas distintas.

Las cuatro condiciones de la contraseña están en una lista, y de esa lista
salen **las dos cosas**: los requisitos que se muestran en pantalla y el
mensaje de error. Agregar una regla es agregar un renglón.

El mensaje dice **qué falta**, no repite la regla entera: quien ya puso ocho
caracteres y un número no necesita que le reciten las cuatro condiciones,
necesita saber que le falta una mayúscula.

Nada de esto reemplaza al back-end, que valida lo mismo por su cuenta: todo
esto corre en el navegador y se saltea con cualquier cliente HTTP.

### `modules/transferencia.js` — los datos bancarios

El alias, el CVU y el titular de la cuenta, los botones para copiarlos y el QR
de pago.

**Por qué están en el código y no en la base:** no son un secreto. Son
exactamente el dato que se le entrega a quien tiene que pagarte. Guardarlos en
una tabla agregaría una entidad, un repositorio y una pantalla de edición para
un dato que cambia una vez cada varios años.

**Están repetidos en `application.properties` del back-end**, y no es un
descuido: son dos lugares que no pueden leerse entre sí. El checkout los
muestra sin preguntarle al servidor, y el mail lo arma el servidor sin pasar
por el navegador. Si cambian, hay que tocar los dos.

El botón de copiar usa `navigator.clipboard`, que **solo existe en HTTPS o en
localhost**. Por eso hay respaldo: si no está disponible, se selecciona el
texto para que la persona copie con Ctrl+C en vez de quedarse sin nada.

### `modules/graficos.js` — los gráficos, dibujados a mano

Sin npm no hay Chart.js, así que los dos gráficos del tablero se arman acá:

- **`barrasMensuales()`**: barras verticales con escala, en SVG. Para la serie
  de los últimos meses, donde hace falta una escala dibujada.
- **`barrasRanking()`**: barras horizontales, con elementos HTML normales y
  ancho porcentual. **No es SVG a propósito**: tienen un rótulo largo al lado
  (el nombre del vino), y en SVG habría que medir el texto a mano para que no
  se desborde, además de que no se podría seleccionar ni lo leería bien un
  lector de pantalla.
- **No hay tortas**, también a propósito: para tres o cuatro categorías los
  números con su porcentaje se comparan mejor que los ángulos.

---

## 5. Las páginas públicas

### `index.html` + `inicio.js` — Inicio

Casi todo contenido fijo escrito en el HTML: la portada, la región, la finca.
Lo único que sale de la base son los **vinos destacados**, hasta ocho, para que
la portada muestre siempre lo que de verdad hay a la venta y no una lista que
haya que mantener a mano. Mientras cargan se muestran cuatro esqueletos
animados. Tocar un destacado lleva a su ficha, no a la tienda.

### `vinos.html` + `vinos.js` — Nuestros vinos

El catálogo **informativo**, agrupado por línea: Finca, Estate Reserve, Gran
Reserva y Ediciones Especiales. Cada línea tiene un texto editorial que vive en
el JavaScript, y abajo los vinos que le corresponden con sus notas de cata, que
sí salen de la columna `descripcion` de la base. Acá no se compra.

### `vino.html` + `vino.js` — La ficha de un vino

Se abre con `?id=N` y es a donde se llega al tocar un vino en cualquier parte
del sitio. Combina dos fuentes:

- **De la API:** nombre, línea, varietal, cosecha, precio, stock, si está
  activo. Lo que cambia y lo que el panel edita.
- **De `data/fichas-vinos.js`:** notas de cata, vinificación, crianza, viñedo,
  analítica. Texto editorial fijo.

Si un vino no tiene ficha cargada — por ejemplo uno dado de alta desde el panel
— la página **igual funciona**: muestra lo que da la base y saltea las
secciones que le faltan, en vez de dejar títulos vacíos.

### `tienda.html` + `tienda.js` — La compra

La pantalla más densa del sitio. Cuatro cosas en un archivo:

**El catálogo con filtros.** Búsqueda por texto, varietal, línea, cosecha,
precio máximo y orden. Todos se aplican sobre la copia que ya está en memoria,
así que filtrar es instantáneo y no pega al servidor. Los selectores se pueblan
con los valores que de verdad existen en el catálogo.

**El carrito.** Un modal con las cantidades editables, que avisa si no hay
stock suficiente. El contador vive en el encabezado compartido.

**El checkout.** Dos columnas: a la izquierda los tres pasos a completar
(datos, entrega, pago), a la derecha el resumen del pedido siempre a la vista.
Antes era una sola columna con el total al final, y para saber cuánto se estaba
por pagar había que llegar hasta abajo. En pantalla angosta se apilan solas.

Dentro del checkout:

- **Los medios de pago son fichas elegibles**, no una lista desplegable, y
  muestran u ocultan los campos de la tarjeta según cuál se elija. Hoy son dos:
  tarjeta y transferencia. *(El efectivo se quitó: la bodega dejó de cobrar
  contra entrega. El valor sigue existiendo en el back-end y en la base porque
  hay pedidos viejos pagados así, pero un pedido nuevo que lo mande se
  rechaza.)*
- **La tarjeta se valida con el algoritmo de Luhn**, el mismo que hace el banco
  antes de cobrar, y hay un botón que genera un número de prueba válido para
  poder probar la compra sin usar una tarjeta real. **El número no sale del
  navegador**: se usa para mostrar los últimos cuatro dígitos en la
  confirmación y nada más.
- **Los cupones** (`ALBA10`, `VALLEUCO15`, `BIENVENIDA`) viven en una constante
  del archivo. No hay tabla de cupones.
- **Envío fijo de $2.500 y 21% de impuestos**, que se muestran en el resumen.

**La gestión rápida.** Si quien mira es administrador, cada tarjeta suma
"Editar" y "Dar de baja", para corregir un precio sin ir al panel.

### `cuenta.html` + `cuenta.js` — Mi cuenta

Tiene dos caras según haya sesión o no.

**Sin sesión:** el formulario de ingreso y el de registro, más el **botón de
Google**. El botón oficial se dibuja en un iframe de Google, y hay un detalle
resuelto que no se ve: **el ancho se mide, no se adivina**, porque el iframe no
se adapta solo al contenedor. Si el script de Google no carga (sin internet, o
bloqueado), el resto de la pantalla funciona igual.

**Con sesión:** el perfil, con un menú lateral de cinco secciones que se eligen
por el `#hash` de la dirección, así que se puede enlazar directo a cualquiera
desde el pie del sitio:

| Sección | Qué hace |
|---|---|
| **Datos principales** | Nombre, apellido, email y teléfono |
| **Mis pedidos** | El historial, con el estado y el botón de pedir cancelación |
| **Mis solicitudes** | El seguimiento de las cancelaciones pedidas |
| **Preferencias de vino** | Gustos del cliente |
| **Seguridad** | Cambio de contraseña, exigiendo la actual |

El perfil se guarda contra `PUT /api/usuarios/{id}/perfil`, un endpoint
distinto del de administración **a propósito**: ese ignora `rol` y `activo`,
así que la pantalla del cliente no puede autoascenderse a administrador.

La cancelación de un pedido muestra primero **las preguntas frecuentes del
motivo elegido**, para resolverle el problema a la persona sin que tenga que
esperar una respuesta. Si igual quiere seguir, se le pregunta al servidor si
ese pedido se puede cancelar y cómo: la regla (dentro de las 24 horas se
cancela solo, después pasa a revisión) vive en `PoliticaCancelacion`, del lado
del servidor. **La pantalla solo muestra lo que contesta**: si la regla se
copiara acá, cambiarla obligaría a tocar dos lugares.

### `contacto.html` + `contacto.js`

Formulario con validación en el navegador. **No manda correos**, y la página lo
dice en vez de simular un envío que no ocurre. Si alguien escribe por un pedido
ya hecho, el camino real es el circuito de cancelaciones. Si ya hay sesión, el
nombre y el email vienen completados.

### `finca.html` y `nosotros.html` + `pagina.js`

Páginas de solo texto. No piden nada a la API: lo único que necesitan es el
encabezado y el pie. El id de la página se toma del atributo `data-pagina` del
`<body>` o se deduce del nombre del archivo, para no tener que escribir un
archivo JavaScript por cada página de texto.

---

## 6. El panel de gestión

`admin.html` + `admin.js`, ocho secciones.

**Cómo está armado:** cada sección es una función que recibe el contenedor y lo
llena. `ir(vista)` limpia y llama a la que corresponde. No hay ruteo ni estado
global compartido: cada sección se dibuja entera con los datos del momento, que
es lo más simple de seguir y evita que una pantalla quede desactualizada
respecto de otra.

**La guarda de acceso.** El ingreso no se hace acá: se hace en `cuenta.html`
contra `POST /api/usuarios/login`, y el servidor dice el rol. Esta página solo
comprueba que quien llegó sea ADMIN y, si no, lo manda a ingresar. Es una
guarda de pantalla, no de seguridad.

**El selector de operador**, arriba a la derecha: quién firma los movimientos
de stock que se generen.

| Sección | Qué hace |
|---|---|
| **Tablero** | Dos bloques: **Cómo vendo** (ventas del mes, ticket promedio, cancelados, ventas por mes y vinos más vendidos) y **Qué tengo** (vinos en catálogo, botellas en stock, valorización a precio de lista y cuánto dura el stock) |
| **Inventario** | El catálogo completo, incluidos los dados de baja. Alta, edición, baja lógica y reactivación |
| **Ingreso** | Suma botellas **y deja el movimiento de ENTRADA** con su responsable, en una sola operación del servidor. No se hace con un PUT de stock: eso cambiaría el número sin dejar rastro de quién ni por qué |
| **Movimientos** | La auditoría de stock, de solo lectura: cada entrada, salida o ajuste con su usuario, su motivo y su fecha |
| **Pedidos** | Las ventas, el cambio de estado, la acreditación de transferencias y el reenvío del comprobante por mail |
| **Clientes** | Los usuarios, altas y bajas lógicas |
| **Solicitudes** | La bandeja de cancelaciones, para aprobarlas o rechazarlas con una respuesta |
| **FAQ** | Las preguntas que ve el cliente al querer cancelar |

Dos cosas del tablero que no son obvias:

- **`esVenta()` descarta los cancelados** de todas las métricas. Un pedido
  cancelado devolvió el stock y no cobró nada: contarlo como venta inflaría los
  números.
- Los gráficos se calculan en el navegador sobre los pedidos que ya están en la
  caché, no con endpoints de agregación. Con el volumen de este proyecto
  alcanza; con miles de pedidos habría que mover la cuenta al servidor.

---

## 7. Los datos que viven en el frontend

Dos archivos, con criterio distinto de los que viven en MySQL.

**`data/productos.js`** tiene las listas de valores posibles que usan los
filtros y los formularios: `VARIETALES` y `LINEAS`. En la base son columnas
enum o texto acotado.

**`data/fichas-vinos.js`** tiene la ficha de cata de cada vino: el suelo del
viñedo, los meses de barrica, la analítica del laboratorio, con el enlace de
origen de cada una para poder volver a chequearla.

**Por qué la ficha de cata no está en MySQL:** la tabla `productos` guarda lo
que cambia y lo que el panel edita — precio, stock, añada, estado, una
descripción corta. La ficha es texto editorial fijo. No cambia entre dos
consultas al stock y ningún formulario la llena, así que guardarla en la base
obligaría a sumar diez columnas de texto que nadie escribe.

El viñedo se describe **una sola vez** en la constante `VINEDO`, porque las
diez etiquetas salen del mismo cuadro: repetirlo en cada ficha obligaría a
corregir un dato diez veces.

---

## 8. Los estilos

Tres hojas, sin preprocesador y sin framework.

**`estilos.css`** es el sistema de diseño: las variables de color, los botones,
las tarjetas, los formularios, los modales, los toasts. Todo lo que se repite.

**`sitio.css`** es el sitio público: encabezado, portadas, las fichas anchas de
"Nuestros vinos", el perfil con menú lateral, el pie.

**`admin.css`** es el panel: barra lateral, tablas, tarjetas de números,
gráficos, modales del panel.

**La paleta es la real de la marca**, tomada de albaenlosandes.com: petróleo
`#388497` sobre blanco, bandas en azul petróleo oscuro `#0B2C38`, texto negro
cálido `#262121`. Con un detalle de accesibilidad: **`#388497` no cumple
contraste para texto chico** (4.27:1, y el mínimo es 4.5:1), así que para
textos y enlaces se usa `#2F6F7E`, que da 5.68:1. El color de marca queda para
fondos, bordes y elementos grandes.

Títulos en serif (Georgia), texto en la fuente del sistema. Ninguna fuente se
descarga: no hay pedido a Google Fonts ni espera de red para ver el texto.

Maquetación con **Grid y Flexbox**, mobile-first, con la grilla del catálogo
pasando de 1 a 4 columnas según el ancho.

---

## 9. Tres recorridos completos

### Una compra

```
1. La persona filtra el catálogo            → tienda.js, sobre la caché
2. Agrega al carrito                        → store.carrito, a localStorage
3. Abre el checkout y completa los pasos    → tienda.js
4. Confirma                                 → store.pedidos.create()
5.   ├─ busca o crea el usuario             → POST /api/usuarios
6.   └─ manda qué vino y cuánto             → POST /api/pedidos
7. El SERVIDOR bloquea los productos, verifica el stock, congela el
   precio, calcula el total, guarda el pedido, descuenta stock y
   registra los movimientos de SALIDA
8. El servidor manda el comprobante por mail (en otro hilo)
9. El navegador refresca productos y pedidos, y muestra la confirmación
```

**Lo importante del paso 6:** el navegador manda qué vino y cuánto, nada más.
No manda el total. Si lo calculara acá, se podría falsear desde la consola.

### Un ingreso al sitio

```
1. cuenta.html muestra el formulario (o el botón de Google)
2. sesion.login(email, password)     → POST /api/usuarios/login
3. El SERVIDOR valida y devuelve el usuario CON SU ROL
4. Se guarda en localStorage: id, nombre, email, teléfono, rol
5. Según el rol: un ADMIN va al panel, un CLIENTE a su perfil
6. El encabezado de todas las páginas lee esa sesión y se dibuja distinto
```

Lo que queda guardado **es un dato, no una llave**: sirve para que la página
sepa a quién saludar y qué mostrar, no para autorizar nada.

### Una cancelación

```
1. El cliente toca "Pedir cancelación" en Mis pedidos
2. Elige un motivo
3. Se muestran las FAQ de ese motivo        → GET /api/faq
4. Si insiste, se le pregunta al servidor   → GET /api/solicitudes/evaluar
5. El servidor contesta según PoliticaCancelacion:
     dentro de 24 h  → se cancela sola
     después         → pasa a revisión
6. Se crea la solicitud                     → POST /api/solicitudes
7. Cae en la bandeja del panel, donde un admin la resuelve
8. Si se aprueba, el SERVIDOR cancela el pedido y devuelve el stock
```

---

## 10. Accesibilidad

No es una sección decorativa: está hecho y se puede verificar.

- **HTML semántico**: `<header>`, `<nav>`, `<main>`, `<section>`, `<footer>`.
- **Foco visible** en todo lo interactivo, sin `outline: none` suelto.
- **Navegación por teclado**: las tarjetas responden a Enter, los modales se
  cierran con Escape.
- **`aria-label`** en los botones que son solo un ícono, **`aria-live`** en los
  mensajes de error y en el resultado de los filtros, **`aria-current="page"`**
  en la página actual del menú.
- **`alt` descriptivo** en cada imagen que informa, y `alt=""` con
  `aria-hidden` en las decorativas, como el sello del encabezado (al lado está
  el nombre escrito).
- **Contraste verificado**, con el color de texto distinto del de marca.
- **`prefers-reduced-motion`** respetado: sin animación de aparición para quien
  la pidió apagada.
- Las mayúsculas del nombre en la cinta **se hacen con CSS** y no cambiando el
  texto, así lo que lee un lector de pantalla sigue siendo el nombre como la
  persona lo escribió.

---

## 11. Qué NO decide el navegador

Vale la pena tenerlo junto, porque es el criterio que ordena todo el código:

| Decisión | Dónde vive |
|---|---|
| El total de un pedido | `PedidoService`, en el servidor |
| El precio congelado de cada renglón | `PedidoService` |
| El descuento de stock y los movimientos | `PedidoService` |
| Si hay stock suficiente (de verdad) | El servidor, al confirmar |
| El rol de quien ingresa | `POST /api/usuarios/login` |
| Si un pedido se puede cancelar | `PoliticaCancelacion` |
| Si una transferencia está acreditada | El panel, contra el servidor |
| Si el pago nace confirmado | `PedidoService` |

El frontend valida, avisa y muestra. **Las reglas de plata están todas del otro
lado**, porque cualquier cosa que decida el navegador se puede falsear desde la
consola.

---

## 12. Limitaciones conocidas

Son diferencias entre lo que la pantalla ofrece y lo que el back-end modela
hoy. Ninguna rompe el funcionamiento.

- **No hay autorización real.** El servidor confirma quién es cada uno, pero no
  entrega ninguna credencial de sesión. Los endpoints de administración siguen
  abiertos a quien llame a la API directamente. Cerrarlos es trabajo de Spring
  Security. Las contraseñas, además, se guardan sin hashear.
- **Envío e impuestos no se guardan.** El carrito muestra $2.500 de envío y el
  21%, pero la tabla `pedidos` tiene una sola columna `total`, que es la suma de
  los vinos.
- **Los cupones no viajan al servidor.** El descuento se muestra en el resumen
  y en la confirmación, pero en la base queda el total sin descontar.
- **La tarjeta no sale del navegador.** No hay pasarela de pago ni columna
  donde guardarla, y guardar números de tarjeta sin cifrar sería peor que no
  tenerlos.
- **La dirección de entrega no se guarda.** Se pide y se muestra, pero no hay
  columna.
- **Compra sin cuenta.** Se da de alta el cliente con una contraseña aleatoria
  que no sirve para ingresar. Lo correcto sería que la API acepte una compra
  sin cuenta.
- **Foto por URL, no por archivo.** La columna `imagen_url` admite 300
  caracteres y una imagen en base64 ocupa miles.
- **Tres estados de pedido, no cinco.** El enum de MySQL solo acepta
  CONFIRMADO, ENTREGADO y CANCELADO. "En preparación" y "enviado" requieren
  cambiar la base, no la pantalla.
- **La auditoría cubre stock, no acciones de pantalla.** "Inició sesión" o
  "cambió un precio" no aparecen porque no hay una tabla de auditoría general.
- **El formulario de contacto no manda nada.**
- **El pago es simulado.** El checkout registra el pedido y confirma; no cobra.

---

## 13. Deuda detectada

Cosas encontradas al escribir este documento, que no son limitaciones de diseño
sino restos de versiones anteriores:

- **`USUARIOS_SEED` y `BODEGAS`, en `data/productos.js`, ya no los usa nadie.**
  Son de cuando el ingreso al panel se validaba contra una lista escrita en el
  JavaScript. Hoy el login va contra el servidor. `USUARIOS_SEED` tiene
  contraseñas escritas en texto plano que no sirven para entrar a ningún lado:
  conviene borrar las dos constantes antes de que alguien las crea vigentes.
- **`frontend/README.md` quedó atrás en tres puntos:** dice que el login del
  panel se valida en el navegador contra `data/productos.js` (ya no), publica
  una tabla de usuarios de prueba que ya no funciona, y describe un panel de
  secciones distintas de las ocho actuales. Tampoco menciona el ingreso con
  Google, el circuito de cancelaciones ni las preguntas frecuentes.
