package com.albaenlosandes.inventario.web;

import com.albaenlosandes.inventario.model.Producto;
import com.albaenlosandes.inventario.service.ProductoService;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ==================================================================
 *  EL CARRITO, EN LA SESION DEL SERVIDOR
 * ==================================================================
 *
 * DE DONDE SALE ESTA CLASE. Reemplaza al objeto 'carrito' de
 * js/modules/store.js:381, que guardaba el carrito en el
 * localStorage del navegador bajo la clave 'alba_carrito'.
 *
 * QUE ESTABA MAL CON EL localStorage. No que fuera del lado del
 * cliente -un carrito es informacion del cliente- sino QUE guardaba:
 * cada renglon llevaba el nombre, el PRECIO y la foto del vino,
 * copiados del catalogo en el momento de agregarlo. Tres problemas
 * concretos:
 *
 *   1. El precio quedaba congelado en el navegador. Si la bodega
 *      cambiaba el precio, el carrito seguia mostrando el viejo hasta
 *      que el cliente lo vaciara. Y era editable: abrir la consola y
 *      escribir un precio de $1 era un renglon de JavaScript.
 *   2. El carrito no cruzaba de dispositivo. Armado en la compu, no
 *      existia en el telefono, aunque fuera la misma persona con la
 *      misma cuenta.
 *   3. Un vino dado de baja seguia en el carrito, con su foto y su
 *      precio, como si todavia se vendiera.
 *
 * QUE GUARDA ESTA CLASE. Solo pares (id de producto, cantidad). Nada
 * mas. El nombre, el precio, la foto y el stock se vuelven a leer de
 * la base en cada vista, asi que siempre son los de ahora. Un vino
 * dado de baja desaparece solo del carrito, sin que nadie tenga que
 * acordarse de limpiarlo.
 *
 * POR QUE @SessionScope Y NO UNA TABLA 'carritos'. Porque un carrito
 * sin confirmar no es un dato del negocio: es una pantalla a medio
 * llenar. Guardarlo en MySQL obliga a decidir cuando se vence, quien
 * lo borra y que pasa con el de los visitantes sin cuenta. La sesion
 * ya resuelve las tres cosas: dura lo que dura la visita y se va sola.
 * El costo es que el carrito se pierde al cerrar el navegador, que es
 * lo mismo que pasaba antes para quien navegaba en una ventana
 * privada.
 *
 * PROXY_MODE Y POR QUE HACE FALTA. El controlador es un singleton y
 * esta clase vive por sesion. Spring inyecta un intermediario que, en
 * cada llamada, busca el carrito de la sesion que corresponde. Sin
 * esto, el primer visitante en entrar le dejaria su carrito a todos
 * los demas: un bug que en una tienda significa comprar las botellas
 * de otro.
 */
@Component
@SessionScope(proxyMode = ScopedProxyMode.TARGET_CLASS)
public class CarritoSesion {

    /**
     * id de producto -> cantidad. LinkedHashMap para que los renglones
     * se muestren en el orden en que se fueron agregando; con un
     * HashMap el carrito se reordenaria solo entre pedidos HTTP y el
     * cliente veria sus vinos bailar.
     */
    private final Map<Integer, Integer> cantidades = new LinkedHashMap<>();

    private final ProductoService productos;

    public CarritoSesion(ProductoService productos) {
        this.productos = productos;
    }

    // ==============================================================
    //  LECTURA
    // ==============================================================

    /**
     * Un renglon del carrito, ya resuelto contra la base.
     *
     * @param vino     el vino como lo necesita la pantalla
     * @param cantidad botellas pedidas
     * @param importe  precio x cantidad, calculado aca y no en la plantilla
     */
    public record Renglon(VinoVista vino, int cantidad, BigDecimal importe) { }

    /**
     * Los renglones del carrito con los datos de ahora.
     *
     * UNA SOLA CONSULTA para todo el carrito, no una por renglon: ver
     * ProductoService.obtenerPorIds.
     *
     * LIMPIEZA SILENCIOSA: si un vino del carrito ya no existe o fue
     * dado de baja, se saca del mapa y no se devuelve. Es silenciosa a
     * proposito; avisar "sacamos un vino de tu carrito" obligaria a
     * guardar ese aviso hasta la proxima vista, y lo que el cliente
     * necesita saber es cuanto va a pagar por lo que SI puede comprar.
     */
    public List<Renglon> getRenglones() {
        if (cantidades.isEmpty()) {
            return List.of();
        }
        if (resueltos != null) {
            return resueltos;                       // ya se resolvio en este pedido HTTP
        }

        // Los productos que todavia se venden, indexados por id.
        Map<Integer, Producto> vigentes = new LinkedHashMap<>();
        for (Producto p : productos.obtenerPorIds(cantidades.keySet())) {
            if (!Boolean.FALSE.equals(p.getActivo())) {
                vigentes.put(p.getIdProducto(), p);
            }
        }

        // Lo que no esta vigente se cae del carrito.
        cantidades.keySet().retainAll(vigentes.keySet());

        List<Renglon> renglones = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : cantidades.entrySet()) {
            Producto p = vigentes.get(e.getKey());
            renglones.add(new Renglon(
                    VinoVista.de(p),
                    e.getValue(),
                    p.getPrecio().multiply(BigDecimal.valueOf(e.getValue()))));
        }
        resueltos = List.copyOf(renglones);
        return resueltos;
    }

    /**
     * LOS RENGLONES YA RESUELTOS, PARA NO VOLVER A PREGUNTAR.
     *
     * Una misma vista recorre el carrito varias veces: el subtotal, la
     * burbuja del encabezado, el detalle de la derecha. Sin esto, cada
     * recorrida era otra consulta a la base por los mismos vinos.
     *
     * POR QUE SE PUEDE CACHEAR SIN RIESGO: el objeto vive en la sesion,
     * pero el cache se tira en cuanto el carrito cambia (abajo, en cada
     * metodo que escribe) y tambien al terminar el pedido HTTP, con
     * olvidarLoResuelto(), que llama el controlador. Si no se tirara al
     * terminar, el carrito mostraria precios y stock de la visita
     * anterior.
     *
     * No es volatile ni sincronizado a proposito: una sesion HTTP es de
     * una persona. Dos pestanas de la misma persona pueden pisarse el
     * cache, y el peor caso es que una de las dos muestre el carrito un
     * instante desactualizado, que es lo que pasaria igual.
     */
    private transient List<Renglon> resueltos;

    /**
     * Tira el cache. Lo llama el controlador al empezar a atender, para
     * que la vista lea precios y stock de ahora.
     */
    public void olvidarLoResuelto() {
        resueltos = null;
    }

    /** La suma de los renglones. Es el subtotal, sin envio ni impuestos. */
    public BigDecimal getSubtotal() {
        return getRenglones().stream()
                .map(Renglon::importe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Botellas en total: el numero de la burbuja del encabezado. */
    public int getCantidadTotal() {
        return getRenglones().stream().mapToInt(Renglon::cantidad).sum();
    }

    public boolean isVacio() {
        return getRenglones().isEmpty();
    }

    /** Lo que necesita PedidoService: id de producto -> cantidad. */
    public Map<Integer, Integer> comoMapa() {
        Map<Integer, Integer> copia = new LinkedHashMap<>();
        for (Renglon r : getRenglones()) {
            copia.put(r.vino().getId(), r.cantidad());
        }
        return copia;
    }

    // ==============================================================
    //  ESCRITURA
    // ==============================================================

    /**
     * El resultado de tocar el carrito. Si no se pudo, 'mensaje' es lo
     * que se le muestra al cliente.
     */
    public record Resultado(boolean ok, String mensaje) {
        static Resultado bien()            { return new Resultado(true, null); }
        static Resultado mal(String texto) { return new Resultado(false, texto); }
    }

    /**
     * Suma botellas al carrito.
     *
     * El control de stock de aca es el mismo que hacia
     * store.js:392 y sirve para lo mismo: avisar al instante. El
     * control que decide sigue siendo el de PedidoService al
     * confirmar, con la fila del producto bloqueada; entre que alguien
     * mira el catalogo y paga, otro pudo comprar la ultima botella.
     */
    public Resultado agregar(Integer idProducto, int cantidad) {
        if (cantidad <= 0) {
            return Resultado.mal("La cantidad tiene que ser mayor a cero.");
        }
        Optional<Producto> encontrado = productos.obtenerPorId(idProducto);
        if (encontrado.isEmpty() || Boolean.FALSE.equals(encontrado.get().getActivo())) {
            return Resultado.mal("Ese vino no esta disponible.");
        }
        Producto p = encontrado.get();
        int yaEnCarrito = cantidades.getOrDefault(idProducto, 0);
        if (yaEnCarrito + cantidad > stockDe(p)) {
            // Se avisa que no alcanza SIN decir cuantas botellas hay.
            // Es la misma regla que en la tarjeta y en la ficha: el
            // inventario de la bodega no se publica.
            return Resultado.mal("No quedan tantas botellas de " + p.getNombre() + ".");
        }
        cantidades.put(idProducto, yaEnCarrito + cantidad);
        olvidarLoResuelto();
        return Resultado.bien();
    }

    /** Fija la cantidad de un renglon. En cero o menos, lo quita. */
    public Resultado cambiarCantidad(Integer idProducto, int cantidad) {
        if (!cantidades.containsKey(idProducto)) {
            return Resultado.mal("Ese vino no esta en tu carrito.");
        }
        if (cantidad <= 0) {
            cantidades.remove(idProducto);
            olvidarLoResuelto();
            return Resultado.bien();
        }
        Optional<Producto> encontrado = productos.obtenerPorId(idProducto);
        if (encontrado.isEmpty()) {
            cantidades.remove(idProducto);
            olvidarLoResuelto();
            return Resultado.mal("Ese vino ya no esta disponible.");
        }
        Producto p = encontrado.get();
        if (cantidad > stockDe(p)) {
            return Resultado.mal("No quedan tantas botellas de " + p.getNombre() + ".");
        }
        cantidades.put(idProducto, cantidad);
        olvidarLoResuelto();
        return Resultado.bien();
    }

    public void quitar(Integer idProducto) {
        cantidades.remove(idProducto);
        olvidarLoResuelto();
    }

    public void vaciar() {
        cantidades.clear();
        cupon = null;
        olvidarLoResuelto();
    }

    // ==============================================================
    //  EL CUPON
    // ==============================================================

    /**
     * El codigo que escribio la persona, tal como lo escribio.
     *
     * POR QUE VIVE EN EL CARRITO Y NO EN EL FORMULARIO DEL CHECKOUT.
     * Antes era la variable cuponAplicado de js/tienda.js:436, que se
     * ponia en null cada vez que se abria el checkout: quien aplicaba
     * un cupon, cerraba el panel para agregar otro vino y volvia a
     * pagar, perdia el descuento sin que nada se lo dijera. Guardado
     * con el carrito, el cupon dura lo que dura el carrito.
     *
     * SE GUARDA EL CODIGO, NO EL DESCUENTO. Lo que ese codigo vale lo
     * resuelve Cupones cada vez que hace falta. Si se guardara el
     * importe calculado, un cupon porcentual quedaria viejo en cuanto
     * cambiara el carrito.
     */
    private String cupon;

    public String getCupon() {
        return cupon;
    }

    /** Null o vacio borra el cupon, que es lo que hace el boton "quitar". */
    public void setCupon(String codigo) {
        this.cupon = (codigo == null || codigo.isBlank()) ? null : codigo.trim().toUpperCase();
    }

    private static int stockDe(Producto p) {
        return p.getStockActual() == null ? 0 : p.getStockActual();
    }
}
