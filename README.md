# Alba en los Andes — Trabajo Integrador de Aplicaciones Interactivas

Sistema de gestión de inventario y venta online para la bodega **Alba en los
Andes** (Tupungato, Valle de Uco, Mendoza).

```
gestor-inventario/
├── backend/     Java: la API, los controladores del sitio, las reglas de negocio
├── frontend/    Lo que ve el cliente: páginas, estilos, imágenes, JavaScript
├── sql/         Los scripts para crear y actualizar la base MySQL
└── docs/        Qué hay en cada archivo, y qué falta hacer
```

→ **[`docs/ESTRUCTURA.md`](docs/ESTRUCTURA.md) explica para qué sirve cada
archivo del proyecto, uno por uno.** Si no sabés dónde buscar algo, empezá ahí.

Las páginas **las arma el servidor**: Spring Boot lee las plantillas de
`frontend/templates/`, las llena con los datos de MySQL y manda el HTML
terminado. No hay un segundo servidor para el frontend, y el navegador no
necesita JavaScript para que el sitio funcione.

---

## Cómo levantar el proyecto

### 1) La base

MySQL corriendo y la base `gestor_inventario` creada con los scripts de
[`sql/`](sql/), **en el orden que indica
[`docs/ESTRUCTURA.md`](docs/ESTRUCTURA.md)**. El primero crea todo:

```bash
mysql -u root -p < sql/gestor_inventario_alba.sql
```

Los `agregar_*.sql` son migraciones: se corren **una sola vez cada una y en
orden**, porque `spring.jpa.hibernate.ddl-auto=none` — Hibernate no toca el
esquema, usa las tablas tal como las dejó el script.

> **Paso a paso detallado, con las teclas de VS Code y qué hacer cuando algo no
> anda: [`docs/COMO-CORRER.md`](docs/COMO-CORRER.md).**
>
> **Para cobrar con Mercado Pago (hace falta una credencial de prueba):
> [`docs/MERCADO-PAGO.md`](docs/MERCADO-PAGO.md).**

### 2) El servidor

```bash
cd backend && ./arrancar.sh
```

`arrancar.sh` saca las claves del correo del llavero de macOS y levanta Spring
Boot. Si tu MySQL no usa la clave por defecto:

```bash
cd backend && DB_PASSWORD="tu-clave" ./arrancar.sh
```

Está listo cuando aparece `Started GestorInventarioApplication`. El log de cada
corrida queda en `backend/arranque.log`.

### 3) El navegador

**http://localhost:8080** — y eso es todo. Algunas direcciones:

| Dirección | Qué es |
|---|---|
| `/` | La portada |
| `/vinos` | El catálogo |
| `/tienda` | La tienda |
| `/cuenta` | Ingresar o crear cuenta |
| `/admin.html` | El panel de administración (todavía el viejo) |

Para probar sin crear una cuenta, los usuarios están a la vista en la pantalla
de ingreso: `admin@albaenlosandes.com / Admin123` y `juan@mail.com / Cliente123`.

---

## Las tres partes, y cómo se hablan

**El sitio web** no hace llamadas a la API: los controladores de
`backend/.../web/` usan los servicios directamente y devuelven HTML. El total de
una compra, el descuento de stock y el precio congelado los calcula el servidor,
nunca el navegador.

**La aplicación de escritorio** (Java Swing, `backend/.../escritorio/`) es otro
programa y habla con el **mismo servidor** por HTTP, contra `/api/**`. Por eso
esa API queda abierta: no es un navegador, no tiene cookie de sesión ni token
CSRF. Está explicado, con el camino para cerrarla, en
`backend/.../config/SecurityConfig.java`.

**El panel de administración** todavía es la página vieja
(`frontend/static/admin.html`) y también le pega a `/api/**`. Es lo único que
falta migrar; está anotado en [`docs/MEJORAS-PENDIENTES.md`](docs/MEJORAS-PENDIENTES.md).

---

## Seguridad

La contraseña de la base de datos **no está en el repositorio**.
`application.properties` la lee de una variable de entorno:

```properties
spring.datasource.password=${DB_PASSWORD:root}
```

Hay que definir `DB_PASSWORD` antes de arrancar (por ejemplo en `~/.zshrc`).

### El recupero de contraseña

Quien se olvidó la contraseña confirma **dos cosas distintas** antes de poder
cambiarla: completa los dígitos tapados del teléfono de la cuenta
(`261-1••••11`), y después escribe un código de 6 números que le llega por mail.

Los dígitos **solos no alcanzan**, y está puesto así a propósito: un teléfono no
es un secreto y cuatro dígitos son diez mil combinaciones. Ese paso confirma
quién dice ser; **lo que autoriza el cambio es el código**, porque para leerlo
hay que entrar a la casilla. Tres intentos por paso y el pedido se quema.

Los recuperos a medias viven **en memoria** (`ServicioRecupero`), duran 15
minutos y no necesitan tabla ni migración. Si se reinicia Spring Boot, los que
estaban por la mitad hay que empezarlos de nuevo.

### El correo saliente

La clave de la casilla sigue el mismo criterio, con una diferencia a
propósito: `DB_PASSWORD` tiene un valor por defecto (`root`) y
`MAIL_PASSWORD` **no tiene ninguno**. Si falta, la aplicación avisa al
arrancar en vez de intentar mandar correos sin credencial y fallar de forma
rara en cada compra.

La bodega manda a traves de **Brevo**, un proveedor de correo
transaccional. No se manda desde una casilla de Gmail directamente: un
servidor casero o un Gmail personal no consiguen que el mensaje entre a la
bandeja de entrada del cliente.

```bash
export MAIL_HOST=smtp-relay.brevo.com
export MAIL_PORT=587
export MAIL_USER=c1542c001@smtp-brevo.com
export MAIL_PASSWORD=xsmtpsib-...      # la clave SMTP, NO va al repositorio
export MAIL_AUTH=true
export MAIL_TLS=true
export MAIL_FROM=theolambert.ar@gmail.com
```

`MAIL_AUTH` y `MAIL_TLS` valen `false` por defecto porque el servidor de
prueba local no los usa. Brevo exige los dos: sin ellos la conexion se
rechaza.

`MAIL_FROM` **tiene que ser una direccion verificada en Brevo** (Settings →
Senders). Si se pone cualquier otra, Brevo rechaza el envio entero.

Para desarrollar sin cuenta en ningún proveedor alcanza con un servidor SMTP
de prueba en la propia máquina (`MAIL_HOST=localhost`, `MAIL_PORT=1025`), que
es la configuración por defecto. Y `MAIL_ACTIVO=false` apaga el envío sin
desarmar nada.

**Por qué el remitente no es `pedidos@albaenlosandes.com`:** ese dominio no
es nuestro. SPF y DKIM fallan, DMARC marca el mensaje como falsificado y
termina en spam o rechazado. Por eso hoy el remitente es una casilla de
Gmail verificada en Brevo: el mail llega, aunque algunos clientes muestren
un *"via brevo.com"* al lado del nombre.

El día que se compre `albaenlosandes.com` se cargan los DNS que da Brevo
(Settings → Domains) y recién ahí se puede mandar como
`pedidos@albaenlosandes.com`. No hay que tocar código: alcanza con cambiar
`MAIL_FROM`.

### El Client ID de Google

`alba.google.client-id` **sí está escrito en el repositorio**, y está bien:
un Client ID es público por diseño, viaja en el HTML y lo ve cualquiera que
abra el sitio. Lo que no aparece en ningún lado es el *Client Secret*, y no
hace falta: el flujo de "Sign in with Google" por ID token no lo usa.

Ahora se escribe **en un solo lugar**, `application.properties`: de ahí lo lee
el servicio que valida el token y de ahí lo saca también la pantalla de ingreso
para dibujar el botón. Antes estaba además escrito a mano en el JavaScript, y
dos copias de lo mismo terminan separándose.

En Google Cloud hay que tener cargado `http://localhost:8080` en *Authorized
JavaScript origins* — y tal cual: `127.0.0.1` y `localhost` son orígenes
distintos para Google, aunque sean la misma máquina. El origen de la página se
escribe en la consola del navegador al abrir el ingreso, para copiarlo y
pegarlo.
