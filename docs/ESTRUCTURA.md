# Dónde está cada cosa

Mapa del repositorio, archivo por archivo, con una línea diciendo qué hace cada
uno. Si buscás algo y no sabés por dónde empezar, empezá por acá.

**La regla de oro, en una línea:** el **backend** es Java y vive en `backend/`;
el **frontend** es lo que ve el cliente y vive en `frontend/`; la **base** se
crea con los scripts de `sql/`. Nada de las tres cosas se mezcla con las otras.

---

## Las cuatro carpetas

```
gestor-inventario/
├── backend/     Java: la API, los controladores del sitio, las reglas, la base
├── frontend/    Lo que ve el cliente: páginas, estilos, imágenes, JavaScript
├── sql/         Los scripts para crear y actualizar la base MySQL
└── docs/        Documentos del proyecto (este archivo, y lo que falta hacer)
```

### Por qué el frontend está afuera del backend, si lo sirve el backend

Las páginas las arma el servidor (Thymeleaf), y Spring las busca **adentro del
jar**. Pero una cosa es *dónde las busca el programa* y otra *dónde las
escribimos nosotros*. Están en `frontend/`, a la vista, y al compilar Maven las
copia adentro del jar: eso es lo único que hace el bloque `<resources>` de
`backend/pom.xml`, que está comentado ahí mismo.

**Consecuencia práctica:** un cambio en una página o en el CSS se ve después de
volver a arrancar con `./arrancar.sh`, igual que un cambio en Java.

---

## `frontend/` — lo que ve el cliente

```
frontend/
├── README.md                 Cómo está armado el frontend y qué regla sigue
├── templates/                LAS PÁGINAS. Las arma el servidor con Thymeleaf
└── static/                   Lo que el navegador baja tal cual: css, js, imágenes
```

### `frontend/templates/` — las páginas

Cada página es un archivo. Ninguna se dibuja con JavaScript: el servidor manda
el HTML terminado. Qué página contesta en cada dirección lo deciden los
controladores de `backend/.../web/`.

| Archivo | Qué es |
|---|---|
| `publico/inicio.html` | La portada, con los vinos destacados |
| `publico/vinos.html` | El catálogo informativo, agrupado por línea |
| `publico/vino.html` | La ficha de un vino (`/vinos/3`) |
| `publico/finca.html` | La finca — texto fijo |
| `publico/nosotros.html` | La bodega y el equipo — texto fijo |
| `publico/contacto.html` | El formulario de contacto |
| `tienda/catalogo.html` | La tienda: filtros, grilla y acceso al carrito |
| `tienda/carrito.html` | El carrito |
| `tienda/pagar.html` | Finalizar compra: datos, envío y medio de pago |
| `tienda/confirmacion.html` | "Pedido confirmado", con el comprobante |
| `tienda/cuentas.html` | El desglose del total (subtotal, envío, impuestos), compartido por el carrito, el pago y la confirmación |
| `tienda/fragmentos.html` | Los pedazos de pantalla que devuelven las acciones del carrito |
| `cuenta/ingresar.html` | Ingresar y crear cuenta (las dos solapas) y el botón de Google |
| `cuenta/recupero.html` | Recuperar la contraseña: los cuatro pasos |
| `cuenta/perfil.html` | El marco de "Mi cuenta": saludo y caja |
| `cuenta/panel.html` | El menú de Mi cuenta y sus cinco secciones (datos, pedidos, solicitudes, preferencias, seguridad) |
| `cuenta/cancelacion.html` | Pedir la cancelación de un pedido |
| `legal/terminos.html` | Términos y condiciones |
| `legal/privacidad.html` | Política de privacidad |
| `legal/arrepentimiento.html` | Botón de arrepentimiento (lo exige la ley) |
| `fragmentos/comunes.html` | La cabeza del HTML, el encabezado y el pie de **todo** el sitio |
| `fragmentos/vino.html` | La tarjeta de un vino, usada por tres pantallas |
| `fragmentos/iconos.html` | Los iconos, uno por fragmento |

### `frontend/static/` — lo que baja el navegador

```
static/
├── css/
│   ├── estilos.css       Sistema de diseño: colores, botones, formularios, tarjetas
│   ├── sitio.css         Estilos del sitio: encabezado, portadas, tienda, Mi cuenta
│   └── admin.css         Estilos del panel viejo, y de nada más
├── js/
│   ├── casilleros.js     Los casilleros de a un número del recupero (escribir de corrido, pegar el código)
│   ├── ingreso-google.js Dibuja el botón de Google y lleva el email al enlace del recupero
│   ├── tienda-tarjeta.js El formulario de tarjeta simulado y el QR de transferencia
│   ├── vendor/           Librerías de terceros, versionadas a mano
│   │   ├── htmx.min.js   HTMX: pide pedazos de página al servidor sin recargar
│   │   ├── qrcode.js     Dibuja el QR de la transferencia
│   │   └── LEEME.md      De dónde salió cada librería y su SHA-256
│   └── panel/            EL PANEL VIEJO (ver abajo): admin.js y sus módulos
├── data/
│   └── productos.js      Las listas de líneas y varietales que usa el panel viejo
└── assets/img/
    ├── sitio/            Fotos de la finca, portadas y logos
    ├── vinos/            Una foto por vino, más `_sin-foto.svg` para los que no tienen
    └── equipo/           Las fotos del equipo, para "Nosotros"
```

**Los tres JavaScript del sitio son agregados, no requisitos.** Si no cargan, el
sitio funciona igual: son las cosas que el servidor no puede hacer solo (dibujar
el botón de Google, saber qué se tecleó sin mandar el formulario). Todo lo demás
son formularios y enlaces normales.

### El panel viejo, aparte de todo

`static/admin.html` + `static/js/panel/` + `static/data/productos.js` +
`static/css/admin.css` son **la única parte que todavía funciona a la vieja**: una
página que se dibuja con JavaScript y le pega a `/api/**`. Está junta y
nombrada aparte justamente para que se vea que es lo que falta migrar.

```
static/js/panel/
├── admin.js         El panel: productos, pedidos, usuarios, auditoría, tablero
├── api.js           Cliente HTTP contra /api/**
├── store.js         Caché y escrituras contra la API
├── util.js          Formato de pesos y fechas, atajos del DOM
├── validacion.js    Reglas de email y contraseña, del lado del navegador
└── graficos.js      Los gráficos del tablero, dibujados en SVG a mano
```

---

## `backend/` — Java

```
backend/
├── README.md                 La API endpoint por endpoint y las reglas de negocio
├── arrancar.sh               ARRANCA TODO: saca las claves del llavero y levanta Spring
├── pom.xml                   Dependencias y de dónde sale lo que va adentro del jar
├── mvnw, mvnw.cmd, .mvn/     Maven Wrapper: no hace falta instalar Maven
├── .env.ejemplo              Plantilla de las variables de entorno (sin claves reales)
└── src/main/
    ├── resources/
    │   ├── application.properties   TODA la configuración: base, correo, Google, Thymeleaf
    │   └── datos/fichas-vinos.json  El texto editorial de cada vino (notas de cata, maridaje)
    └── java/com/albaenlosandes/inventario/
        ├── GestorInventarioApplication.java   El main del servidor
        ├── web/          Los controladores DEL SITIO: devuelven páginas HTML
        ├── controller/   Los controladores DE LA API: devuelven JSON (/api/**)
        ├── service/      Las reglas de negocio. Acá se decide qué se puede y qué no
        ├── repository/   El acceso a la base. Interfaces sin código: las escribe Spring Data
        ├── model/        Las entidades (una clase por tabla) y los enums
        ├── config/       Seguridad: quién puede entrar a dónde
        └── escritorio/   La aplicación de escritorio en Swing (otro programa, misma API)
```

### `web/` — los controladores del sitio (HTML)

| Archivo | Qué hace |
|---|---|
| `PaginasController.java` | Las páginas de texto fijo: inicio, finca, nosotros, contacto, legales |
| `VinosController.java` | El catálogo y la ficha de cada vino |
| `TiendaController.java` | La tienda: filtros, grilla, carrito, cupones y checkout |
| `CuentaController.java` | Mi cuenta y sus cinco secciones |
| `IngresoController.java` | El alta de cuenta y el botón de Google (el ingreso con contraseña lo hace Spring Security) |
| `RecuperoController.java` | Los cuatro pasos del recupero de contraseña |
| `CancelacionController.java` | La pantalla para pedir la cancelación de un pedido |
| `CompatibilidadController.java` | Las direcciones viejas (`index.html`, `tienda.html`…) redirigen a las nuevas |
| `ModeloGlobal.java` | Los datos que el encabezado necesita en **todas** las páginas: quién entró y cuántas botellas hay en el carrito |
| `CarritoSesion.java` | El carrito, guardado en la sesión del servidor y no en el navegador |
| `VinoVista.java` | Un vino, con lo que una pantalla necesita mostrar |
| `FiltrosDelCatalogo.java` | Los filtros de la tienda, y cómo se aplican |
| `DatosDeCompra.java` | El formulario del checkout, con sus validaciones |
| `DatosBancarios.java` | La cuenta a la que se transfiere |
| `Textos.java` | Cómo se escribe cada valor de la base (`PRODUCTO_EQUIVOCADO` → "Me llegó algo distinto") |

### `controller/` — la API JSON (`/api/**`)

La consume la aplicación de escritorio. **Está abierta a propósito** y el por qué
está escrito en `config/SecurityConfig.java`.

| Archivo | Qué hace |
|---|---|
| `ProductoController.java` | ABM del catálogo de vinos |
| `UsuarioController.java` | Usuarios, ingreso, Google y los cuatro pasos del recupero |
| `PedidoController.java` | Pedidos: alta, estados y cancelación |
| `DetallePedidoController.java` | Los renglones de un pedido ya confirmado |
| `MovimientoStockController.java` | La trazabilidad del inventario (sólo lectura y alta) |
| `SolicitudCancelacionController.java` | El circuito de cancelación: evaluar, pedir y resolver |
| `PreguntaFrecuenteController.java` | Las preguntas frecuentes que se le muestran al cliente |
| `GlobalExceptionHandler.java` | Traduce cualquier error de la API a un JSON con su código HTTP |
| `RecursoNoEncontradoException.java` | "Eso que pediste por id no existe" → 404 |
| `RecuperoVencidoException.java` | "El recupero venció o se gastó" → 410 |

### `service/` — las reglas

| Archivo | Qué decide |
|---|---|
| `PedidoService.java` | El corazón: valida stock, descuenta, deja trazabilidad, cancela |
| `ProductoService.java` | Las reglas del catálogo y del stock |
| `UsuarioService.java` | Email y contraseña: formato, repetidos y los cuatro requisitos |
| `CalculadoraDeCuentas.java` | Subtotal, descuento, envío, impuestos y total |
| `Cupones.java` | Los códigos promocionales y cuánto descuenta cada uno |
| `PoliticaCancelacion.java` | Si un pedido se cancela solo o pasa a revisión |
| `SolicitudCancelacionService.java` | Quién puede pedir cancelar, y qué pasa cuando pide |
| `DetallePedidoService.java` | Modificar un pedido ya confirmado |
| `MovimientoStockService.java` | El historial del inventario (se agrega, no se corrige) |
| `PreguntaFrecuenteService.java` | Qué preguntas se le muestran según el motivo |
| `FichasVino.java` | Las fichas de cata, leídas de `datos/fichas-vinos.json` |
| `ServicioCorreo.java` | Manda el comprobante de compra y el código del recupero |
| `TransporteApiBrevo.java` | Manda el correo por HTTPS, para redes que bloquean SMTP |
| `ServicioGoogle.java` | Verifica el token que firma Google antes de creerle |
| `ServicioRecupero.java` | Los cuatro pasos del recupero: token, intentos y vencimiento |

### `config/` — seguridad

| Archivo | Qué hace |
|---|---|
| `SecurityConfig.java` | **Quién puede entrar a dónde.** Empezá por acá para entender la seguridad |
| `ProveedorAutenticacion.java` | Cómo se comprueba una contraseña al ingresar |
| `UsuarioAutenticado.java` | Quién está ingresado, como lo ve Spring Security |

### `model/` — las tablas

Una clase por tabla (`Usuario`, `Producto`, `Pedido`, `DetallePedido`,
`MovimientoStock`, `SolicitudCancelacion`, `PreguntaFrecuente`) y los enums que
copian a los `ENUM` de MySQL (`Rol`, `Linea`, `EstadoPedido`, `EstadoSolicitud`,
`MedioPago`, `MotivoCancelacion`, `TipoMovimiento`, `ProveedorIngreso`).

### `repository/` — la base

Siete interfaces **sin código**: al extender `JpaRepository`, Spring Data
escribe sola la consulta a partir del nombre del método.

### `escritorio/` — la aplicación de escritorio

Otro programa, con su propio `main` (`AppEscritorio.java`), que habla con el
**mismo servidor** por HTTP. No comparte nada con el sitio web salvo la API.
`ApiCliente.java` es la única clase que sabe que existe una red; el resto son
las pestañas (`PanelProductos`, `PanelPedidos`, `PanelUsuarios`,
`PanelMovimientos`, `PanelSolicitudes`, `PanelResumen`), sus diálogos y `UI.java`
con lo visual compartido.

---

## `sql/` — la base

**Se corren en este orden.** El primero crea la base; los demás la van
actualizando, y cada uno es una etapa del proyecto.

| Archivo | Qué hace |
|---|---|
| `gestor_inventario_alba.sql` | **Empezá por acá:** crea la base y las tablas |
| `agregar_datos_demo.sql` | Carga vinos, clientes y pedidos de ejemplo |
| `agregar_crud_completo.sql` | Completa el CRUD de todas las entidades |
| `agregar_solicitudes_cancelacion.sql` | La tabla de solicitudes de cancelación |
| `agregar_preguntas_frecuentes.sql` | La tabla de preguntas frecuentes |
| `actualizar_catalogo_real.sql` | Reemplaza el catálogo de ejemplo por el real de la bodega |
| `agregar_preferencias_vino.sql` | Las preferencias de vino del cliente |
| `agregar_pago_transferencia.sql` | La confirmación de pago por transferencia |
| `agregar_ingreso_con_google.sql` | Las columnas que necesita el ingreso con Google |
| `agregar_desglose_del_total.sql` | Guarda el desglose del total en cada pedido |

---

## `docs/`

| Archivo | Qué es |
|---|---|
| `ESTRUCTURA.md` | Este mapa |
| `MEJORAS-PENDIENTES.md` | Lo que falta hacer, un pedido por tema, listo para copiar y pegar |

---

## Archivos de la raíz

| Archivo | Qué es |
|---|---|
| `README.md` | Qué es el proyecto y cómo levantarlo |
| `.gitignore` | Lo que no se sube: `target/`, las claves (`.env`), `.DS_Store` |
| `.vscode/` | Las tareas y configuraciones de arranque de VS Code |
