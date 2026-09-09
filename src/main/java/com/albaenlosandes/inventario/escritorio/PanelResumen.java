package com.albaenlosandes.inventario.escritorio;

import com.albaenlosandes.inventario.model.Producto;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;

/**
 * PESTANA "RESUMEN": la pantalla de bienvenida.
 *
 * Sirve para dos cosas en la demostracion:
 *  1. Muestra de un vistazo los numeros del negocio.
 *  2. Deja a la vista el esquema de la arquitectura, que es lo primero
 *     que suelen preguntar.
 */
public class PanelResumen extends JPanel {

    private final VentanaPrincipal ventana;

    private final JLabel valVinos    = valor("-");
    private final JLabel valBotellas = valor("-");
    private final JLabel valValor    = valor("-");
    private final JLabel valAlertas  = valor("-");
    private final JLabel valClientes = valor("-");

    public PanelResumen(VentanaPrincipal ventana) {
        this.ventana = ventana;
        setLayout(new BorderLayout(0, 16));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createEmptyBorder(20, 22, 20, 22));

        add(construirTarjetas(), BorderLayout.NORTH);
        add(construirEsquema(), BorderLayout.CENTER);
    }

    private static JLabel valor(String t) {
        JLabel l = new JLabel(t, SwingConstants.CENTER);
        l.setFont(new Font("SansSerif", Font.BOLD, 24));
        l.setForeground(UI.PRINCIPAL);
        return l;
    }

    private JPanel tarjeta(String titulo, JLabel valor) {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setBackground(UI.FONDO_SUAVE);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UI.BORDE),
                BorderFactory.createEmptyBorder(14, 10, 14, 10)));
        JLabel t = new JLabel(titulo, SwingConstants.CENTER);
        t.setFont(new Font("SansSerif", Font.PLAIN, 12));
        t.setForeground(UI.TEXTO_TENUE);
        p.add(valor, BorderLayout.CENTER);
        p.add(t, BorderLayout.SOUTH);
        return p;
    }

    private JPanel construirTarjetas() {
        JPanel p = new JPanel(new GridLayout(1, 5, 12, 0));
        p.setOpaque(false);
        p.add(tarjeta("vinos en catalogo", valVinos));
        p.add(tarjeta("botellas en stock", valBotellas));
        p.add(tarjeta("valor del inventario", valValor));
        p.add(tarjeta("hay que reponer", valAlertas));
        p.add(tarjeta("usuarios", valClientes));
        return p;
    }

    private JComponent construirEsquema() {
        JTextArea a = new JTextArea("""
            COMO ESTA ARMADO EL SISTEMA

               Esta ventana (Swing)                    Futura pagina web
                        |                                     |
                        +------------- HTTP ------------------+
                                        |
                        API REST  (los @RestController)
                                        |
                        Servicios (@Service)  <-- TODAS las reglas viven aca
                                        |
                        Repositorios (interfaces JpaRepository)
                                        |
                        Hibernate / JPA  (el ORM: traduce objetos <-> tablas)
                                        |
                                     MySQL


            LO IMPORTANTE DE ESTE ESQUEMA

            Esta ventana NO se conecta a MySQL. Le habla al servidor por HTTP,
            exactamente igual que lo hara el navegador en la segunda entrega.

            Por eso esta ventana no tiene ni una regla de negocio adentro:
            no valida stock, no calcula totales, no decide nada. Solo muestra
            lo que el servidor le manda y le pide cosas.

            Consecuencia practica: el dia que cambie una regla, se cambia en un
            solo lugar (el servidor) y las dos interfaces quedan al dia solas.


            QUE MIRAR EN CADA PESTANA

            Catalogo     ABM de vinos, filtros, ingreso de mercaderia y el aviso
                         de stock minimo (las filas en ambar hay que reponerlas).
            Clientes     ABM de usuarios. Fijate que la contrasena no aparece:
                         el servidor directamente no la manda.
            Pedidos      El checkout completo, la comparacion entre el precio
                         pagado y el de hoy, y la cancelacion que devuelve stock.
            Movimientos  El historial. Es de solo lectura a proposito: lo escribe
                         el servidor solo, cada vez que el stock cambia.
            """);
        a.setEditable(false);
        a.setCaretPosition(0);   // que siempre arranque arriba del todo
        a.setFont(new Font("Monospaced", Font.PLAIN, 12));
        a.setForeground(UI.TEXTO);
        a.setBackground(Color.WHITE);
        a.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UI.BORDE),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));
        return UI.scroll(a);
    }

    /** Recalcula los numeros con lo que hay en las caches de la ventana. */
    public void refrescar() {
        var vinos = ventana.getProductosCache();
        valVinos.setText(String.valueOf(vinos.size()));

        int botellas = vinos.stream().mapToInt(p -> p.getStockActual() == null ? 0 : p.getStockActual()).sum();
        valBotellas.setText(String.valueOf(botellas));

        BigDecimal valor = BigDecimal.ZERO;
        int alertas = 0;
        for (Producto p : vinos) {
            if (p.getPrecio() != null && p.getStockActual() != null) {
                valor = valor.add(p.getPrecio().multiply(BigDecimal.valueOf(p.getStockActual())));
            }
            if (p.getStockActual() != null && p.getStockMinimo() != null
                    && p.getStockActual() < p.getStockMinimo()) alertas++;
        }
        valValor.setText(UI.pesosCorto(valor));
        valAlertas.setText(String.valueOf(alertas));
        valClientes.setText(String.valueOf(ventana.getUsuariosCache().size()));
    }
}
