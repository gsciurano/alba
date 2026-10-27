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
| 3 | Validación de email y contraseña | Baja | No |
| 4 | Ir a Inicio al iniciar sesión | Muy baja | No |
| ~~5~~ | ~~Perfil del usuario~~ **HECHO** | Media | No |
| 6 | Acceso al perfil desde el pie | Muy baja | No |
| 7 | Detalle y edición de pedidos en el panel | Media | No |
| 8 | Sección Usuarios del panel | Media | No |
| 9 | Estadísticas del tablero | Media | No |
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

# 3 · Validación de email y contraseña

**Qué pasa hoy:** en `js/cuenta.js` el registro valida el email con una
expresión mínima (`algo@algo.algo`) y solo exige 6 caracteres de contraseña. No
se verifica que el correo exista de verdad.

```
En el registro de Alba en los Andes (frontend/js/cuenta.js) quiero endurecer la
validación:

- El email tiene que tener una estructura válida de verdad, no la comprobación
  mínima de hoy. Explicame hasta dónde se puede validar en el navegador y qué
  requiere mandar un correo de confirmación.
- La contraseña tiene que exigir: mínimo 8 caracteres, al menos una mayúscula,
  una minúscula y un número. Mostrá los requisitos en pantalla ANTES de que la
  persona escriba, y marcá cuáles va cumpliendo mientras tipea.
- La misma validación tiene que estar también en el back-end (UsuarioService),
  porque la del navegador se puede saltear.

Mostrame los mensajes de error que va a ver el usuario antes de implementarlos.
```

---

# 4 · Ir a Inicio al iniciar sesión

**Qué pasa hoy:** en `js/cuenta.js:118`, un ADMIN va a `admin.html` y un CLIENTE
a `cuenta.html`.

```
En Alba en los Andes, cuando un CLIENTE inicia sesión queda en cuenta.html
(Mis pedidos). Quiero que vaya a la página de inicio (index.html), con su nombre
ya visible arriba.

El ADMIN tiene que seguir yendo directo al panel (admin.html), eso no cambia.

Está en frontend/js/cuenta.js, en el submit del formulario de ingreso.
```

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

# 6 · Acceso al perfil desde el pie

**Qué pasa hoy:** en `js/modules/sitio.js:126` el pie tiene una columna "Tu
cuenta" con un solo enlace: "Mis pedidos" (o "Ingresar o crear cuenta").

```
En el pie de página de Alba en los Andes (frontend/js/modules/sitio.js, la
columna "Tu cuenta") hay un solo enlace. Quiero que para alguien con la sesión
iniciada muestre enlaces separados a: Mi perfil, Mis pedidos y Mis solicitudes,
cada uno llevando directo a su sección.

Para un visitante sin sesión, que siga mostrando "Ingresar o crear cuenta".

Depende de que exista el perfil (tema 5), así que hacelo después de ese.
```

---

# 7 · Detalle y edición de pedidos en el panel

**Qué pasa hoy:** el detalle de un pedido se abre en un modal armado en
`js/admin.js` (`abrirDetallePedido`). Según la observación, la información
queda amontonada.

```
En el panel de Alba en los Andes (frontend/js/admin.js), cuando abro el detalle
de un pedido o edito algo, la información queda toda pegada y mal organizada.

Quiero que lo revises y lo rediseñes: que haya jerarquía visual clara entre los
datos del cliente, los renglones del pedido, los totales y las acciones.
Respetá el sistema visual que ya tiene el panel (css/admin.css): barra oscura,
tarjetas blancas, azul petróleo de la marca.

Antes de cambiar nada, abrí el panel en el navegador, sacá una captura del
detalle de un pedido y mostrame qué está mal puntualmente. Después proponé el
diseño nuevo.

Lo mismo para los otros formularios de edición del panel.
```

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

# 9 · Estadísticas del tablero

**Qué pasa hoy:** el tablero tiene 6 indicadores: Vinos en catálogo, Botellas en
stock, Valorización, A reponer, Pedidos del mes, Solicitudes sin responder.
Todos son números sueltos: no hay ningún gráfico ni comparación contra el mes
anterior.

```
El tablero del panel de Alba en los Andes (vistaTablero en
frontend/js/admin.js) tiene seis números sueltos. Quiero sumarle los
indicadores que cualquier e-commerce necesita de verdad.

Proponeme primero una lista de métricas y gráficos ordenada por utilidad real
para una bodega, explicando qué decisión permite tomar cada uno. Por ejemplo:
ventas por mes, vinos más vendidos, ticket promedio, comparación contra el
período anterior. Descartá lo que sea decorativo.

Después implementá los que acordemos. Condición importante: el proyecto es
HTML + CSS + JavaScript puro, sin npm ni frameworks, así que los gráficos hay
que dibujarlos con SVG o canvas a mano. Si alguno no se justifica con esa
restricción, decímelo.

Los datos salen de la API: /api/pedidos, /api/productos y /api/movimientos.
Fijate si hace falta algún endpoint nuevo en el back-end.
```

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
