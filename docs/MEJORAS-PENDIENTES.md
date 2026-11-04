# Mejoras pendientes — Alba en los Andes

Un *mini prompt* por tema, para ir trabajándolos de a uno. Cada uno es
autocontenido: se puede copiar y pegar tal cual sin tener que explicar de nuevo
el proyecto.

**Antes de empezar cualquiera**, conviene arrancar los dos servidores:

```bash
cd ~/Downloads/gestor-inventario/backend && ./mvnw spring-boot:run
```
```bash
cd ~/Downloads/gestor-inventario/frontend && python3 -m http.server 8099
```

---

## Orden sugerido

No están en el orden de la lista original, sino en el que conviene hacerlos:
primero lo que desbloquea a los demás, al final lo que depende de cosas externas.

| # | Tema | Dificultad | ¿Necesita algo de afuera? |
|---|---|---|---|
| 1 | Base de datos compartida | Media | Sí — servidor o cuenta en la nube |
| 2 | Seguridad real (Spring Security) | Alta | No |
| ~~3~~ | ~~Validación de email y contraseña~~ **HECHO** | Baja | No |
| ~~4~~ | ~~Ir a Inicio al iniciar sesión~~ **HECHO** | Muy baja | No |
| ~~5~~ | ~~Perfil del usuario~~ **HECHO** | Media | No |
| ~~6~~ | ~~Acceso al perfil desde el pie~~ **HECHO** | Muy baja | No |
| ~~7~~ | ~~Detalle y edición de pedidos en el panel~~ **HECHO** | Media | No |
| 8 | Sección Usuarios del panel | Media | No |
| ~~9~~ | ~~Estadísticas del tablero~~ **HECHO** | Media | No |
| 10 | Interfaz y distribución de pedidos | Media | No |
| 11 | Derechos de venta en el pie | Baja | No |
| 12 | Más preguntas frecuentes | Baja | No |
| 13 | Logo real | Baja | Sí — el archivo del logo |
| 14 | Datos de transferencia + confirmación de pago | Media | No |
| 15 | QR de pago | Media | Depende de la decisión |
| 16 | Factura por mail | Alta | Sí — cuenta de correo |
| 17 | Ingreso con Google | Alta | Sí — credenciales de Google |
| 18 | Dominios | — | Sí — es averiguar, no programar |

---

# 1 · Base de datos compartida

**Qué pasa hoy:** `application.properties` apunta a
`jdbc:mysql://localhost:3306/gestor_inventario`. Cada integrante corre su propio
MySQL en su propia máquina, así que **cada uno ve un stock distinto**. No es un
error del código: es que hay tantas bases como computadoras.

```
En el proyecto Alba en los Andes, el back-end apunta a jdbc:mysql://localhost:3306.
Como cada integrante del grupo corre MySQL en su propia máquina, cada uno ve
stock y pedidos distintos.

Necesito pasar a UNA sola base compartida. Explicame primero las opciones con
sus costos y contras (MySQL gratis en la nube, una máquina del grupo haciendo de
servidor, etc.), recomendame una para un trabajo práctico de facultad, y después
implementala.

Importante: la contraseña de la base NO puede quedar escrita en el repositorio.
Hoy se lee de la variable de entorno DB_PASSWORD; mantené ese esquema y explicá
a los demás integrantes cómo configurarla en su máquina.

Al terminar, verificá que desde mi máquina veo los mismos datos que habría en la
base compartida, y dejá en el README cómo se conecta cada uno.
```

---

# 2 · Seguridad real

**Qué pasa hoy:** el `pom.xml` **no tiene Spring Security**. Las contraseñas se
guardan en texto plano (la columna se llama `password_hash` pero no guarda un
hash). Los endpoints de administración están abiertos: cualquiera que llame a la
API directamente puede borrar productos sin pasar por el login. La guarda del
panel es de pantalla, no de seguridad, y está dicho en el código.

```
En el proyecto Alba en los Andes hay tres agujeros de seguridad que quiero
cerrar, en este orden:

1. Las contraseñas se guardan en texto plano. Quiero hashearlas con BCrypt al
   registrar y comparar contra el hash al ingresar. Necesito también migrar las
   que ya están en la base sin dejar a nadie afuera.
2. Los endpoints de la API están abiertos: cualquiera con curl puede hacer
   DELETE /api/productos/5 sin estar logueado. Quiero que las operaciones de
   administración exijan ser ADMIN.
3. El login no entrega ninguna credencial de sesión: el navegador solo recuerda
   un dato. Quiero una sesión de verdad.

Usá Spring Security. Antes de escribir código explicame qué cambia para el
frontend (que hoy llama a la API sin mandar ninguna credencial) y cuánto trabajo
es adaptarlo, porque no quiero romper lo que ya funciona.

Al terminar, demostrame con curl que un DELETE sin credenciales es rechazado y
que con credenciales de ADMIN funciona.
```

---

# 3 · Validación de email y contraseña — **HECHO**

Las reglas viven en `frontend/js/modules/validacion.js` (un solo lugar, usado
por el registro, por Mi cuenta → Seguridad y por el alta de usuarios del panel)
y en `UsuarioService`, que es el que de verdad protege la base: lo del
navegador se saltea con cualquier cliente HTTP.

**Contraseña:** mínimo 8 caracteres, una mayúscula, una minúscula y un número.
Los cuatro requisitos se ven debajo del campo *antes* de escribir y se van
marcando en verde al tipear. El error al mandar nombra lo que falta
(«A la contraseña le falta: una mayúscula y un número.»), no la regla entera.

**Email:** se valida la forma, con un mensaje distinto según el error
(falta el @, al dominio le falta el final, tiene espacios, punto de más).

**Hasta dónde llega la validación de email, y qué faltaría:** sólo la forma.
Que el dominio exista necesita una consulta DNS; que la casilla exista sólo lo
sabe el servidor de correo destino, y casi todos se niegan a contestarlo a
propósito; y que la persona sea dueña de esa casilla no lo prueba nada salvo
un correo de confirmación. Eso último necesita una cuenta de envío con la clave
en variable de entorno, dos columnas nuevas en `usuarios` (verificado y un token
con vencimiento), un endpoint de verificación y decidir si se puede comprar sin
verificar. Es la misma infraestructura del tema 16, conviene hacerlos juntos.

**Las claves de demostración pasaron a `Admin123` y `Cliente123`**, para que no
queden anunciadas en la pantalla de ingreso dos claves que el propio formulario
rechazaría. Están actualizadas en la base, en el cartel del ingreso, en
`db/gestor_inventario_alba.sql` y en el README.

---

# 4 · Ir a Inicio al iniciar sesión — **HECHO**

Un CLIENTE que ingresa cae en `index.html` con su nombre ya visible en la
barra de arriba; un ADMIN sigue yendo derecho a `admin.html`. Está en el
submit del formulario de ingreso de `frontend/js/cuenta.js`:

```js
location.href = u.rol === "ADMIN" ? "admin.html" : "index.html";
```

El registro de una cuenta nueva también termina en `index.html`, por el
mismo motivo: quien acaba de crearse la cuenta todavía no tiene ningún
pedido que mirar.

---

# 5 · Perfil del usuario — **HECHO**

`cuenta.html` ya no es solo la lista de pedidos: es un perfil con menú
lateral y cinco secciones, que se eligen por el `#hash` de la dirección
(`cuenta.html#pedidos`), así que cada una tiene su propio enlace.

| Sección | Qué hace |
|---|---|
| Datos principales | Nombre, apellido, email y teléfono, editables |
| Mis pedidos | Lo que ya existía |
| Mis solicitudes de cancelación | Lo que ya existía, ahora como sección propia |
| Preferencias de vino | Varietales y líneas favoritas, **con las recomendaciones** del catálogo que coinciden |
| Seguridad | Cambiar la contraseña, pidiendo la actual |

**Lo que se sumó al back-end:**

- `db/agregar_preferencias_vino.sql` — dos columnas en `usuarios`
  (`varietales_favoritos`, `lineas_favoritas`). **Hay que correrlo en cada
  máquina**, igual que los otros scripts de `db/`.
- `PUT /api/usuarios/{id}/perfil` — el cliente edita sus propios datos.
  Existe aparte del `PUT /api/usuarios/{id}` porque ese aplica `rol` y
  `activo` si vienen en el JSON: puesto detrás de la pantalla del cliente,
  alcanzaba con mandar `rol: "ADMIN"` para autoascenderse.
- `POST /api/usuarios/{id}/password` — exige la contraseña actual. El PUT
  general la guardaba sin preguntar, que no sirve para un formulario de
  cambio de contraseña.

**Lo que NO se hizo, y por qué:** no hay secciones de notificaciones (el
sitio todavía no manda ningún correo: es el tema 16), medios de pago
guardados (la tarjeta del checkout es de prueba) ni datos personales
sueltos. Serían formularios que no hacen nada.

**Lo que quedó afuera y conviene decidir:** una sección de *dirección de
envío*. Hoy el checkout pide la dirección en cada compra
(`tienda.js`) y **no se guarda en ningún lado**: la tabla `pedidos` no
tiene columna para eso, se usa solo para la pantalla de confirmación.
Guardarla en el perfil ahorraría retipearla, pero necesita otra migración.

---

# 6 · Acceso al perfil desde el pie — **HECHO**

La columna "Tu cuenta" del pie (`frontend/js/modules/sitio.js`) lista
**Mi perfil**, **Mis pedidos** y **Mis solicitudes**, cada uno apuntando
directo a su sección (`cuenta.html#datos`, `#pedidos`, `#solicitudes`), más
**Panel de gestión** si es ADMIN.

Las secciones se muestran **con o sin sesión**: el pie es donde se busca qué
ofrece un sitio, y esconderlas hasta después de entrar hace que no se sepa que
existen. A un visitante se le suma arriba **Ingresar o crear cuenta**, y si
toca cualquiera de las secciones cae en el formulario de ingreso — el mismo
camino, pero sabiendo a qué iba.

Funciona porque "Mi cuenta" elige la sección por el `#hash`: estando ya en
`cuenta.html`, tocar un enlace del pie cambia de sección sin recargar.

---

# 7 · Detalle y edición de pedidos en el panel — **HECHO**

Los cuatro modales del panel (detalle de pedido, vino, usuario y pregunta
frecuente) se arman ahora con una sola función, `panelModal()` en
`frontend/js/admin.js`: **cabecera** con el título, **cuerpo** con el contenido
agrupado y **pie** con las acciones separadas por una línea.

**Qué estaba mal, medido en el navegador:**

- el modal no tenía relleno (`padding: 0`), así que el título arrancaba pegado
  al borde y la tabla tocaba los laterales;
- el `<h2>` ocupaba el ancho completo y la **X de cerrar le caía encima**, en
  los cuatro modales;
- los tres datos del cliente ocupaban **188 px** contra **163 px** de la tabla
  de vinos: el bloque más grande era el menos importante;
- el total era una fila más de la tabla, sin fondo ni borde;
- el estado del pedido sólo existía dentro del `<select>`: al abrir el detalle
  se perdía la marca de color que sí tiene la tabla.

**Qué quedó:** el cliente en una fila de tres (188 px → **51 px**), el total en
su propia franja con fondo, el estado como insignia en la cabecera, y los
campos agrupados por tema (Identidad / Venta y stock / Cómo se muestra, y
Datos de contacto / Acceso al sistema).

La X sigue acompañando el scroll (`position: sticky`) porque estos modales
pueden ser más altos que la pantalla; anclada con `absolute` se iba de vista.

**La bandeja de Solicitudes no se tocó:** no es un modal sino una vista de dos
columnas, y ya usaba el patrón correcto (`.panel-titulo` con título e insignia).
De hecho fue de ahí que salió el diseño del resto.

---

# 8 · Sección Usuarios del panel

**Qué pasa hoy:** `vistaClientes` en `js/admin.js` es una tabla con filtro por
rol y botones Editar / Baja por fila.

```
En el panel de Alba en los Andes, la sección Clientes (vistaClientes en
frontend/js/admin.js) es una tabla plana. Quiero mejorar su diseño.

Ideas a evaluar (decime cuáles valen la pena y cuáles son adorno):
- Ver el detalle de un cliente: sus pedidos, cuánto gastó, desde cuándo compra
- Buscador por nombre o email, además del filtro por rol
- Distinguir visualmente a los clientes activos de los dados de baja
- Indicador de clientes nuevos del mes

Mantené el sistema visual del panel. Mostrame una captura del antes y del
después.
```

---

# 9 · Estadísticas del tablero — **HECHO**

Cinco métricas nuevas, elegidas por la decisión que permiten tomar:

| Métrica | Para qué sirve |
|---|---|
| **Ventas por mes**, contra el mes anterior | si el mes viene abajo, salir a mover stock |
| **Vinos más vendidos** | qué reponer primero y qué promocionar |
| **Pedidos cancelados** (%) + el motivo más repetido | el motivo dice cómo arreglarlo: demora, precio o stock |
| **Ticket promedio**, contra el mes anterior | si baja, se compra de a una botella y conviene empujar el combo |
| **Cuánto dura el stock**, en meses de venta | reemplaza con ventaja al "A reponer": el mínimo es un número a mano que no sabe cuánto se vende |

**Los gráficos están dibujados a mano** en `frontend/js/modules/graficos.js`, sin
librerías: el de meses es un SVG con `viewBox` (se achica solo con la tarjeta) y
el ranking son barras de ancho porcentual con elementos normales, para que el
nombre del vino se pueda seleccionar y lo lea bien un lector de pantalla.

**El tablero está organizado en dos mitades** — *Cómo vendo* y *Qué tengo* —
cada una con su título, sus indicadores y una fila de dos paneles de la misma
altura. Lo único que pide una acción (solicitudes sin responder, vinos bajo el
mínimo) va arriba de todo en una franja que sólo aparece cuando hay algo que
hacer.

**Dos métricas se sacaron por redundantes:** "Pedidos del mes" repetía el número
que ya dice la tarjeta de cancelados ("3 de 7 pedidos este mes"), y el panel
"Por qué piden cancelar" ocupaba media fila para mostrar tres motivos — el más
repetido pasó a la misma tarjeta de la tasa, con enlace a la bandeja. Entre eso
y pasar el gráfico de ancho completo a media pantalla, el tablero bajó de
2289px a 1835px de alto sin perder información.

**No hay tortas a propósito:** para tres o cuatro categorías los números con su
porcentaje se comparan mejor que los ángulos, y dibujar arcos a mano cuesta
bastante más. Es el único gráfico que no se justifica con la restricción de SVG
a mano.

**No hizo falta ningún endpoint nuevo.** `/api/pedidos` ya devuelve los
renglones anidados con su producto, y son pocos pedidos: todo se calcula en el
navegador. Haría falta un `GET /api/estadisticas/ventas?desde=&hasta=` recién
cuando traer todos los pedidos enteros para sumar doce números sea un
desperdicio, digamos a partir de unos cientos.

**Lo que se descartó, y por qué:** ventas por línea (se solapa con más
vendidos), clientes nuevos y recurrencia (con 10 usuarios no dice nada),
valorización en el tiempo (linda línea, ninguna decisión), ventas por día u
hora (decorativo a esta escala) y embudo de conversión (**no se puede**: no hay
analítica de visitas, sólo se sabe quién compró).

---

# 10 · Interfaz y distribución de pedidos

```
En el panel de Alba en los Andes, la sección Pedidos (vistaPedidos en
frontend/js/admin.js) es una tabla con filtro por estado.

Quiero repensar cómo se trabaja ahí. Pensalo desde quién lo usa: alguien de la
bodega que entra a la mañana y necesita saber qué pedidos tiene que preparar y
despachar hoy.

Evaluá y proponeme: agrupar por estado en columnas, acciones rápidas sin abrir
el detalle, ver qué vinos hay que juntar para los pedidos del día.

Tené en cuenta una limitación real: la base solo acepta tres estados
(CONFIRMADO, ENTREGADO, CANCELADO). Si para que esto funcione bien hacen falta
estados intermedios como "en preparación" o "enviado", decímelo y explicame qué
habría que cambiar en la base de datos.
```

---

# 11 · Derechos de venta en el pie

**Qué pasa hoy:** el pie tiene solo "Beber con moderación. Prohibida su venta a
menores de 18 años."

```
En el pie de página de Alba en los Andes (frontend/js/modules/sitio.js) quiero
agregar la información legal sobre la venta de vinos en Argentina: la ley que
prohíbe la venta a menores, el aviso de consumo responsable, condiciones de
venta y política de devoluciones.

Buscá qué exige efectivamente la normativa argentina para la venta de alcohol
online y decime qué es obligatorio y qué es recomendable. No inventes números de
ley: si no podés verificarlos, dejalos marcados para que los confirme yo.

Si hace falta una página aparte de Términos y Condiciones además del pie,
proponelo.
```

---

# 12 · Más preguntas frecuentes

**Qué pasa hoy:** hay 17 preguntas en la tabla `preguntas_frecuentes`, todas
atadas al circuito de cancelación. Se administran desde la sección FAQ del panel.

```
En Alba en los Andes, las preguntas frecuentes (tabla preguntas_frecuentes, se
ven al pedir una cancelación) hoy son 17 y están todas enfocadas en cancelar un
pedido.

Quiero ampliarlas a las dudas reales de alguien que compra vino online: envíos y
plazos, formas de pago, qué pasa si llega una botella rota, si se puede regalar,
conservación, si hay compra mínima, facturación.

Escribí las preguntas y respuestas con el tono de la bodega: claro, directo, sin
vueltas. Después dejame el script SQL para cargarlas, o cargalas desde el panel.

Fijate también si conviene una página de Preguntas Frecuentes pública, además de
las que aparecen dentro del formulario de cancelación.
```

---

# 13 · Logo real

**Qué pasa hoy:** el logo es un sello circular dibujado en SVG a mano dentro de
`js/modules/sitio.js`. En `assets/img/sitio/` ya hay `logo-completo.png` y
`logo-sello.png`.

```
En Alba en los Andes el logo es un sello circular que dibujé en SVG, no el logo
real de la bodega. En frontend/assets/img/sitio/ ya hay logo-completo.png y
logo-sello.png.

Quiero reemplazar el SVG por el logo real en el encabezado, el pie y el panel de
administración. Fijate que se vea bien sobre el fondo azul petróleo oscuro y en
pantallas chicas.

Si los archivos que están no sirven (mala resolución, fondo que no es
transparente), decímelo antes de usarlos en vez de forzarlos.
```

---

# 14 · Datos de transferencia y confirmación de pago

```
En el checkout de Alba en los Andes (frontend/js/tienda.js) se elige medio de
pago entre TARJETA, TRANSFERENCIA y EFECTIVO, pero no pasa nada distinto según
cuál se elija.

Quiero que cuando el cliente elija TRANSFERENCIA:
1. Se le muestren los datos bancarios de la bodega (CBU, alias, titular, CUIT)
   con un botón para copiarlos.
2. Se le pida que envíe el comprobante.
3. En el panel, ese pedido aparezca marcado como "esperando comprobante", con
   una casilla para que el administrador tilde la confirmación del pago.

Esto necesita algo que hoy no existe: la tabla pedidos no tiene dónde guardar si
el pago fue confirmado. Proponeme primero qué hay que agregar a la base y al
back-end, y recién después lo implementamos.

Los datos bancarios reales te los paso yo: usá valores de ejemplo marcados como
tales hasta entonces.
```

---

# 15 · QR de pago

```
En Alba en los Andes quiero agregar un QR para el pago electrónico en el
checkout.

Antes de programar nada, explicame las opciones y sé honesto sobre cuál es
realista para un trabajo práctico de facultad:
- Un QR que solo codifica el alias o CBU, para que la persona lo escanee y
  cargue los datos sola
- Integración real con Mercado Pago, que cobra de verdad

Para cada una decime qué hace falta (cuenta, credenciales, si se puede probar
sin dinero real), cuánto trabajo es, y si se puede mostrar funcionando en una
defensa.

Recordá que el proyecto es JavaScript puro sin npm: si generar el QR necesita
una librería, decime cómo se resuelve sin romper esa regla.
```

---

# 16 · Factura por mail

```
En Alba en los Andes, cuando un cliente confirma una compra, el pedido se guarda
en MySQL y se le muestra la confirmación en pantalla. No se le envía nada por
correo.

Quiero que reciba un mail con el detalle de la compra: número de pedido, vinos,
cantidades, precios y total.

Empezá explicándome qué hace falta de verdad: desde dónde sale el correo, si
necesito una cuenta aparte, si se puede probar sin pagar nada, y por qué mandar
mails desde una cuenta personal de Gmail suele terminar en spam.

Después implementalo en el back-end (Spring Boot). La clave de la cuenta de
correo NO puede quedar en el repositorio: tiene que leerse de una variable de
entorno, igual que DB_PASSWORD.

Aclaración: "factura" acá es un comprobante de la compra, no una factura
fiscal con CAE de AFIP. Si querés eso último, decímelo porque es otro tema.
```

---

# 17 · Ingreso con Google

```
En Alba en los Andes el ingreso es con email y contraseña contra
POST /api/usuarios/login. Quiero sumar "Ingresar con Google".

Antes de escribir código explicame:
- Qué hay que crear en Google Cloud Console y si tiene costo
- Cómo funciona el flujo (OAuth) en palabras simples
- Qué pasa con alguien que ya tiene cuenta con el mismo email creada a mano
- Si se puede probar en localhost

Después implementalo. La tabla usuarios hoy exige una contraseña obligatoria al
dar de alta, así que fijate qué hay que cambiar para alguien que entra con
Google y nunca eligió una.

Las credenciales de Google NO pueden quedar en el repositorio.

Conviene hacerlo DESPUÉS del tema 2 (Spring Security), porque se apoya en eso.
```

---

# 18 · Dominios

**No es programar: es averiguar y decidir.**

```
Para el proyecto Alba en los Andes quiero entender el tema de los dominios y el
alojamiento. Hoy todo corre en localhost.

Explicame en términos simples:
- Qué es un dominio y cuánto cuesta uno .com.ar
- Qué necesito para que la página esté en internet y no solo en mi computadora
- Dónde se aloja un back-end de Spring Boot con MySQL, y qué opciones gratuitas
  o baratas hay para un trabajo de facultad
- Si vale la pena para la entrega o si alcanza con mostrarlo en localhost

Dame una recomendación concreta con los pasos, no una lista de opciones sin
elegir.
```

---

## Dos cosas que conviene decidir antes

**El orden importa.** Los temas 2 (seguridad) y 1 (base compartida) cambian cosas
de las que dependen los demás. Hacerlos primero evita trabajo repetido: por
ejemplo, el ingreso con Google (17) se apoya en Spring Security (2).

**Hay cuatro que no dependen solo de programar.** El 16 (mail) necesita una
cuenta de correo, el 17 (Google) credenciales de Google Cloud, el 13 (logo) el
archivo real, y el 1 (base compartida) un lugar donde alojarla. Conviene ir
consiguiendo eso en paralelo para no frenarse después.
