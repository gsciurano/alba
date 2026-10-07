# Gestor de Inventario — Alba en los Andes

Back-end del trabajo integrador de Aplicaciones Interactivas (Etapa 1).

Es una API REST para manejar el inventario y las ventas de la bodega Alba en
los Andes. El catálogo que trae cargado no es inventado: son los diez vinos
reales de la bodega, con los precios, varietales y notas de cata sacados de
https://albaenlosandes.com.


## Cómo está organizado

Lo primero: ningún cliente toca la base de datos. Todo va por HTTP.

    Cliente  ──HTTP/JSON──>  Back-end  ──JDBC──>  MySQL

Adentro del back-end hay cuatro capas y cada una solo le habla a la de abajo:

Controller son los `@RestController`. Reciben la petición HTTP, se fijan que
el formato esté bien y devuelven JSON. No deciden nada del negocio.

Service son los `@Service`. Acá viven las reglas: validar el stock, calcular
el total, decidir si una cancelación se aprueba sola. Es el cerebro.

Repository son interfaces que extienden `JpaRepository`. Definen qué se le
puede pedir a la base; la implementación la escribe Spring Data sola.

Model son las `@Entity`, las clases que se mapean a las tablas, más los
enums del dominio.

**¿Por qué separarlo así y no meter todo junto?** Porque si mañana cambiamos
MySQL por PostgreSQL hay que tocar la configuración y a lo sumo alguna
`@Query`; los Service y los Controller ni se enteran. Y porque cuando en la
Etapa 2 aparezca la página web, no vamos a tener que reescribir ni una regla:
ya están del lado del servidor, no de la interfaz.

### Sobre que no sea una solución monolítica
 El servidor no sabe ni le importa quién lo llama. Durante el desarrollo escucha en `localhost:8080`,
pero con cambiar la dirección en el cliente el servidor puede estar en otra
máquina de la red y todo sigue funcionando igual.

## La API

Todo cuelga de `http://localhost:8080` y todo va y viene en JSON.

# Productos

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

# Usuarios

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/usuarios` | Todos |
| GET | `/api/usuarios/{id}` | Uno |
| POST | `/api/usuarios` | Registro. Si el email ya existe, lo rechaza |
| PUT | `/api/usuarios/{id}` | Modificación |
| DELETE | `/api/usuarios/{id}` | Baja lógica, para no perder su historial de compras |

# Pedidos

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/pedidos` | Todos |
| GET | `/api/pedidos/{id}` | El comprobante de uno |
| GET | `/api/pedidos/usuario/{idUsuario}` | El historial de un cliente |
| POST | `/api/pedidos` | El checkout |
| PUT | `/api/pedidos/{id}/estado?valor=ENTREGADO` | Cambia el estado. Si va `CANCELADO`, devuelve el stock solo |
| DELETE | `/api/pedidos/{id}` | Baja lógica. Si estaba `CONFIRMADO` lo cancela primero, así el stock vuelve antes de archivarlo |

Los estados posibles son `CONFIRMADO`, `ENTREGADO` y `CANCELADO`, y los medios
de pago `TARJETA`, `TRANSFERENCIA` y `EFECTIVO`.

# Renglones de un pedido

Un pedido ya confirmado se puede corregir sin darlo de baja y volver a
cargarlo: se le agrega un vino, se le cambia una cantidad o se le saca uno.
Cada una de estas operaciones ajusta el stock, deja su movimiento y recalcula
el total del pedido.

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/detalles/pedido/{idPedido}` | Los renglones de un pedido |
| GET | `/api/detalles/{id}` | Un renglón |
| POST | `/api/detalles?idPedido=3&idProducto=5&cantidad=2` | Le agrega un vino. Si ese vino ya estaba, suma al renglón que existe en vez de repetirlo |
| PUT | `/api/detalles/{id}?cantidad=5` | Cambia la cantidad y ajusta el stock por la diferencia |
| DELETE | `/api/detalles/{id}` | Saca el vino del pedido y le devuelve el stock |

# Movimientos de stock

Acá el CRUD está, pero no funciona como en las otras entidades, y es a
propósito: esto es el libro de trazabilidad del inventario. Un movimiento ya
escrito no se reescribe ni se borra, porque el stock de cada vino tiene que
seguir siendo la suma de sus movimientos.

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/movimientos` | Todo lo que pasó con el inventario, sin los anulados |
| GET | `/api/movimientos/auditoria` | Lo mismo pero incluyendo los anulados y sus contra-asientos |
| GET | `/api/movimientos/{id}` | Uno solo |
| GET | `/api/movimientos/producto/{id}` | El historial de un vino puntual |
| POST | `/api/movimientos?idProducto=5&cantidad=-3&motivo=Rotura&idUsuario=1` | Ajuste manual: el conteo físico del depósito. La cantidad puede ser negativa (faltan botellas) o positiva (aparecieron), nunca cero, y el motivo es obligatorio |
| PUT | `/api/movimientos/{id}?motivo=Devolución de un cliente` | Corrige **solo** la redacción del motivo. La cantidad, el tipo, el producto y la fecha no se tocan |
| DELETE | `/api/movimientos/{id}?idUsuario=1&motivo=Se cargó dos veces` | No borra: **anula**. Marca el original como inactivo, crea uno nuevo con la cantidad opuesta enlazado a él y corrige el stock |

Ese DELETE es el único de toda la API que contesta **201** en vez de 204, y
tiene sentido: la operación crea una fila nueva —el contra-asiento— y esa fila
es lo que devuelve. Los tipos de movimiento son `ENTRADA`, `SALIDA` y `AJUSTE`.

# Solicitudes de cancelación

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
| DELETE | `/api/solicitudes/{id}` | Baja lógica, y solo si ya está resuelta: dar de baja una abierta dejaría al cliente esperando una respuesta que no va a llegar |

El `evaluar` es el que más nos gustó: en vez de que el cliente confirme y
después vea qué pasó, el formulario le puede decir de antemano "esto se cancela
al instante" o "esto va a revisión".

# Preguntas frecuentes

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

#### Qué falta de esta parte

El circuito de reclamos es el único que **no está terminado**, y es a propósito.
Lo diseñamos pensando en la pantalla de la Etapa 2: la idea es que el cliente
elija el motivo, el formulario le muestre las preguntas de ese motivo y recién
si ninguna lo resuelve, mande la solicitud. Esa conversación de ida y vuelta
vive en el front, así que lo que hicimos en la Etapa 1 fue dejar el lado del
servidor listo para cuando exista: los endpoints, la política de las 24 horas y
el `evaluar`, que es justamente el que le va a contestar al formulario qué va a
pasar antes de que el cliente confirme.

Lo que está cargado en la base es de prueba. Las preguntas frecuentes son
textos nuestros para poder mostrar el circuito funcionando, no los definitivos
de la bodega, y las solicitudes de `agregar_datos_demo.sql` están ahí para que
la bandeja del administrador no aparezca vacía en la defensa. Cuando la bodega
escriba sus propias respuestas no hay que recompilar nada: las preguntas están
en la base por eso mismo.


## Ejemplos de cuerpo

El checkout, `POST /api/pedidos`. Fijarse que no se manda ni el precio ni el
total: eso lo calcula el servidor y lo congela.


## Los errores

Todos salen con la misma forma, así el que consume la API no tiene que tratar
ninguno como caso especial. Los arma una sola clase,
[GlobalExceptionHandler](src/main/java/com/albaenlosandes/inventario/controller/GlobalExceptionHandler.java),
anotada con `@RestControllerAdvice`, que es como decirle a Spring "vigilá a
todos los controllers".


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
      repository/    7 interfaces JpaRepository
      service/       7 servicios con las reglas, más la política de cancelación
      controller/    7 controllers y el manejador global de errores
      escritorio/    el cliente Swing (no forma parte de la Etapa 1)

    src/main/resources/
      application.properties    la conexión a MySQL y la configuración de JPA
      db/                       los cinco scripts, en el orden en que van, más
                                uno alternativo para una base ya creada

Sobre las tecnologías: Spring Boot 3.5.16, Spring Data JPA con Hibernate de ORM,
MySQL 8, Bean Validation para las validaciones de entrada y JSON como formato de
intercambio.
