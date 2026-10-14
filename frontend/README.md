# Alba en los Andes — Tienda web (Etapa 2)

Frontend completo de e-commerce de vinos para la bodega **Alba en los Andes**.
Tienda publica + panel de administracion, en **HTML5 + CSS3 + JavaScript vanilla
(ES módulos)**. Sin frameworks, sin build step, sin dependencias de npm.

Es la Etapa 2 del Trabajo Integrador. **La tienda consume la API REST de la
Etapa 1**: los vinos, los pedidos y el stock salen de MySQL a traves del
back-end de Spring Boot. No hay datos de mentira ni catalogo duplicado.

---

## Cómo correrlo

Hacen falta **las dos cosas corriendo a la vez**: el back-end que sirve los
datos y un servidor estatico que sirve las paginas.

**1) El back-end (Etapa 1), en su propia terminal:**

```bash
cd ../backend && DB_PASSWORD="tu-clave" ./mvnw spring-boot:run
```

Tiene que quedar escuchando en `http://localhost:8080`. Para comprobarlo:

```bash
curl http://localhost:8080/api/productos
```

Si eso no devuelve la lista de vinos, la tienda va a mostrar la pantalla
"No se puede conectar con el servidor" en vez de un catalogo vacio.

**2) El frontend, en otra terminal:**

```bash
cd frontend && python3 -m http.server 8099
```

Los ES módulos no funcionan abriendo el archivo con doble clic, por la
politica de `file://`: hay que servirlo.

Después abrir en el navegador:

- **Tienda:** http://localhost:8099/index.html
- **Administracion:** http://localhost:8099/admin.html

(Cualquier otro server estatico sirve: `npx serve`, la extension Live Server
de VS Code, etc.)

### Usuarios de prueba del admin

| Usuario     | Contrasena  | Rol           |
|-------------|-------------|---------------|
| `admin`     | `admin123`  | administrador |
| `ejecutivo` | `ventas123` | ejecutivo     |

Este login se valida en el navegador, no contra la base: el back-end todavia
no tiene endpoint de autenticacion.

---

## Estructura del proyecto

```
frontend/
├── index.html              Tienda publica
├── admin.html              Panel de administracion
├── css/
│   ├── estilos.css         Sistema de diseno (variables, grilla, componentes)
│   └── admin.css           Estilos propios del panel
├── js/
│   ├── tienda.js           Logica de la tienda (catalogo, carrito, checkout)
│   ├── admin.js            Logica del panel (login, CRUD, pedidos, auditoria)
│   └── modules/
│       ├── api.js          CLIENTE HTTP + traduccion entre los dos modelos
│       ├── store.js        CAPA DE PERSISTENCIA (cache + escrituras a la API)
│       └── util.js         Utilidades compartidas (formato, placeholder, DOM)
├── data/
│   └── productos.js        Listas fijas de filtros + usuarios del panel
└── assets/img/vinos/       Imagenes reales descargadas de albaenlosandes.com
```

---

## Mapeo requisito funcional → pantalla → módulo de código

| Requisito | Pantalla | Módulo |
|-----------|----------|--------|
| Hero / propuesta de valor | Tienda, seccion hero | `index.html` |
| Catalogo en grilla | Tienda, seccion catalogo | `tienda.js` → `renderCatalogo()` |
| Filtros (varietal, linea, cosecha, precio) + orden | Barra de filtros | `tienda.js` → `productosFiltrados()` |
| Busqueda por texto | Input de busqueda | `tienda.js` → filtro `texto` |
| Vista de detalle de producto | Modal de detalle | `tienda.js` → `abrirDetalle()` |
| Carrito (alta/baja/modificar cantidad) | Modal carrito | `store.js` → `carrito.*` |
| Subtotal, envio e impuestos | Resumen del carrito | `tienda.js` → `calcularTotales()` |
| Checkout con validacion en cliente | Modal checkout | `tienda.js` → `procesarCheckout()` |
| Codigo promocional con descuento | Resumen del checkout | `tienda.js` → `CUPONES` y `panelResumen()` |
| Numero de tarjeta de prueba (Luhn) | Paso 03 del checkout | `tienda.js` → `generarNumeroTarjeta()` |
| Confirmacion con numero de pedido | Modal confirmacion | `tienda.js` → `mostrarConfirmacion()` |
| Control de stock (no vender de mas) | Carrito y checkout | `store.js` → `carrito.agregar()` |
| Estados vacio / carga / error | Catalogo y carrito | `tienda.js` (esqueletos, estado vacio) |
| Login simulado con rol + guarda | Pantalla de login | `admin.js` + `store.js` → `sesion.*` |
| Dashboard con metricas | Admin, Dashboard | `admin.js` → `vistaDashboard()` |
| CRUD de productos | Admin, Productos | `admin.js` → `abrirFormProducto()` |
| Edicion inline de stock y precio | Tabla de productos | `admin.js` → `guardarInline()` |
| Baja logica de productos | Boton "Baja" | `store.js` → `productos.remove()` |
| Gestion de categorias/varietales/bodegas | Admin, Categorias | `admin.js` → `vistaCategorias()` |
| Listado de pedidos + cambio de estado | Admin, Pedidos | `admin.js` → `vistaPedidos()` |
| Trazabilidad de stock (quien/que/cuando) | Admin, Auditoria | `api.js` → `movimientos.listar()` |
| Persistencia tipo repositorio | — | `store.js` sobre `api.js` |
| Carrito persistente del cliente | — | `store.js` → `carrito.*` |

### Mapeo entidad del modelo → frontend

- **Producto** → catalogo + CRUD admin, via `GET/POST/PUT/DELETE /api/productos`.
- **Usuario + Rol** → se crea o reutiliza un CLIENTE al confirmar la compra.
- **Pedido + DetallePedido + MedioPago** → carrito, checkout y listado de pedidos.
- **EstadoPedido** → los tres reales del enum: confirmado, entregado, cancelado.
- **MovimientoStock** → la vista "Trazabilidad de stock" del panel.
- **Linea / varietal / bodega** → filtros del catalogo y gestion de categorias.

### Como se traducen los nombres de los campos

El back-end y el front-end se escribieron por separado y nombran las cosas
distinto. Toda la traduccion esta concentrada en `js/modules/api.js`, en las
funciones `productoDesdeApi`, `productoHaciaApi` y `pedidoDesdeApi`:

| Back-end (MySQL / JSON) | Front-end        |
|-------------------------|------------------|
| `idProducto`            | `id`             |
| `anada`                 | `cosecha`        |
| `descripcion`           | `notasCata`      |
| `stockActual`           | `stock`          |
| `linea: "GRAN_RESERVA"` | `linea: "Gran Reserva"` |
| `imagenUrl` (remota)    | `imagen` (archivo local) |
| `estado: "CONFIRMADO"`  | `estado: "confirmado"`   |

La ruta de la imagen local se arma con el nombre del vino y la anada
(`gran-reserva-malbec-2021.jpg`). Si el archivo no existe, la tarjeta cae
sola al placeholder SVG.

---

## Decisiones de diseno

- **Paleta real de la marca.** Colores tomados del sitio albaenlosandes.com:
  petroleo `#388497` sobre blanco, con bandas en azul petroleo oscuro `#0B2C38`
  y texto negro calido `#262121`. Como `#388497` no cumple contraste para texto
  chico (4.27:1), para textos y links se usa `#2F6F7E` (5.68:1). Titulos con serif.
- **Persistencia desacoplada detras de una interfaz.** `store.js` expone una API
  tipo repositorio (`getAll`, `getById`, `create`, `update`, `remove`), la misma
  forma que tendria contra una base de datos. Cuando los datos vivian en
  localStorage las pantallas ya lo usaban asi, y al conectar la API REST se
  reemplazo SOLO ese archivo: ninguna pantalla cambio su forma de pedir datos.
- **Cache en memoria para no llenar el codigo de esperas.** Leer de localStorage
  es instantaneo; pedir por HTTP tarda. En vez de poner un `await` en cada linea
  que dibuja una tarjeta o una fila, `store.js` trae los datos una vez al
  arrancar y las lecturas van contra esa copia. Solo las escrituras esperan al
  servidor, y cuando responde refrescan la copia.
- **La logica de negocio no se duplica en el navegador.** Al confirmar una
  compra, el navegador manda que vino y cuanto, nada mas. El total, el precio
  congelado, el descuento de stock y el movimiento de SALIDA los hace
  `PedidoService` del lado del servidor. Si se calcularan aca, se podrian
  falsear desde la consola del navegador.
- **La tienda y el admin comparten la misma base.** Un cambio de precio o stock
  en el panel queda en MySQL, asi que la tienda lo ve al recargar. Una compra
  descuenta stock y aparece en los pedidos del admin y en la trazabilidad.
- **Imagenes locales.** Las fotos se descargaron de albaenlosandes.com y se
  sirven por ruta relativa, nunca por URL remota. El campo `imagenUrlOrigen`
  queda registrado en el data model solo para trazabilidad. Si una imagen no
  carga, `util.js` genera un placeholder SVG consistente con el diseno.
- **Accesibilidad.** HTML semantico, foco visible en todo lo interactivo,
  `aria-label`/`aria-live` donde corresponde, `alt` descriptivo en cada imagen,
  navegacion por teclado en tarjetas y modales (Escape cierra), y respeto de
  `prefers-reduced-motion`.
- **Responsive mobile-first**, con grilla que se adapta de 1 a 4 columnas.

---

## Limitaciones conocidas

Son todas diferencias entre lo que la pantalla ofrece y lo que el back-end
modela hoy. Ninguna rompe el funcionamiento, pero conviene tenerlas a la vista.

- **Login del navegador.** No hay endpoint de autenticacion en la API, asi que
  el ingreso al panel se valida contra la lista de `data/productos.js`. El
  back-end ademas guarda las contrasenas sin hashear, con una nota en
  `UsuarioService` de que BCrypt viene despues.
- **Envio e impuestos no se guardan.** El carrito muestra el envio ($2.500) y el
  21%, pero la tabla `pedidos` tiene una sola columna `total`, que es la suma de
  los vinos. Por eso la confirmacion dice "Vinos: $X": es el numero que quedo en
  la base. Para guardar el total final harian falta columnas nuevas.
- **Los cupones viven en el navegador.** Los codigos (`ALBA10`, `VALLEUCO15`,
  `BIENVENIDA`) estan en la constante `CUPONES` de `tienda.js`. No hay tabla de
  cupones ni columna de descuento en `pedidos`, asi que el descuento se muestra
  en el resumen y en la confirmacion pero no viaja al servidor: en la base queda
  el total de los vinos sin descontar. Igual que el envio y el 21%.
- **La tarjeta no sale del navegador.** El numero se valida con el algoritmo de
  Luhn y se usa para mostrar los ultimos cuatro digitos en la confirmacion, nada
  mas. No hay pasarela de pago ni columna donde guardarlo, y guardar numeros de
  tarjeta sin cifrar seria peor que no tenerlos. El boton "Generar numero de
  tarjeta" existe para poder probar la compra sin usar una tarjeta real.
- **La direccion de entrega no se guarda.** Se pide en el checkout y se muestra
  en la confirmacion, pero no hay columna donde ponerla.
- **Compra sin cuenta.** `UsuarioService.registrar()` exige una contrasena, asi
  que al comprar se da de alta el cliente con un valor aleatorio que no sirve
  para iniciar sesion. Lo correcto seria que la API acepte una compra sin cuenta,
  o que haya un registro de verdad antes de comprar.
- **Foto por URL, no por archivo.** La columna `imagen_url` admite 300
  caracteres y una imagen en base64 ocupa miles, asi que el formulario del admin
  pide la URL de la foto en vez de subir un archivo.
- **Tres estados de pedido, no cinco.** El enum de MySQL solo acepta
  CONFIRMADO, ENTREGADO y CANCELADO. "En preparacion" y "enviado" requieren
  cambiar la base, no la pantalla.
- **La trazabilidad cubre stock, no acciones de pantalla.** Se muestran los
  movimientos reales de inventario. "Inicio sesion" o "cambio un precio" no
  aparecen porque no hay una tabla de auditoria general.
- **Pago simulado.** El checkout no cobra: registra el pedido y confirma.
- **Fuera de alcance de esta etapa** (existen en la Etapa 1 pero este enunciado
  no los pide): el circuito de solicitudes de cancelacion y las preguntas
  frecuentes, que ya tienen endpoints (`/api/solicitudes` y `/api/faq`).

---

## Estado de los requisitos (verificado en el navegador)

Probado con el back-end y MySQL corriendo, operando la interfaz en el navegador
y verificando despues cada cambio con un `SELECT` en la base:

- **Catalogo:** los 11 vinos activos salen de `GET /api/productos`, con sus
  precios, stock y descripciones reales. El que tiene stock 0 aparece "Agotado".
- **Compra completa:** 3 botellas de Gran Reserva Malbec → pedido #22
  CONFIRMADO / TRANSFERENCIA / total $75.000, con el detalle guardando el precio
  congelado ($25.000), el stock bajando de 44 a 41, el movimiento
  `SALIDA -3 "Venta - pedido #22"` y el cliente dado de alta como usuario 7.
- **Cambio de estado:** el pedido #22 pasado a "entregado" desde el panel quedo
  `ENTREGADO` en la tabla `pedidos`.
- **Edicion inline:** poner stock 25 al Estate Reserve Malbec modifico solo esa
  columna; precio, anada, descripcion e imagen quedaron intactos.
- **Servidor caido:** con el back-end apagado, las dos pantallas muestran
  "No se puede conectar con el servidor" con boton de reintentar, en vez de un
  catalogo vacio que haria pensar que no hay vinos.
