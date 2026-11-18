# Prompt de auditoría funcional

Para pegar en una sesión nueva. Busca inconsistencias en los tres frentes
(sitio web, API, Swing) y además marca qué falta.

Está partido en cuatro tandas. **Pegá una por vez** y esperá el informe antes de
seguir: si va todo junto, la sesión se queda sin contexto a mitad de camino y el
final sale peor que el principio.

---

## Antes de empezar

El auditor necesita el proyecto corriendo:

```bash
cd ~/Downloads/gestor-inventario/backend && ./arrancar.sh
```

Y acceso a la base para verificar:

```bash
source ~/.zshrc
MYSQL_PWD="$DB_PASSWORD" /usr/local/mysql/bin/mysql -u root -D gestor_inventario
```

---

# Tanda 1 · Mapa y contradicciones entre los dos caminos

```
Proyecto Alba en los Andes, en ~/Downloads/gestor-inventario. Es un trabajo
integrador de facultad: tienda de vinos con Spring Boot + MySQL, páginas
Thymeleaf, y además una aplicación de escritorio en Java Swing.

Quiero una auditoría funcional. Esta primera tanda es solo de lectura: no
cambies nada todavía.

EL DATO QUE IMPORTA: hay DOS caminos que escriben en la misma base.
  1. backend/.../web/         controladores Thymeleaf, con sesión. Lo que usa
                              el cliente desde el navegador.
  2. backend/.../controller/  la API /api/**, abierta sin autenticación. La
                              usan la app Swing Y el panel viejo
                              (frontend/static/admin.html).

Cuando dos caminos hacen lo mismo por separado, las reglas se separan. Ese es
el primer lugar donde buscar.

QUÉ QUIERO QUE HAGAS:

1. Mapeá cada operación de negocio (crear pedido, cancelar, cambiar estado,
   dar de baja un producto, ingresar stock, alta de usuario, resolver una
   solicitud) e indicá por qué camino o caminos se puede hacer.

2. Para cada operación que exista en LOS DOS caminos, compará línea por línea
   qué valida cada uno. Buscá específicamente:
   - validaciones que están en uno y faltan en el otro
   - reglas de negocio duplicadas que podrían haber quedado distintas
   - cálculos de totales, descuentos o impuestos hechos en dos lugares
   - transiciones de estado permitidas en uno y prohibidas en el otro

3. Revisá si los servicios (backend/.../service/) son realmente el único lugar
   donde viven las reglas, o si algún controlador decide cosas por su cuenta.

FORMATO DEL INFORME:
Una tabla de operaciones con sus caminos. Después, una lista de
contradicciones encontradas, cada una con:
   - archivo:línea de cada lado
   - qué dice cada uno
   - qué pasa en la práctica si se usa el camino flojo
   - gravedad: ROMPE DATOS / INCONSISTENTE / COSMÉTICO

No propongas arreglos todavía. Primero quiero ver el mapa completo.
```

---

# Tanda 2 · Flujos de compra, de punta a punta

```
Seguimos con la auditoría de Alba en los Andes. Ahora quiero que EJECUTES los
flujos de compra de verdad y verifiques cada uno contra MySQL, no contra lo
que muestra la pantalla.

Regla innegociable: después de cada paso, consultá la base y mostrame el SELECT
con su resultado. Que la pantalla diga "listo" no prueba que se haya guardado.

FLUJOS A PROBAR, cada uno completo:

A. Compra normal de un cliente logueado
   Verificá en la base: fila en pedidos, filas en detalle_pedido con el precio
   CONGELADO (no el precio actual del producto), stock descontado, movimiento
   de SALIDA registrado, y que el total guardado sea igual a la suma de los
   renglones.

B. Compra sin cuenta, si el sitio la permite
   Si crea un usuario al vuelo, mirá con qué datos queda.

C. Compra con stock insuficiente
   Pedí más botellas de las que hay. Tiene que rechazarla. Verificá que NO haya
   quedado un pedido a medias ni stock descontado.

D. Compra del mismo vino dos veces en el mismo carrito
   Dos renglones del mismo producto. Verificá que descuente la suma y no una
   sola vez.

E. Compra por transferencia
   Hay columnas de pago por transferencia en la base (ver
   sql/agregar_pago_transferencia.sql). Seguí el circuito hasta donde llegue y
   decime dónde se corta.

F. Compra con Mercado Pago
   Ver docs/MERCADO-PAGO.md. Si no se puede completar, decime exactamente en
   qué paso falla y con qué error textual.

G. Cupones y desglose del total
   Existen Cupones.java y sql/agregar_desglose_del_total.sql. Probá un cupón y
   verificá que el desglose guardado cierre con lo que se le cobró.

H. Cancelación de un pedido
   Desde la cuenta del cliente. Verificá que el stock vuelva, que quede el
   movimiento de ajuste, y que la solicitud quede con el estado correcto.

I. Lo mismo que A, pero desde la app Swing
   Compará el resultado en la base contra el del flujo A. Si quedan filas
   distintas para la misma operación, eso es un hallazgo grave.

PARA CADA FLUJO: qué hiciste, qué esperabas, qué pasó, el SELECT que lo
prueba, y si pasa o falla.

Si alguno no se puede ejecutar (falta una credencial, un servicio externo no
responde), decilo en vez de darlo por bueno.
```

---

# Tanda 3 · Panel de gestión y app de escritorio

```
Seguimos con la auditoría de Alba en los Andes. Ahora el lado de gestión.

CONTEXTO: frontend/templates/admin/ está VACÍO. El panel sigue siendo la página
vieja, frontend/static/admin.html, que le pega a /api/**. La app Swing
(backend/.../escritorio/) también. Las dos quedaron fuera de la migración a
Thymeleaf.

QUÉ QUIERO:

1. Recorré las dos y probá cada sección. En el panel: tablero, inventario,
   ingreso, movimientos, pedidos, clientes, solicitudes, FAQ. En Swing: todos
   los paneles que haya.

2. Por cada pantalla decime:
   - qué funciona
   - qué está roto o tira error
   - qué muestra datos que no coinciden con la base (verificalo con un SELECT)
   - qué botón no hace nada

3. Comparación cruzada, que es lo que más me interesa: tomá el mismo dato
   (stock de un vino, estado de un pedido, total vendido) y miralo en los tres
   lugares a la vez: el panel, la app Swing y la base. Si los tres no dicen lo
   mismo, quiero saber cuál miente y por qué.

4. Hacé un cambio desde Swing y verificá si el panel lo ve al recargar, y al
   revés. Si hay caché en algún lado que muestre datos viejos, señalalo.

5. Probá las operaciones destructivas con cuidado: dar de baja un producto que
   tiene ventas, dar de baja al único administrador, cancelar un pedido ya
   entregado. Tienen que estar impedidas o explicadas. Si alguna rompe datos,
   es el hallazgo más importante del informe.

Mismo formato: qué probaste, qué esperabas, qué pasó, el SELECT que lo prueba.
```

---

# Tanda 4 · Qué falta y qué agregar

```
Última tanda de la auditoría de Alba en los Andes. Ahora mirá qué NO está.

1. Leé docs/MEJORAS-PENDIENTES.md y decime cuáles de esos temas ya se
   resolvieron y cuáles siguen abiertos. El documento puede haber quedado
   desactualizado respecto del código.

2. Buscá funcionalidad a medio hacer: endpoints que nadie llama, columnas en la
   base que nadie lee, pantallas sin terminar, servicios escritos pero no
   conectados. Enumerá lo que encuentres con su archivo.

3. Pensá qué le falta a esto para ser una tienda que funcione de verdad, no
   para un trabajo de facultad. Ordenalo por lo que más duele, no por lo más
   fácil.

4. Revisá si hay reglas de negocio que un negocio real necesita y acá no
   existen: compra mínima, límite de botellas por pedido, control de que no se
   venda a menores, qué pasa si un vino se discontinúa con pedidos pendientes,
   devoluciones.

5. Punto aparte, y quiero que seas directo: ¿hay algo en este proyecto que
   parezca funcionar pero no funcione? Pantallas que muestran datos inventados
   en vez de reales, botones decorativos, confirmaciones que no confirman nada,
   mensajes de éxito sobre operaciones que fallaron en silencio. Eso es lo peor
   que puede aparecer en una defensa y lo que menos se nota probando a mano.

Cerrá con las cinco cosas que arreglaría yo primero, con el motivo de cada una.
```

---

## Cómo leer el informe

Tres hallazgos cambian de prioridad según lo que sean:

| Tipo | Qué significa | Cuándo arreglarlo |
|---|---|---|
| **Rompe datos** | Deja la base en un estado imposible: stock negativo, pedido sin renglones, total que no cierra | Siempre, primero |
| **Inconsistente** | Los dos caminos hacen lo mismo distinto, o una pantalla muestra algo que la base no dice | Antes de la entrega |
| **Cosmético** | Se ve mal pero los datos están bien | Si sobra tiempo |

Lo que **no** es un hallazgo: que falte una función que nunca se pidió. Eso va
a la tanda 4, no a las primeras tres.
