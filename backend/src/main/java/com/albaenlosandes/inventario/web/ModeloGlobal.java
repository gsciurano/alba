package com.albaenlosandes.inventario.web;

import com.albaenlosandes.inventario.config.UsuarioAutenticado;
import com.albaenlosandes.inventario.model.Usuario;
import com.albaenlosandes.inventario.service.UsuarioService;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * ==================================================================
 *  LOS DOS DATOS QUE NECESITA EL ENCABEZADO EN TODAS LAS PAGINAS
 * ==================================================================
 *
 * El encabezado y el pie son iguales en todo el sitio, y los dos
 * necesitan saber lo mismo: quien esta ingresado y cuantas botellas hay
 * en el carrito. Sin esto, cada metodo de cada controlador tendria que
 * acordarse de agregar esos dos atributos al modelo, y la primera vez
 * que alguien se olvidara, esa pagina aparecería como si nadie hubiera
 * ingresado.
 *
 * @ControllerAdvice limitado al paquete 'web': alcanza a los
 * controladores de vistas y NO toca los @RestController de /api/**, que
 * siguen devolviendo exactamente el mismo JSON que antes.
 *
 * ANTES ESTO LO HACIA EL NAVEGADOR: sitio.js leia localStorage en cada
 * pagina para decidir que mostrar en la cinta de arriba. El dato venia
 * del propio navegador, asi que se podia editar a mano. Ahora viene de
 * la sesion del servidor.
 */
@ControllerAdvice(basePackages = "com.albaenlosandes.inventario.web")
public class ModeloGlobal {

    /**
     * El usuario de la sesion, o null si todavia no ingreso.
     *
     * Las plantillas lo usan como ${usuario}: con th:if="${usuario == null}"
     * sale el enlace "Ingresar", y con th:if="${usuario != null}" el
     * nombre, el acceso al panel (solo ADMIN) y el boton de salir.
     */
    @ModelAttribute("usuario")
    public Usuario usuario(@AuthenticationPrincipal UsuarioAutenticado autenticado) {
        return autenticado == null ? null : autenticado.getUsuario();
    }

    /**
     * Cuantas botellas hay en el carrito, para la burbuja del encabezado.
     *
     * POR QUE EN LA SESION Y NO EN localStorage: el carrito tiene que
     * poder consultarse desde el servidor para que el servidor sea quien
     * calcule el total. Guardado en el navegador, el servidor no lo ve, y
     * el total que calculara el cliente seria el total que se cobra.
     *
     * El carrito se inyecta como un intermediario de sesion: ver la nota
     * de CarritoSesion sobre ScopedProxyMode.
     */
    @ModelAttribute("itemsCarrito")
    public int itemsCarrito() {
        return carrito.getCantidadTotal();
    }

    /**
     * El Client ID de Google, para que el boton de Google de la
     * pantalla de ingreso sepa a que aplicacion pertenece.
     *
     * VIAJA EN EL HTML Y ES PUBLICO POR DISENO: cualquiera que abra el
     * sitio lo ve, igual que lo veia escrito a mano en cuenta.js:108.
     * El secreto del cliente no esta aca ni hace falta. Lo que cambia
     * respecto de antes es que ahora sale de application.properties, el
     * mismo lugar del que lo lee ServicioGoogle para validar el token:
     * escrito dos veces, un dia uno de los dos queda viejo.
     */
    @ModelAttribute("googleClientId")
    public String googleClientId() {
        return googleClientId;
    }

    /**
     * Los requisitos de la contrasena, para las dos pantallas que los
     * muestran: el alta (/cuenta/registro) y Seguridad
     * (/cuenta/seguridad).
     *
     * Salen de la misma lista con la que UsuarioService controla
     * despues: si manana se suma una quinta condicion, las dos
     * pantallas la muestran sin que nadie se acuerde de venir a
     * tocarlas. Era requisitosPassword() de validacion.js:72.
     */
    @ModelAttribute("requisitosPassword")
    public List<String> requisitosPassword() {
        return UsuarioService.REQUISITOS_PASSWORD.stream()
                .map(UsuarioService.RequisitoPassword::texto)
                .toList();
    }

    private final CarritoSesion carrito;
    private final String googleClientId;

    public ModeloGlobal(CarritoSesion carrito,
                        @Value("${alba.google.client-id:}") String googleClientId) {
        this.carrito = carrito;
        this.googleClientId = googleClientId;
    }
}
