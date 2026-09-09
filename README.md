# Gestor de Inventario — Alba en los Andes

Back-end del trabajo integrador de Aplicaciones Interactivas (Etapa 1).

Es una API REST para manejar el inventario y las ventas de la bodega Alba en
los Andes. El catálogo que trae cargado no es inventado: son los diez vinos
reales de la bodega, con los precios, varietales y notas de cata sacados de
https://albaenlosandes.com.

La Etapa 2 va a ser la página web que consuma esta API. Acá está solamente el
servidor.


## Qué hace falta para correrlo

* Java 17 o más nuevo (nosotros compilamos con 26). Se chequea con `java -version`.
* MySQL 8 andando en `localhost:3306`.

Maven no hace falta instalarlo: el proyecto trae el wrapper (`mvnw`), así que
alcanza con usarlo desde la carpeta del proyecto.


## Cómo levantarlo

**Primero la base de datos.** Son tres scripts y van en este orden. El primero
crea todo de cero y carga el catálogo; los otros dos agregan las tablas de las
funcionalidades que sumamos después.

```bash
cd src/main/resources/db
mysql -u root -p < gestor_inventario_alba.sql
mysql -u root -p < agregar_solicitudes_cancelacion.sql
mysql -u root -p < agregar_preguntas_frecuentes.sql
```

OJO con el primero: arranca con `DROP DATABASE IF EXISTS gestor_inventario`.
Si ya tenés la base cargada con pedidos que te importan, no lo corras.

Cuando termina tienen que quedar siete tablas: `usuarios`, `productos`,
`pedidos`, `detalle_pedido`, `movimientos_stock`, `solicitudes_cancelacion` y
`preguntas_frecuentes`.

**Después la contraseña.** No está escrita en ningún archivo del proyecto, se
lee de una variable de entorno. Así cada uno usa la suya y no queda la de nadie
subida al repositorio:

```bash
export DB_PASSWORD=tu_password        # Mac y Linux
setx DB_PASSWORD tu_password          # Windows
```

Si no la definís asume `root`. El usuario también se puede cambiar, con
`DB_USER`.

> Si arrancás desde el IDE en vez de la terminal, la variable hay que cargarla
> en la configuración de ejecución, porque el IDE no hereda el `export` de la
> consola.

**Y ahora sí:**

```bash
./mvnw spring-boot:run
```

Para probar que quedó andando, `curl http://localhost:8080/api/productos` o
directamente esa dirección en el navegador.


## Cómo está organizado

Lo primero: ningún cliente toca la base de datos. Todo va por HTTP.

    Cliente  ──HTTP/JSON──>  Back-end  ──JDBC──>  MySQL

Adentro del back-end hay cuatro capas y cada una solo le habla a la de abajo:

`controller/` son los `@RestController`. Reciben la petición HTTP, se fijan que
el formato esté bien y devuelven JSON. No deciden nada del negocio.

`service/` son los `@Service`. Acá viven las reglas: validar el stock, calcular
el total, decidir si una cancelación se aprueba sola. Es el cerebro.

`repository/` son interfaces que extienden `JpaRepository`. Definen qué se le
puede pedir a la base; la implementación la escribe Spring Data sola.

`model/` son las `@Entity`, las clases que se mapean a las tablas, más los
enums del dominio.

**¿Por qué separarlo así y no meter todo junto?** Porque si mañana cambiamos
MySQL por PostgreSQL hay que tocar la configuración y a lo sumo alguna
`@Query`; los Service y los Controller ni se enteran. Y porque cuando en la
Etapa 2 aparezca la página web, no vamos a tener que reescribir ni una regla:
ya están del lado del servidor, no de la interfaz.

### Sobre que no sea una solución monolítica

Esto es una condición del TPO, así que vale aclararlo. El servidor no sabe ni
le importa quién lo llama. Durante el desarrollo escucha en `localhost:8080`,
pero con cambiar la dirección en el cliente el servidor puede estar en otra
máquina de la red y todo sigue funcionando igual.

Para poder mostrarlo en la defensa dejamos el cliente de escritorio (más abajo),
que recibe la dirección del servidor por parámetro.


## La API

Todo cuelga de `http://localhost:8080` y todo va y viene en JSON.

### Productos

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/productos` | El catálogo: los vinos activos |
| GET | `/api/productos/{id}` | Uno solo |
| GET | `/api/productos/linea/{linea}` | Por línea: `FINCA`, `ESTATE_RESERVE`, `GRAN_RESERVA` o `EDICION_ESPECIAL` |
| GET | `/api/productos/buscar?nombre=malbec` | El buscador |
| GET | `/api/productos/precio?desde=20000` | De ese precio para arriba |
| GET | `/api/productos/alertas` | Los que están por debajo del stock mínimo |
| POST | `/api/productos` | Alta |
| PUT | `/api/productos/{id}` | Modificación |
| DELETE | `/api/productos/{id}` | Baja lógica: pone `activo = false`, no borra la fila |
| POST | `/api/productos/{id}/ingreso?cantidad=24&idUsuario=1&motivo=texto` | Entra mercadería: sube el stock y deja el movimiento `ENTRADA` |

### Usuarios

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/usuarios` | Todos |
| GET | `/api/usuarios/{id}` | Uno |
| POST | `/api/usuarios` | Registro. Si el email ya existe, lo rechaza |
| PUT | `/api/usuarios/{id}` | Modificación |
| DELETE | `/api/usuarios/{id}` | Baja lógica, para no perder su historial de compras |

### Pedidos

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/pedidos` | Todos |
| GET | `/api/pedidos/{id}` | El comprobante de uno |
| GET | `/api/pedidos/usuario/{idUsuario}` | El historial de un cliente |
| POST | `/api/pedidos` | El checkout |
| PUT | `/api/pedidos/{id}/estado?valor=ENTREGADO` | Cambia el estado. Si va `CANCELADO`, devuelve el stock solo |

### Movimientos de stock

Estos dos son de solo lectura a propósito: es el registro de trazabilidad, no
se edita a mano.

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/movimientos` | Todo lo que pasó con el inventario |
| GET | `/api/movimientos/producto/{id}` | El historial de un vino puntual |

### Solicitudes de cancelación

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/solicitudes` | Todas |
| GET | `/api/solicitudes/{id}` | Una |
| GET | `/api/solicitudes/usuario/{idUsuario}` | "Mis solicitudes", el seguimiento del cliente |
| GET | `/api/solicitudes/pedido/{idPedido}` | Las de un pedido |
| GET | `/api/solicitudes/pendientes` | La bandeja del administrador |
| GET | `/api/solicitudes/evaluar?idPedido=1&idUsuario=2&motivo=ARREPENTIMIENTO` | Le avisa al cliente qué va a pasar antes de que confirme. No guarda nada |
| POST | `/api/solicitudes` | Pedir la cancelación en firme |
| PUT | `/api/solicitudes/{id}/resolver?aprobar=true&idAdmin=1` | El admin aprueba o rechaza |

El `evaluar` es el que más nos gustó: en vez de que el cliente confirme y
después vea qué pasó, el formulario le puede decir de antemano "esto se cancela
al instante" o "esto va a revisión".

### Preguntas frecuentes

La idea es que cuando el cliente elige el motivo de su problema, el formulario
le muestre las preguntas de ese motivo y muchas veces lo resuelva ahí, sin
tener que mandar ninguna solicitud. Están en la base y no escritas en el código
para que la bodega pueda corregir un texto sin que nadie recompile.

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/faq` | Las activas |
| GET | `/api/faq/todas` | Incluye las dadas de baja (panel de gestión) |
| GET | `/api/faq/motivo/{motivo}` | Las de ese motivo más las generales |
| GET | `/api/faq/{id}` | Una |
| POST | `/api/faq` | Alta |
| PUT | `/api/faq/{id}` | Modificación |
| DELETE | `/api/faq/{id}` | Baja lógica |


## Ejemplos de cuerpo

El checkout, `POST /api/pedidos`. Fijarse que no se manda ni el precio ni el
total: eso lo calcula el servidor y lo congela.

```json
{
  "usuario":   { "idUsuario": 2 },
  "medioPago": "TARJETA",
  "detalles": [
    { "producto": { "idProducto": 2 }, "cantidad": 2 },
    { "producto": { "idProducto": 5 }, "cantidad": 1 }
  ]
}
```

Un alta de producto, `POST /api/productos`:

```json
{
  "nombre": "Gran Reserva Merlot",
  "linea": "GRAN_RESERVA",
  "varietal": "Merlot",
  "precio": 25000,
  "stockActual": 60,
  "stockMinimo": 12
}
```

Una solicitud de cancelación, `POST /api/solicitudes`:

```json
{
  "idPedido": 3,
  "idUsuario": 2,
  "motivo": "ARREPENTIMIENTO",
  "comentario": "Me equivoqué de varietal"
}
```


## Los errores

Todos salen con la misma forma, así el que consume la API no tiene que tratar
ninguno como caso especial. Los arma una sola clase,
[GlobalExceptionHandler](src/main/java/com/albaenlosandes/inventario/controller/GlobalExceptionHandler.java),
anotada con `@RestControllerAdvice`, que es como decirle a Spring "vigilá a
todos los controllers".

```json
{
  "timestamp": "2026-09-09T14:21:21.588",
  "estado": 404,
  "error": "No existe el producto con id 9999",
  "ruta": "/api/productos/9999"
}
```

El criterio de códigos es este:

* **400** — el cliente mandó algo mal. Falta un dato, el formato no sirve, el
  enum no existe, o se violó una regla (querer comprar más botellas de las que
  hay).
* **404** — pidió por id algo que no está.
* **409** — el dato es válido pero la base lo rechaza: un email repetido, o
  borrar algo que otra tabla está usando.
* **500** — se rompió algo nuestro. Es el único que es culpa del servidor.

Cuando lo que falla es la validación de un formulario, la respuesta trae además
un campo `campos` con **todos** los errores juntos, no solo el primero. Eso es a
propósito: así el front puede marcar los cinco campos mal cargados de una vez y
el usuario no los descubre de a uno.

```json
{
  "estado": 400,
  "error": "Hay 2 campos con errores",
  "campos": {
    "nombre": "El nombre es obligatorio",
    "precio": "El precio es obligatorio"
  },
  "ruta": "/api/productos"
}
```


## Las reglas de negocio

Esta es la parte que más nos importa, porque es lo que hace que el sistema sea
un sistema y no un CRUD.

**Inventario perpetuo.** Ninguna botella se mueve sin dejar rastro. Toda
variación de stock escribe su fila en `movimientos_stock`, con tipo `ENTRADA`,
`SALIDA` o `AJUSTE`. Si el stock de un vino no cierra, se puede reconstruir
qué pasó.

**El checkout es transaccional.** Valida el stock, guarda el precio del momento
en `detalle_pedido` y descuenta. Va anotado `@Transactional` porque toca tres
tablas: si falla a mitad de camino, no puede quedar el pedido guardado con el
stock sin descontar.

**Cancelar devuelve el stock**, y deja su movimiento `AJUSTE` registrado.

**El precio queda congelado.** `detalle_pedido` guarda cuánto salía el vino el
día de la compra. Si mañana sube, el pedido viejo sigue diciendo lo que
realmente se pagó.

**La política de cancelación.** Si el pedido tiene menos de 24 horas y el motivo
es de los simples, se cancela solo y el cliente no espera a nadie. Si no, pasa a
que lo mire una persona. La pusimos en su propia clase,
[PoliticaCancelacion](src/main/java/com/albaenlosandes/inventario/service/PoliticaCancelacion.java),
justamente porque es la regla que más va a cambiar: hoy son 24 horas, mañana
pueden ser 48 o depender del monto. Así se cambia sin tocar nada más.

**Solo el dueño** de un pedido puede pedir que se cancele, y **solo un ADMIN**
puede resolverlo. Y no se puede mandar la misma solicitud cinco veces: hay una
sola abierta por pedido.

**Las bajas son lógicas.** Productos y usuarios se marcan `activo = false` en
vez de borrarse. Un DELETE de verdad rompería las claves foráneas de todos los
pedidos históricos.


## El cliente de escritorio

En `escritorio/` hay una aplicación hecha en Java Swing que consume esta misma
API. **No es el front-end de la Etapa 2** — ese va a ser HTML5, CSS3 y
JavaScript. Está para poder mostrar en la defensa que el back-end atiende
clientes externos por HTTP y que se banca estar en otra máquina.

```bash
# contra el servidor local
java -cp target/classes com.albaenlosandes.inventario.escritorio.AppEscritorio

# contra un servidor que corre en otra computadora
java -cp target/classes com.albaenlosandes.inventario.escritorio.AppEscritorio http://192.168.0.15:8080
```

La única clase de toda la ventana que sabe que existe una red es `ApiCliente`.
Ningún panel escribe una URL ni tiene reglas adentro: solo piden y muestran.


## Los datos que vienen cargados

Tres usuarios:

| id | Nombre | Email | Rol |
|---|---|---|---|
| 1 | Admin Bodega | admin@albaenlosandes.com | ADMIN |
| 2 | Juan Pérez | juan.perez@mail.com | CLIENTE |
| 3 | Ana García | ana.garcia@mail.com | CLIENTE |

Y los diez vinos, repartidos en las cuatro líneas de la bodega: Finca
($15.000), Estate Reserve ($18.000), Gran Reserva ($25.000) y Edición Especial
($35.000).


## Dónde está cada cosa

    src/main/java/com/albaenlosandes/inventario/
      GestorInventarioApplication.java    el main del servidor (@SpringBootApplication)
      model/         7 entidades y 7 enums
      repository/    6 interfaces JpaRepository
      service/       6 servicios con las reglas, más la política de cancelación
      controller/    6 controllers y el manejador global de errores
      escritorio/    el cliente Swing (no forma parte de la Etapa 1)

    src/main/resources/
      application.properties    la conexión a MySQL y la configuración de JPA
      db/                       los tres scripts, en el orden en que van

Sobre las tecnologías: Spring Boot 3.5.16, Spring Data JPA con Hibernate de ORM,
MySQL 8, Bean Validation para las validaciones de entrada y JSON como formato de
intercambio.
