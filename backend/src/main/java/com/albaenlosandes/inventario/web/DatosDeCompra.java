package com.albaenlosandes.inventario.web;

import com.albaenlosandes.inventario.model.MedioPago;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.YearMonth;
import java.util.regex.Pattern;

/**
 * ==================================================================
 *  EL FORMULARIO DEL CHECKOUT
 * ==================================================================
 *
 * DE DONDE SALE. Es el objeto que antes se armaba a mano en
 * js/tienda.js:789 (procesarCheckout) leyendo campo por campo del
 * formulario, y las validaciones son las mismas que hacia la funcion
 * marcar() unas lineas mas abajo.
 *
 * POR QUE AHORA SON ANOTACIONES. Las de antes corrian en el navegador
 * y nada mas: quien mandara el formulario por fuera de la pantalla
 * entraba sin control. Con Bean Validation las mismas reglas corren en
 * el servidor, que es el unico lugar donde una validacion vale. El
 * aviso rapido mientras se escribe no se pierde: lo siguen dando los
 * atributos type="email", required y pattern del HTML.
 *
 * ES UNA CLASE Y NO UN RECORD a proposito: Spring la completa con
 * setters desde el formulario y Thymeleaf la lee con th:field, que
 * necesita un bean con getters y setters. Un record no se puede usar
 * con th:field.
 */
public class DatosDeCompra {

    @Size(min = 3, message = "Ingresá tu nombre completo")
    private String nombre = "";

    @Email(message = "Email inválido")
    @Size(min = 1, message = "Email inválido")
    private String email = "";

    @Size(min = 6, message = "Teléfono inválido")
    private String telefono = "";

    @Size(min = 5, message = "Ingresá una dirección válida")
    private String direccion = "";

    @NotNull(message = "Elegí cómo vas a pagar")
    private MedioPago medioPago = MedioPago.TARJETA;

    /**
     * ==============================================================
     *  LOS DATOS DE LA TARJETA: QUE CAMBIO Y QUE SE PIERDE
     * ==============================================================
     *
     * ESTO ES UNA DIFERENCIA REAL CON LA VERSION ANTERIOR, Y NO ES
     * UNA MEJORA. Antes el numero de tarjeta NO salia del navegador:
     * tienda.js lo validaba de ese lado, usaba los ultimos cuatro
     * digitos para la pantalla de confirmacion y lo tiraba. Ahora el
     * formulario se envia al servidor, asi que el numero viaja.
     *
     * POR QUE PASA. Un checkout dibujado por el servidor es un <form>
     * que se manda entero. Sacar estos tres campos del envio
     * significaria quedarse sin la validacion de Luhn y sin los
     * ultimos cuatro digitos, que son funcionalidades que existen hoy.
     *
     * QUE SE HACE CON ELLOS: se validan, se sacan los ultimos cuatro
     * digitos y se descartan. No se guardan en la base, no se
     * escriben en ningun registro y no se devuelven en ninguna
     * respuesta. La clase no tiene ningun metodo que exponga el numero
     * completo.
     *
     * POR QUE IGUAL NO ALCANZA, dicho claro: en una tienda de verdad
     * los datos de la tarjeta no pasan por el servidor del comercio.
     * Se cargan en un formulario del proveedor de pagos (Mercado Pago,
     * Stripe) embebido en la pagina, y al comercio le llega un identi-
     * ficador, nunca el numero. Mientras esto sea un trabajo practico
     * sin cobro real da igual; el dia que haya un cobro de verdad,
     * esta parte se reemplaza por la pasarela y estos tres campos
     * desaparecen.
     */
    private String tarjetaNumero = "";
    private String tarjetaVence = "";
    private String tarjetaCvv = "";

    /** El codigo promocional tecleado. Puede venir vacio. */
    private String cupon = "";

    // ==============================================================
    //  LAS VALIDACIONES DE LA TARJETA
    // ==============================================================
    //
    // Van aparte y no como anotaciones porque dependen del medio de
    // pago: a quien paga por transferencia no se le puede exigir un
    // numero de tarjeta valido. Una anotacion no puede mirar otro
    // campo sin escribir un validador propio, y para tres reglas no
    // vale la pena.

    public boolean isPagaConTarjeta() {
        return medioPago == MedioPago.TARJETA;
    }

    /**
     * Validacion de Luhn: la misma cuenta que hace el banco antes de
     * cobrar, y la misma funcion que tenia tienda.js:646.
     *
     * Se doblan los digitos en las posiciones impares contando desde
     * la derecha; si el doble pasa de 9 se le restan 9. La suma de
     * todo tiene que ser multiplo de 10.
     */
    public boolean isNumeroDeTarjetaValido() {
        String d = soloDigitos(tarjetaNumero);
        if (d.length() != 16) {
            return false;
        }
        int suma = 0;
        for (int i = 0; i < d.length(); i++) {
            int n = d.charAt(d.length() - 1 - i) - '0';
            if (i % 2 == 1) {
                n *= 2;
                if (n > 9) {
                    n -= 9;
                }
            }
            suma += n;
        }
        return suma % 10 == 0;
    }

    /** MM/AA y que no este vencida. Es vencimientoValido de tienda.js:660. */
    public boolean isVencimientoValido() {
        if (tarjetaVence == null || !FORMA_VENCE.matcher(tarjetaVence.trim()).matches()) {
            return false;
        }
        String[] partes = tarjetaVence.trim().split("/");
        YearMonth vence = YearMonth.of(2000 + Integer.parseInt(partes[1]), Integer.parseInt(partes[0]));
        // Vence el ultimo dia del mes, asi que el mes en curso todavia sirve.
        return !vence.isBefore(YearMonth.now());
    }

    public boolean isCvvValido() {
        return tarjetaCvv != null && tarjetaCvv.trim().matches("\\d{3,4}");
    }

    /**
     * Los ultimos cuatro digitos, lo unico del numero que sobrevive al
     * pedido HTTP. Van a la pantalla de confirmacion: "Pagado con
     * tarjeta terminada en 4242".
     */
    public String ultimosCuatro() {
        String d = soloDigitos(tarjetaNumero);
        return d.length() >= 4 ? d.substring(d.length() - 4) : "";
    }

    private static final Pattern FORMA_VENCE = Pattern.compile("^(0[1-9]|1[0-2])/\\d{2}$");

    private static String soloDigitos(String texto) {
        return texto == null ? "" : texto.replaceAll("\\D", "");
    }

    // ----- getters y setters -----
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public MedioPago getMedioPago() { return medioPago; }
    public void setMedioPago(MedioPago medioPago) { this.medioPago = medioPago; }
    public String getTarjetaNumero() { return tarjetaNumero; }
    public void setTarjetaNumero(String tarjetaNumero) { this.tarjetaNumero = tarjetaNumero; }
    public String getTarjetaVence() { return tarjetaVence; }
    public void setTarjetaVence(String tarjetaVence) { this.tarjetaVence = tarjetaVence; }
    public String getTarjetaCvv() { return tarjetaCvv; }
    public void setTarjetaCvv(String tarjetaCvv) { this.tarjetaCvv = tarjetaCvv; }
    public String getCupon() { return cupon; }
    public void setCupon(String cupon) { this.cupon = cupon; }
}
