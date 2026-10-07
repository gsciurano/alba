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
