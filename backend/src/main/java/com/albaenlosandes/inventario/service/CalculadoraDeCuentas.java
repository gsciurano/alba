package com.albaenlosandes.inventario.service;

import com.albaenlosandes.inventario.service.Cupones.Cupon;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * ==================================================================
 *  LAS CUENTAS DE UN PEDIDO
 * ==================================================================
 *
 * DE DONDE SALE ESTA CLASE. Es la traduccion de
 * js/tienda.js:337 (calcularTotales), que corria en el navegador.
 *
 * POR QUE SE MUDA. No por prolijidad: porque de ese lado el total era
 * una sugerencia. El navegador calculaba subtotal, descuento, envio e
 * impuestos, los mostraba, y despues le mandaba al servidor solamente
 * que vinos y cuantos. El servidor sumaba los vinos y guardaba eso
 * como 'total'. Las tres consecuencias:
 *
 *   1. la base guardaba un importe distinto al que se le cobro;
 *   2. el comprobante por correo salia con el numero de la base, asi
 *      que el cliente recibia un mail que no coincidia con la
 *      pantalla que acababa de ver;
 *   3. el descuento no quedaba registrado en ningun lugar.
 *
 * Ahora la cuenta se hace una sola vez, aca, y el mismo resultado es
 * el que se dibuja en pantalla y el que se guarda. No hay dos
 * versiones del total porque no hay dos lugares donde se calcule.
 *
 * EL ORDEN DE LA CUENTA (el mismo que tenia el navegador):
 *
 *     subtotal   suma de precio x cantidad de cada renglon
 *     descuento  lo que saca el cupon, tope en el subtotal
 *     gravado    subtotal - descuento
 *     envio      fijo, si hay algo que enviar
 *     impuestos  21% del gravado
 *     total      gravado + envio + impuestos
 *
 * Primero se descuenta y despues se grava: el impuesto se calcula
 * sobre lo que efectivamente se cobra por los vinos, no sobre el
 * precio de lista. Y el envio queda fuera del 21%, que es como estaba
 * antes.
 */
@Service
public class CalculadoraDeCuentas {

    /**
     * Costo de envio, fijo para todo el pais.
     *
     * Era la constante COSTO_ENVIO de js/tienda.js:25. Que el valor
     * sea fijo y no dependa del destino es una simplificacion que ya
     * existia; lo que cambia es que ahora el cliente no puede
     * modificarlo.
     */
    public static final BigDecimal COSTO_ENVIO = new BigDecimal("2500");

    /** TASA_IMPUESTO de js/tienda.js:26. El 21% del IVA argentino. */
    public static final BigDecimal TASA_IMPUESTO = new BigDecimal("0.21");

    /**
     * El resultado: las cinco columnas de plata del pedido, mas el
     * codigo del cupon que se aplico (null si no se aplico ninguno).
     *
     * Es un record porque es un valor, no un objeto con vida: se
     * calcula, se muestra, se guarda y se tira. Dos cuentas con los
     * mismos numeros son la misma cuenta.
     */
    public record Cuentas(BigDecimal subtotal,
                          BigDecimal descuento,
                          String cupon,
                          BigDecimal envio,
                          BigDecimal impuestos,
                          BigDecimal total) {

        /** Para las plantillas: el renglon del descuento solo se dibuja si hay descuento. */
        public boolean isConDescuento() {
            return descuento.signum() > 0;
        }
    }

    /**
     * Calcula sobre un subtotal ya sumado.
     *
     * @param subtotal suma de los renglones, nunca null
     * @param cupon    el cupon validado, o null
     */
    public Cuentas calcular(BigDecimal subtotal, Cupon cupon) {
        BigDecimal base = subtotal == null ? BigDecimal.ZERO : subtotal;

        BigDecimal descuento = BigDecimal.ZERO;
        if (cupon != null) {
            // El tope es el subtotal. Sin el, BIENVENIDA ($5.000 fijos)
            // sobre un carrito de $3.000 dejaria el gravado en -2.000 y
            // el impuesto en negativo: la bodega terminaria pagandole
            // al cliente por comprar.
            descuento = cupon.descuentoSobre(base).min(base);
        }

        BigDecimal gravado   = base.subtract(descuento);
        BigDecimal envio     = base.signum() > 0 ? COSTO_ENVIO : BigDecimal.ZERO;
        BigDecimal impuestos = gravado.multiply(TASA_IMPUESTO).setScale(0, RoundingMode.HALF_UP);

        return new Cuentas(
                base,
                descuento,
                cupon != null ? cupon.codigo() : null,
                envio,
                impuestos,
                gravado.add(envio).add(impuestos));
    }

    /** Las cuentas de un carrito vacio: todo en cero. */
    public Cuentas vacias() {
        return calcular(BigDecimal.ZERO, null);
    }
}
