package com.albaenlosandes.inventario.escritorio;

import com.albaenlosandes.inventario.model.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * EL CARRITO. Es la pantalla mas importante para la defensa.
 *
 * Lo que se manda al servidor es a proposito MUY poco:
 *
 *     { "usuario":   { "idUsuario": 2 },
 *       "medioPago": "TARJETA",
 *       "detalles":  [ { "producto": {"idProducto": 5}, "cantidad": 2 } ] }
 *
 * NO se manda el precio. NO se manda el total. NO se manda la fecha.
 * Todo eso lo decide el servidor. Si el cliente pudiera mandar el precio,
 * cualquiera podria comprarse un Gran Reserva a un peso cambiando el JSON.
 *
 * El total que se ve aca abajo es solo una PREVISUALIZACION para el usuario.
 * El total de verdad, el que se guarda, lo calcula PedidoService.
 */
public class DialogoCheckout extends JDialog {

    private final ApiCliente api;
    private final List<Producto> catalogo;

    private final JComboBox<Usuario> cbCliente;
    private final JComboBox<MedioPago> cbMedioPago = new JComboBox<>(MedioPago.values());
    private final JComboBox<Producto> cbVino;
    private final JSpinner spCantidad = new JSpinner(new SpinnerNumberModel(1, 1, 9999, 1));

    private final DefaultTableModel mCarrito = UI.modelo("Vino", "Cantidad", "Precio unitario", "Subtotal");
    private final JTable tCarrito = UI.tabla(mCarrito);

    /** idProducto -> cantidad. LinkedHashMap para conservar el orden en que se agregaron. */
    private final Map<Integer, Integer> lineas = new LinkedHashMap<>();

    private final JLabel lbTotal = new JLabel("Total estimado: $ 0,00");
    private Pedido pedidoCreado = null;

    public DialogoCheckout(Window padre, ApiCliente api, List<Usuario> usuarios, List<Producto> catalogo) {
        super(padre, "Nuevo pedido - checkout", ModalityType.APPLICATION_MODAL);
        this.api = api;
        this.catalogo = catalogo;

        cbCliente = new JComboBox<>(usuarios.toArray(new Usuario[0]));
        cbCliente.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                super.getListCellRendererComponent(l, v, i, s, f);
                if (v instanceof Usuario u) setText(u.getNombre() + " " + u.getApellido() + "  <" + u.getEmail() + ">");
                return this;
            }
        });

        cbVino = new JComboBox<>(catalogo.toArray(new Producto[0]));
        cbVino.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                super.getListCellRendererComponent(l, v, i, s, f);
                if (v instanceof Producto p) setText(p.getNombre() + "   " + UI.pesos(p.getPrecio())
                        + "   (stock: " + p.getStockActual() + ")");
                return this;
            }
        });

        setContentPane(construir());
        pack();
        setSize(Math.max(getWidth(), 760), Math.max(getHeight(), 520));
        setLocationRelativeTo(padre);
    }

    public Pedido getPedidoCreado() { return pedidoCreado; }

    private JPanel construir() {
        // ---- Arriba: quien compra y como paga ----
        JPanel cabecera = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        cabecera.setBackground(Color.WHITE);
        cabecera.add(UI.etiqueta("Cliente:"));   cabecera.add(cbCliente);
        cabecera.add(Box.createHorizontalStrut(12));
        cabecera.add(UI.etiqueta("Medio de pago:")); cabecera.add(cbMedioPago);

        // ---- Medio: agregar renglones ----
        JButton bAgregar = UI.boton("Agregar al carrito", false);
        JButton bQuitar  = UI.boton("Quitar renglon", false);
        bAgregar.addActionListener(e -> agregar());
        bQuitar.addActionListener(e -> quitar());

        JPanel alta = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        alta.setBackground(Color.WHITE);
        alta.add(UI.etiqueta("Vino:")); alta.add(cbVino);
        alta.add(UI.etiqueta("Cantidad:")); alta.add(spCantidad);
        alta.add(bAgregar);
        alta.add(bQuitar);

        JPanel norte = new JPanel(new GridLayout(0, 1));
        norte.setBackground(Color.WHITE);
        norte.add(cabecera);
        norte.add(alta);

        // ---- Abajo: total y botones ----
        lbTotal.setFont(UI.FUENTE_TITULO);
        lbTotal.setForeground(UI.PRINCIPAL);

        JButton bConfirmar = UI.boton("Confirmar pedido", true);
        JButton bCancelar  = UI.boton("Cancelar", false);
        bConfirmar.addActionListener(e -> confirmar());
        bCancelar.addActionListener(e -> dispose());

        JPanel pie = new JPanel(new BorderLayout());
        pie.setBackground(UI.FONDO_SUAVE);
        pie.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        JPanel izq = new JPanel(new GridLayout(0, 1, 0, 2));
        izq.setOpaque(false);
        izq.add(lbTotal);
        izq.add(UI.ayuda("Estimado local. El total definitivo lo calcula el servidor con los precios de la base."));
        pie.add(izq, BorderLayout.WEST);
        JPanel der = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        der.setOpaque(false);
        der.add(bCancelar); der.add(bConfirmar);
        pie.add(der, BorderLayout.EAST);

        JPanel raiz = new JPanel(new BorderLayout(0, 6));
        raiz.setBackground(Color.WHITE);
        raiz.setBorder(BorderFactory.createEmptyBorder(10, 12, 0, 12));
        raiz.add(norte, BorderLayout.NORTH);
        raiz.add(UI.scroll(tCarrito), BorderLayout.CENTER);
        raiz.add(pie, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(bConfirmar);
        return raiz;
    }

    private Producto buscar(Integer id) {
        return catalogo.stream().filter(p -> p.getIdProducto().equals(id)).findFirst().orElse(null);
    }

    private void agregar() {
        Producto p = (Producto) cbVino.getSelectedItem();
        if (p == null) return;
        int cant = (Integer) spCantidad.getValue();
        lineas.merge(p.getIdProducto(), cant, Integer::sum);
        refrescarCarrito();
    }

    private void quitar() {
        int fila = tCarrito.getSelectedRow();
        if (fila < 0) return;
        Integer id = new java.util.ArrayList<>(lineas.keySet()).get(tCarrito.convertRowIndexToModel(fila));
        lineas.remove(id);
        refrescarCarrito();
    }

    private void refrescarCarrito() {
        mCarrito.setRowCount(0);
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Integer, Integer> e : lineas.entrySet()) {
            Producto p = buscar(e.getKey());
            if (p == null) continue;
            BigDecimal sub = p.getPrecio().multiply(BigDecimal.valueOf(e.getValue()));
            total = total.add(sub);
            mCarrito.addRow(new Object[]{p.getNombre(), e.getValue(), UI.pesos(p.getPrecio()), UI.pesos(sub)});
        }
        lbTotal.setText("Total estimado: " + UI.pesos(total));
    }

    private void confirmar() {
        if (lineas.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "El carrito esta vacio.\n\nSi lo mandaras igual, el servidor lo rechazaria con\n"
                            + "\"El pedido debe tener al menos un producto\".",
                    "Carrito vacio", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Usuario cliente = (Usuario) cbCliente.getSelectedItem();
        MedioPago medio = (MedioPago) cbMedioPago.getSelectedItem();

        UI.enSegundoPlano(this,
                () -> api.crearPedido(cliente.getIdUsuario(), medio, lineas),
                creado -> { pedidoCreado = creado; dispose(); });
    }
}
