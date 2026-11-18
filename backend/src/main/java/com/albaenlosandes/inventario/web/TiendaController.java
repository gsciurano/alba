package com.albaenlosandes.inventario.web;

import com.albaenlosandes.inventario.config.UsuarioAutenticado;
import com.albaenlosandes.inventario.model.*;
import com.albaenlosandes.inventario.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

/**
 * ==================================================================
 *  LA TIENDA: CATALOGO, FILTROS Y CARRITO
 * ==================================================================
 *
 * QUE REEMPLAZA. La mitad de js/tienda.js (915 lineas), que era el
 * archivo mas grande del frontend publico:
 *
 *     tienda.js:28  filtros              -> FiltrosDelCatalogo
 *     tienda.js:79  poblarSelectores     -> los tres metodos de abajo
 *     tienda.js:107 productosFiltrados   -> FiltrosDelCatalogo.aplicarA
 *     tienda.js:137 renderCatalogo       -> GET /tienda/grilla
 *     tienda.js:318 agregarAlCarrito     -> POST /tienda/carrito
 *     tienda.js:355 abrirCarrito         -> GET /tienda/carrito
 *     tienda.js:337 calcularTotales      -> CalculadoraDeCuentas
 *     store.js:381  carrito (localStorage) -> CarritoSesion
 *
 * EL PATRON DE LOS FRAGMENTOS. Cada pedazo de pantalla que puede
 * cambiar sin recargar tiene su propia direccion y devuelve HTML, no
 * JSON:
 *
 *     GET  /tienda           la pagina entera
 *     GET  /tienda/grilla    solo la grilla + el contador de resultados
 *     GET  /tienda/carrito   el panel del carrito
 *     POST /tienda/carrito   agrega y devuelve el panel actualizado
 *
 * Se pueden probar con curl de a uno:
 *     curl "localhost:8080/tienda/grilla?texto=malbec&orden=precio-asc"
 *
 * POR QUE NO SE TOCA /api/**. Los @RestController de /api sirven a la
 * app de escritorio en Swing, que espera JSON. Este controlador es
 * aparte y devuelve HTML. Las dos rutas leen los mismos servicios, asi
 * que no hay dos verdades.
 */
@Controller
@RequestMapping("/tienda")
public class TiendaController {

    /**
     * Los topes del desplegable "Precio maximo", tal como estaban
     * escritos a mano en tienda.html.
     *
     * POR QUE SIGUEN FIJOS Y NO SE CALCULAN DEL CATALOGO: son cortes
     * comerciales redondos ("hasta $15.000"), elegidos para que la
     * persona elija rapido. Calcularlos de los precios reales daria
     * topes como "hasta $17.300", que no ayudan a decidir.
     */
    public record TopeDePrecio(String valor, String etiqueta) { }

    private static final List<TopeDePrecio> TOPES = List.of(
            new TopeDePrecio("15000", "hasta $15.000"),
            new TopeDePrecio("20000", "hasta $20.000"),
            new TopeDePrecio("30000", "hasta $30.000"),
            new TopeDePrecio("40000", "hasta $40.000")
    );

    private final ProductoService productos;
    private final CarritoSesion carrito;
    private final CalculadoraDeCuentas calculadora;
    private final Cupones cupones;
    private final PedidoService pedidos;
    private final UsuarioService usuarios;
    private final ServicioCorreo correo;

    public TiendaController(ProductoService productos,
                            CarritoSesion carrito,
                            CalculadoraDeCuentas calculadora,
                            Cupones cupones,
                            PedidoService pedidos,
                            UsuarioService usuarios,
                            ServicioCorreo correo) {
        this.productos = productos;
        this.carrito = carrito;
        this.calculadora = calculadora;
        this.cupones = cupones;
        this.pedidos = pedidos;
        this.usuarios = usuarios;
        this.correo = correo;
    }

    // ============================================================
    //  LA PAGINA
    // ============================================================

    /**
     * La tienda completa: portada, filtros y grilla.
     *
     * LOS FILTROS LLEGAN EN LA URL, y eso es nuevo. Antes vivian en una
     * variable del navegador, asi que un catalogo filtrado no se podia
     * compartir ni volver a el con el boton Atras: la direccion era
     * siempre tienda.html. Ahora
     * /tienda?varietal=Malbec&orden=precio-asc es una direccion de
     * verdad, se guarda en favoritos y se manda por mensaje.
     */
    @GetMapping
    public String tienda(@ModelAttribute("filtros") FiltrosDelCatalogo filtros, Model modelo) {
        carrito.olvidarLoResuelto();
        llenarElModelo(filtros, modelo);
        return "tienda/catalogo";
    }

    /**
     * Solo la grilla y el contador. Es lo que piden los filtros con
     * hx-get cada vez que alguien cambia algo.
     *
     * POR QUE DEVUELVE DOS COSAS EN UNA RESPUESTA. El contador
     * ("10 vinos encontrados") esta arriba de los filtros y la grilla
     * abajo: no son vecinos, asi que un solo hx-target no alcanza. El
     * fragmento envuelve a los dos y se reemplaza entero. Es mas
     * simple que dos pedidos o un swap fuera de banda, y el contador
     * conserva su aria-live, que es lo que le anuncia el resultado a
     * quien usa lector de pantalla.
     */
    @GetMapping("/grilla")
    public String grilla(@ModelAttribute("filtros") FiltrosDelCatalogo filtros,
                         HttpServletRequest pedido,
                         HttpServletResponse respuesta,
                         Model modelo) {
        carrito.olvidarLoResuelto();

        /*
         * LA DIRECCION QUE SE ESCRIBE EN LA BARRA LA DECIDE EL
         * SERVIDOR, con la cabecera HX-Push-Url.
         *
         * POR QUE NO ALCANZA hx-push-url="true" EN LA PLANTILLA: ese
         * atributo empuja la direccion DEL PEDIDO, y el pedido va a
         * /tienda/grilla, que es el fragmento. Quedaba en la barra
         * algo como
         *     /tienda/grilla?texto=malbec&orden=nombre
         * y esa direccion, recargada o compartida, devuelve la grilla
         * pelada: sin encabezado, sin menu y sin pie. Una direccion
         * que se puede compartir y que al abrirla muestra media
         * pagina es peor que no empujar ninguna.
         *
         * Con la cabecera, el navegador guarda /tienda?<los filtros>,
         * que es la pagina de verdad y devuelve exactamente lo que se
         * esta viendo.
         */
        String consulta = pedido.getQueryString();
        respuesta.setHeader("HX-Push-Url",
                pedido.getContextPath() + "/tienda" + (consulta == null ? "" : "?" + consulta));

        llenarElModelo(filtros, modelo);
        return "tienda/catalogo :: resultado";
    }

    private void llenarElModelo(FiltrosDelCatalogo filtros, Model modelo) {
        List<VinoVista> activos = productos.obtenerTodos().stream().map(VinoVista::de).toList();

        modelo.addAttribute("vinos", filtros.aplicarA(activos));
        modelo.addAttribute("comprable", true);
        modelo.addAttribute("topes", TOPES);

        /*
         * LAS TRES LISTAS DE LOS DESPLEGABLES SALEN DEL CATALOGO REAL.
         *
         * Antes dos de las tres eran listas fijas en
         * static/data/productos.js (VARIETALES y LINEAS) y solo las
         * cosechas se calculaban de los datos. El problema de la lista
         * fija es que ofrece filtros que no filtran: si la bodega no
         * tiene ningun Chardonnay a la venta, "Chardonnay" seguia en el
         * desplegable y elegirlo daba cero resultados.
         *
         * Calculadas del catalogo, un filtro que esta en la lista
         * siempre devuelve algo.
         */
        modelo.addAttribute("varietales", activos.stream()
                .map(VinoVista::getVarietal).filter(v -> v != null && !v.isBlank())
                .distinct().sorted().toList());
        modelo.addAttribute("lineas", activos.stream()
                .map(VinoVista::getLinea).filter(l -> l != null && !l.isBlank())
                .distinct().sorted().toList());
        modelo.addAttribute("cosechas", activos.stream()
                .map(VinoVista::getCosecha).filter(c -> c != null)
                .distinct().sorted(Comparator.reverseOrder()).toList());
    }

    // ============================================================
    //  EL CARRITO
    // ============================================================

    /**
     * El carrito, como pagina.
     *
     * POR QUE PAGINA Y NO MODAL: la razon larga esta en el comentario
     * de tienda/carrito.html. En una linea: el modal viejo ya queria
     * ser una pagina y lo simulaba con el ancla tienda.html#carrito,
     * que sitio.js:128 escribia a mano.
     */
    @GetMapping("/carrito")
    public String verCarrito(Model modelo) {
        carrito.olvidarLoResuelto();
        ponerElCarritoEnElModelo(modelo);
        return "tienda/carrito";
    }

    /**
     * Agrega botellas al carrito desde el catalogo.
     *
     * NO LLEVA A NINGUNA PARTE, y eso es a proposito: quien esta
     * mirando el catalogo quiere seguir mirandolo. Lo que vuelve es el
     * pedazo de tarjeta con el boton, ahora en estado "Agregado", mas
     * la burbuja del encabezado fuera de banda. Dos lugares de la
     * pantalla cambian y la pagina no se recarga.
     *
     * Es lo que hacia agregarAlCarrito en tienda.js:318 con un toast()
     * y actualizarBadgeCarrito(), pero sin que el navegador tenga que
     * saber como se dibuja un boton ni de donde sale el numero.
     *
     * SIN HTMX se vuelve al carrito como pagina: sin JavaScript no hay
     * forma de cambiar un pedazo de la pantalla, asi que el formulario
     * navega. Es el comportamiento honesto, no un plan B roto.
     */
    @PostMapping("/carrito")
    public String agregar(@RequestParam Integer idProducto,
                          @RequestParam(defaultValue = "1") int cantidad,
                          @RequestHeader(value = "HX-Request", required = false) String htmx,
                          Model modelo) {
        carrito.olvidarLoResuelto();
        CarritoSesion.Resultado r = carrito.agregar(idProducto, cantidad);

        modelo.addAttribute("aviso", r.mensaje());
        modelo.addAttribute("itemsCarrito", carrito.getCantidadTotal());

        if (!esHtmx(htmx)) {
            ponerElCarritoEnElModelo(modelo);
            return "tienda/carrito";
        }

        // El vino que se acaba de agregar, para redibujar su boton.
        modelo.addAttribute("vino", productos.obtenerPorId(idProducto)
                .map(VinoVista::de).orElse(null));
        modelo.addAttribute("agregado", r.ok());
        return "tienda/fragmentos :: compraConBurbuja";
    }

    /** Cambia la cantidad de un renglon. En cero o menos, lo quita. */
    @PostMapping("/carrito/{idProducto}")
    public String cambiarCantidad(@PathVariable Integer idProducto,
                                  @RequestParam int cantidad,
                                  @RequestHeader(value = "HX-Request", required = false) String htmx,
                                  Model modelo) {
        carrito.olvidarLoResuelto();
        CarritoSesion.Resultado r = carrito.cambiarCantidad(idProducto, cantidad);
        ponerElCarritoEnElModelo(modelo);
        modelo.addAttribute("aviso", r.mensaje());
        return esHtmx(htmx) ? "tienda/fragmentos :: panelConBurbuja" : "tienda/carrito";
    }

    /**
     * Saca un vino del carrito.
     *
     * ES UN DELETE DE VERDAD (hx-delete), no un POST disfrazado: la
     * accion es "borra este renglon" y el metodo HTTP que significa eso
     * es DELETE. Para que Spring Security no lo rechace, el token CSRF
     * viaja en una cabecera que declara el <body> con hx-headers; ver
     * la nota en tienda/catalogo.html.
     *
     * Solo lo usa HTMX: sin JavaScript, el mismo boton es un submit del
     * formulario del renglon con cantidad=0, que entra por el metodo de
     * arriba.
     */
    @DeleteMapping("/carrito/{idProducto}")
    public String quitar(@PathVariable Integer idProducto, Model modelo) {
        carrito.olvidarLoResuelto();
        carrito.quitar(idProducto);
        ponerElCarritoEnElModelo(modelo);
        return "tienda/fragmentos :: panelConBurbuja";
    }

    // ============================================================
    //  EL CUPON
    // ============================================================

    /**
     * Aplica o saca un codigo promocional.
     *
     * EL DESCUENTO NO VIAJA DESDE EL NAVEGADOR. Lo unico que llega es
     * el codigo que la persona escribio; cuanto vale lo decide Cupones
     * del lado del servidor. Antes el navegador tenia la tabla entera
     * (tienda.js:683): se leia abriendo el inspector, y el descuento
     * que informaba era el que el cliente quisiera informar.
     *
     * UN CODIGO QUE NO EXISTE NO ES UN ERROR DEL PEDIDO: se guarda
     * igual para poder mostrar "el codigo X no existe o venció" al
     * lado de la cajita, que es lo que hacia aplicarCupon en
     * tienda.js:705. El carrito se queda sin descuento y la compra
     * sigue andando.
     */
    @PostMapping("/cupon")
    public String aplicarCupon(@RequestParam(required = false) String codigo,
                               @RequestHeader(value = "HX-Request", required = false) String htmx,
                               Model modelo) {
        carrito.olvidarLoResuelto();
        carrito.setCupon(codigo);

        if (!esHtmx(htmx)) {
            return "redirect:/tienda/pagar";
        }
        ponerElCarritoEnElModelo(modelo);
        return "tienda/fragmentos :: resumenDelPedido";
    }

    // ============================================================
    //  EL CHECKOUT
    // ============================================================

    /**
     * La pantalla de finalizar compra.
     *
     * ES UNA PAGINA, NO UN MODAL. El checkout viejo era el modal mas
     * grande del sitio (modal--ancho, 980px) y cargaba con todos los
     * problemas de serlo: el boton Atras sacaba del sitio, no se podia
     * volver a el, y si se cerraba sin querer habia que rehacer los
     * cuatro campos. Como pagina tiene direccion propia, /tienda/pagar.
     *
     * LAS DOS COLUMNAS NO CAMBIAN: a la izquierda los tres pasos
     * numerados, a la derecha el resumen que queda a la vista. Es el
     * mismo marcado y el mismo CSS (.pago, .pago__columna,
     * .pago__resumen).
     *
     * CON EL CARRITO VACIO NO HAY NADA QUE PAGAR, asi que se va al
     * carrito en vez de mostrar un formulario que no puede terminar.
     */
    @GetMapping("/pagar")
    public String pagar(@ModelAttribute("compra") DatosDeCompra compra,
                        @AuthenticationPrincipal UsuarioAutenticado autenticado,
                        Model modelo) {
        carrito.olvidarLoResuelto();
        if (carrito.isVacio()) {
            return "redirect:/tienda/carrito";
        }

        /*
         * SI YA INGRESO, LOS DATOS VIENEN PUESTOS. Es lo que hacia
         * tienda.js:446 leyendo la sesion del localStorage, pero ahora
         * salen de la base: si la persona corrigio su telefono en el
         * perfil, el checkout muestra el corregido.
         */
        if (autenticado != null && compra.getEmail().isEmpty()) {
            Usuario u = autenticado.getUsuario();
            compra.setNombre(((u.getNombre() == null ? "" : u.getNombre()) + " "
                            + (u.getApellido() == null ? "" : u.getApellido())).trim());
            compra.setEmail(u.getEmail());
            compra.setTelefono(u.getTelefono() == null ? "" : u.getTelefono());
        }

        ponerElCarritoEnElModelo(modelo);
        return "tienda/pagar";
    }

    /**
     * Confirma la compra.
     *
     * EL ORDEN DE LOS CONTROLES, Y POR QUE ESTE:
     *
     *  1. Bean Validation sobre los cuatro datos del comprador.
     *  2. La tarjeta, solo si se paga con tarjeta. Son tres reglas que
     *     dependen de otro campo, asi que no pueden ser anotaciones:
     *     ver DatosDeCompra.
     *  3. El carrito no puede estar vacio.
     *  4. PedidoService, que bloquea los productos, revalida el stock
     *     y hace las cuentas. Es el unico control que decide: los tres
     *     anteriores son para avisar antes y no para autorizar.
     *
     * SI ALGO FALLA SE DEVUELVE LA MISMA PAGINA con los errores al
     * lado de cada campo y todo lo demas como estaba. Antes esto lo
     * hacia marcar() en tienda.js:793 agregando clases y textos a
     * mano; ahora lo hace th:errors, que ademas no puede olvidarse de
     * un campo.
     *
     * POST-REDIRECT-GET: si sale bien no se devuelve HTML, se redirige
     * a la confirmacion. Asi recargar la pagina de confirmacion no
     * vuelve a comprar. El codigo viejo tapaba eso deshabilitando el
     * boton (tienda.js:822), que es una curita: alcanzaba con apretar
     * F5 para que el navegador reenviara el formulario.
     */
    @PostMapping("/pagar")
    public String confirmar(@Valid @ModelAttribute("compra") DatosDeCompra compra,
                            BindingResult errores,
                            RedirectAttributes flash,
                            Model modelo) {
        carrito.olvidarLoResuelto();

        if (compra.isPagaConTarjeta()) {
            if (!compra.isNumeroDeTarjetaValido()) {
                errores.rejectValue("tarjetaNumero", "luhn",
                        "Número inválido. Podés generar uno de prueba con el botón.");
            }
            if (!compra.isVencimientoValido()) {
                errores.rejectValue("tarjetaVence", "vence",
                        "Vencimiento inválido o vencido (MM/AA)");
            }
            if (!compra.isCvvValido()) {
                errores.rejectValue("tarjetaCvv", "cvv", "Son 3 o 4 dígitos");
            }
        }

        if (carrito.isVacio()) {
            return "redirect:/tienda/carrito";
        }

        if (errores.hasErrors()) {
            ponerElCarritoEnElModelo(modelo);
                return "tienda/pagar";
        }

        Pedido guardado;
        try {
            Usuario comprador = usuarios.resolverComprador(
                    compra.getNombre(), compra.getEmail(), compra.getTelefono());

            Pedido pedido = new Pedido();
            pedido.setUsuario(comprador);
            pedido.setMedioPago(compra.getMedioPago());
            carrito.comoMapa().forEach((idProducto, cantidad) -> {
                Producto p = new Producto();
                p.setIdProducto(idProducto);
                DetallePedido renglon = new DetallePedido();
                renglon.setProducto(p);
                renglon.setCantidad(cantidad);
                pedido.getDetalles().add(renglon);
            });

            guardado = pedidos.crearPedido(pedido, carrito.getCupon());

        } catch (IllegalArgumentException e) {
            /*
             * ACA CAEN LOS RECHAZOS DEL SERVIDOR: sin stock, vino dado
             * de baja, un ADMIN intentando comprar. El mensaje que se
             * muestra es el que escribio el servicio, sin reescribirlo:
             * el que sabe por que no se pudo es el que dijo que no.
             *
             * El carrito NO se vacia: la persona tiene que poder
             * corregir y volver a intentar.
             */
            ponerElCarritoEnElModelo(modelo);
                modelo.addAttribute("aviso", e.getMessage());
            return "tienda/pagar";
        }

        /*
         * El comprobante sale DESPUES de que crearPedido devolvio, o
         * sea con la compra ya comprometida en la base: si saliera
         * desde adentro de la transaccion y esta se revirtiera, el
         * cliente recibiria el comprobante de un pedido inexistente.
         * Es la misma razon que explica PedidoController.crear.
         */
        correo.enviarComprobante(guardado);

        /*
         * LA DIRECCION Y LOS ULTIMOS CUATRO DIGITOS NO ESTAN EN LA
         * BASE. La tabla 'pedidos' no tiene columna de direccion de
         * entrega -una limitacion que ya existia- y el numero de
         * tarjeta se descarta a proposito. Van como atributos de un
         * solo uso: sobreviven a la redireccion y despues se borran.
         *
         * Si alguien recarga la confirmacion, esos dos datos ya no
         * estan y la pantalla los omite en vez de inventarlos.
         */
        flash.addFlashAttribute("direccion", compra.getDireccion());
        flash.addFlashAttribute("ultimosCuatro",
                compra.isPagaConTarjeta() ? compra.ultimosCuatro() : "");

        carrito.vaciar();
        return "redirect:/tienda/pedido/" + guardado.getIdPedido();
    }

    /**
     * La confirmacion: "Pedido confirmado, #27".
     *
     * LOS NUMEROS SALEN DE LA BASE, no de lo que calculo la pantalla
     * anterior. Antes la confirmacion mezclaba las dos cosas -el total
     * de los vinos lo mandaba el servidor y el envio, el 21% y el
     * descuento los ponia el navegador- y por eso podia mostrar un
     * numero distinto al del comprobante que llegaba por correo. Ahora
     * los cinco importes son las cinco columnas del pedido guardado.
     */
    @GetMapping("/pedido/{id}")
    public String confirmacion(@PathVariable Integer id, Model modelo) {
        Pedido pedido = pedidos.obtenerPorId(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe el pedido #" + id));
        modelo.addAttribute("pedido", pedido);
        modelo.addAttribute("botellas", pedido.getDetalles().stream()
                .mapToInt(DetallePedido::getCantidad).sum());
        modelo.addAttribute("datosBancarios", DatosBancarios.LOS_DE_LA_BODEGA);
        return "tienda/confirmacion";
    }

    // ============================================================

    /**
     * El carrito y sus cuentas, como lo necesitan las plantillas.
     *
     * EL CUPON SE GUARDA EN EL CARRITO, no en el formulario: asi
     * sobrevive a que se agregue otro vino, que es lo que pasaba antes
     * (cuponAplicado de tienda.js:436 se borraba al reabrir el
     * checkout).
     */
    private void ponerElCarritoEnElModelo(Model modelo) {
        String tecleado = carrito.getCupon();
        Cupones.Cupon cupon = cupones.buscar(tecleado).orElse(null);

        modelo.addAttribute("renglones", carrito.getRenglones());
        modelo.addAttribute("cuentas", calculadora.calcular(carrito.getSubtotal(), cupon));
        modelo.addAttribute("itemsCarrito", carrito.getCantidadTotal());
        modelo.addAttribute("datosBancarios", DatosBancarios.LOS_DE_LA_BODEGA);

        /*
         * EL ESTADO DEL CUPON LO DECIDE EL SERVIDOR, no la plantilla.
         *
         * Son tres casos: nada tecleado, un codigo que existe, un
         * codigo que no. Antes esto lo resolvia aplicarCupon() en
         * tienda.js:705 con la tabla de cupones a mano en el
         * navegador.
         *
         * POR QUE LA CLASE TAMBIEN SE ARMA ACA Y NO EN EL th:classappend:
         * Thymeleaf usa __algo__ como sintaxis de preprocesado, asi que
         * una expresion con dos guiones bajos dobles adentro -y
         * "cupon__estado--ok" junto a "cupon__estado--error" los
         * tiene- no parsea. Mandar el valor ya decidido evita el
         * problema y deja la plantilla sin logica, que es como tiene
         * que estar.
         */
        modelo.addAttribute("codigoTecleado", tecleado == null ? "" : tecleado);
        if (tecleado == null || tecleado.isBlank()) {
            modelo.addAttribute("claseDelCupon", "");
            modelo.addAttribute("mensajeDelCupon", "");
        } else if (cupon != null) {
            modelo.addAttribute("claseDelCupon", " cupon__estado--ok");
            modelo.addAttribute("mensajeDelCupon", "Listo: " + cupon.etiqueta() + ".");
        } else {
            modelo.addAttribute("claseDelCupon", " cupon__estado--error");
            modelo.addAttribute("mensajeDelCupon",
                    "El código " + tecleado + " no existe o venció.");
        }
    }

    /**
     * El bloque del medio de pago elegido.
     *
     * Lo piden los radios del checkout al cambiar. Devuelve el bloque
     * de la tarjeta o el de la transferencia, nunca los dos: el que no
     * corresponde deja de existir en vez de viajar escondido con
     * hidden=true, que es lo que hacia tienda.js:468.
     */
    @GetMapping("/medios")
    public String medios(@ModelAttribute("compra") DatosDeCompra compra, Model modelo) {
        modelo.addAttribute("datosBancarios", DatosBancarios.LOS_DE_LA_BODEGA);
        return "tienda/fragmentos :: bloquesDePago";
    }

    /** El pedido viene de HTMX cuando trae la cabecera HX-Request. */
    private static boolean esHtmx(String cabecera) {
        return cabecera != null;
    }
}
