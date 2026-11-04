# Alba en los Andes — Trabajo Integrador de Aplicaciones Interactivas

Sistema de gestión de inventario y venta online para la bodega **Alba en los
Andes** (Tupungato, Valle de Uco, Mendoza).

El trabajo tiene dos etapas, y cada una vive en su propia carpeta:

```
alba/
├── backend/     Etapa 1 — API REST con Spring Boot + MySQL
└── frontend/    Etapa 2 — Tienda web con HTML5 + CSS3 + JavaScript
```

Las dos partes **no son independientes**: el frontend no tiene datos propios,
los pide al backend. Si el backend está apagado, la tienda muestra un aviso de
que no puede conectarse.

---

## Qué hay en cada carpeta

### `backend/` — Etapa 1

API REST hecha con **Spring Boot 3.5.16** sobre **MySQL**, con JPA/Hibernate
como ORM. Expone los endpoints que consume la tienda y contiene toda la lógica
de negocio: validación de stock, congelado de precios, baja lógica y
trazabilidad de los movimientos de inventario.

Incluye además un cliente de escritorio en **Java Swing** (`src/.../escritorio/`)
que consume esos mismos endpoints, usado para la defensa de la Etapa 1.

→ Ver [`backend/README.md`](backend/README.md) para el detalle de endpoints,
entidades y decisiones de diseño.

### `frontend/` — Etapa 2

Tienda pública + panel de administración, en **HTML5, CSS3 y JavaScript puro
(módulos ES)**. Sin frameworks, sin npm, sin paso de compilación: son archivos
`.html`, `.css` y `.js` que se sirven tal cual.

→ Ver [`frontend/README.md`](frontend/README.md) para la estructura, el mapeo
de requisitos y las limitaciones conocidas.

---

## Cómo levantar el proyecto completo

Hacen falta **dos terminales**, porque son dos servidores distintos.

**1) La base de datos**

MySQL tiene que estar corriendo y la base `gestor_inventario` creada. Los
scripts están en `backend/src/main/resources/db/`.

El script base es `gestor_inventario_alba.sql`. Los `agregar_*.sql` son
migraciones que se corren **una vez cada una y en orden**, porque
`spring.jpa.hibernate.ddl-auto=none`: Hibernate no toca el esquema, usa las
tablas tal como las dejó el script. Las últimas dos:

```bash
mysql -u root -p gestor_inventario < backend/src/main/resources/db/agregar_pago_transferencia.sql
mysql -u root -p gestor_inventario < backend/src/main/resources/db/agregar_ingreso_con_google.sql
```

**2) El backend** (puerto 8080)

```bash
cd backend && DB_PASSWORD="tu-clave" ./mvnw spring-boot:run
```

Para comprobar que arrancó bien:

```bash
curl http://localhost:8080/api/productos
```

Tiene que devolver la lista de vinos en JSON. Si devuelve un error 500, casi
siempre es que `DB_PASSWORD` está mal: la aplicación arranca igual, pero no
puede entrar a la base.

**3) El frontend** (puerto 8099)

```bash
cd frontend && python3 -m http.server 8099
```

Y después, en el navegador:

- Tienda: http://localhost:8099/index.html
- Administración: http://localhost:8099/admin.html

Hace falta servirlo con un servidor: abrir el `index.html` con doble clic no
funciona, porque los módulos de JavaScript están bloqueados bajo `file://`.

---

## Cómo se comunican las dos partes

El frontend habla con el backend por HTTP, en un solo archivo:
`frontend/js/modules/api.js`. Ahí está la dirección del servidor
(`http://localhost:8080/api`) y la traducción entre los nombres que usa cada
lado (por ejemplo `idProducto` ↔ `id`, `anada` ↔ `cosecha`).

Esto significa que si el backend cambia de puerto o de servidor, se toca **un
solo archivo** y el resto de la tienda no se entera.

La lógica de negocio no está duplicada: al confirmar una compra el navegador
manda solamente qué vino y cuántas botellas. El total, el precio congelado, el
descuento de stock y el registro del movimiento los calcula el backend.

---

## Seguridad

La contraseña de la base de datos **no está en el repositorio**.
`application.properties` la lee de una variable de entorno:

```properties
spring.datasource.password=${DB_PASSWORD:root}
```

Hay que definir `DB_PASSWORD` antes de arrancar (por ejemplo en `~/.zshrc`).

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

Tiene que ser **el mismo** en `application.properties` y en `js/cuenta.js`:
el back-end comprueba que el token venga emitido para ese client id, así que
si no coinciden se rechazan todos los ingresos. En Google Cloud hay que
tener cargado `http://localhost:8099` en *Authorized JavaScript origins*.
