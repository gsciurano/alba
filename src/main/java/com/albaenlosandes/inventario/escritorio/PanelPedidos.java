package com.albaenlosandes.inventario.escritorio;

import com.albaenlosandes.inventario.model.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * PESTANA "PEDIDOS": el corazon de la demostracion.
 *
 * Aca se ven en vivo las cuatro reglas de negocio mas importantes:
 *
 *  1. CHECKOUT       -> valida stock, lo descuenta y calcula el total (el servidor).
 *  2. FOTO DEL PRECIO-> la tabla de abajo compara lo que se PAGO contra el precio
 *                       de HOY. Si cambio, se ve la diferencia: el pedido viejo
 *                       no se altera.
 *  3. CANCELAR       -> devuelve el stock automaticamente y deja un AJUSTE.
 *  4. TODO O NADA    -> si falla la validacion de stock, no se guarda nada.
 */
public class PanelPedidos extends JPanel {

    private final ApiCliente api;
    private final VentanaPrincipal ventana;

    private final DefaultTableModel mPedidos = UI.modelo(
            "N pedido", "Fecha", "Cliente", "Medio de pago", "Total", "Estado", "Renglones");
    private final JTable tPedidos = UI.tabla(mPedidos);

    private final DefaultTableModel mDetalle = UI.modelo(
            "Vino", "Cantidad", "Precio pagado", "Subtotal", "Precio hoy", "Diferencia");
    private final JTable tDetalle = UI.tabla(mDetalle);

    private final JLabel resumen = UI.ayuda(" ");
    private final JLabel tituloDetalle = UI.etiqueta("Elegi un pedido para ver su detalle");

    private List<Pedido> pedidos = List.of();

    public PanelPedidos(ApiCliente api, VentanaPrincipal ventana) {
        this.api = api;
        this.ventana = ventana;
        setLayout(new BorderLayout(0, 10));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        add(construirNorte(), BorderLayout.NORTH);

        JPanel abajo = new JPanel(new BorderLayout(0, 6));
        abajo.setOpaque(false);
        abajo.add(tituloDetalle, BorderLayout.NORTH);
        abajo.add(UI.scroll(tDetalle), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, UI.scroll(tPedidos), abajo);
        split.setResizeWeight(0.55);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);

        add(construirSur(), BorderLayout.SOUTH);

        tPedidos.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) mostrarDetalle();
        });

        recargar();
    }

    private JPanel construirNorte() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(UI.fila(UI.titulo("Pedidos")), BorderLayout.WEST);
        JButton bTodos = UI.boton("Ver todos", false);
        bTodos.addActionListener(e -> recargar());
        p.add(UI.fila(bTodos), BorderLayout.EAST);
        return p;
    }

    private JPanel construirSur() {
        JButton bNuevo     = UI.boton("Nuevo pedido (checkout)", true);
        JButton bEntregado = UI.boton("Marcar ENTREGADO", false);
        JButton bCancelar  = UI.boton("CANCELAR (devuelve stock)", false);

        bNuevo.addActionListener(e -> nuevoPedido());
        bEntregado.addActionListener(e -> cambiarEstado(EstadoPedido.ENTREGADO));
        bCancelar.addActionListener(e -> cambiarEstado(EstadoPedido.CANCELADO));

        JPanel cont = new JPanel(new BorderLayout());
        cont.setOpaque(false);
        cont.add(UI.fila(bNuevo, Box.createHorizontalStrut(16), bEntregado, bCancelar), BorderLayout.WEST);
        JPanel der = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        der.setOpaque(false); der.add(resumen);
        cont.add(der, BorderLayout.EAST);
        return cont;
    }

    // ------------------------------------------------------------------

    public void recargar() {
        cargar(api::listarPedidos, "pedidos");
    }

    /** Lo llama la pestana de clientes con el boton "Ver sus pedidos". */
    public void filtrarPorUsuario(Usuario u) {
        cargar(() -> api.pedidosDeUsuario(u.getIdUsuario()),
                "pedidos de " + u.getNombre() + " " + u.getApellido());
    }

    private interface Consulta { List<Pedido> ejecutar() throws Exception; }

    private void cargar(Consulta c, String descripcion) {
        UI.enSegundoPlano(this, c::ejecutar, lista -> {
            pedidos = lista;
            mPedidos.setRowCount(0);
            for (Pedido p : lista) {
                Usuario u = p.getUsuario();
                mPedidos.addRow(new Object[]{
                        p.getIdPedido(),
                        UI.fecha(p.getFecha()),
                        u == null ? "" : u.getNombre() + " " + u.getApellido(),
                        p.getMedioPago(),
                        UI.pesos(p.getTotal()),
                        p.getEstado(),
                        p.getDetalles() == null ? 0 : p.getDetalles().size()});
            }
            mDetalle.setRowCount(0);
            tituloDetalle.setText("Elegi un pedido para ver su detalle");
            resumen.setText(lista.size() + " " + descripcion);
            ventana.estado("Pedidos actualizados: " + lista.size() + " " + descripcion);
        });
    }

    private Pedido seleccionado() {
        int fila = tPedidos.getSelectedRow();
        if (fila < 0) return null;
        return pedidos.get(tPedidos.convertRowIndexToModel(fila));
    }

    /**
     * Arma la tabla de abajo. La parte interesante son las dos ultimas columnas:
     * comparan el precio congelado en el pedido contra el precio actual del catalogo.
     */
    private void mostrarDetalle() {
        Pedido p = seleccionado();
        mDetalle.setRowCount(0);
        if (p == null) { tituloDetalle.setText("Elegi un pedido para ver su detalle"); return; }

        Map<Integer, BigDecimal> preciosHoy = ventana.getProductosCache().stream()
                .collect(Collectors.toMap(Producto::getIdProducto, Producto::getPrecio, (a, b) -> a));

        for (DetallePedido d : p.getDetalles()) {
            Producto pr = d.getProducto();
            BigDecimal pagado = d.getPrecioUnitario();
            BigDecimal subtotal = pagado.multiply(BigDecimal.valueOf(d.getCantidad()));
            BigDecimal hoy = pr == null ? null : preciosHoy.get(pr.getIdProducto());

            String difer = "-";
            if (hoy != null) {
                int cmp = hoy.compareTo(pagado);
                if (cmp == 0) difer = "sin cambios";
                else difer = (cmp > 0 ? "subio " : "bajo ") + UI.pesos(hoy.subtract(pagado).abs());
            }
            mDetalle.addRow(new Object[]{
                    pr == null ? "" : pr.getNombre(),
                    d.getCantidad(),
                    UI.pesos(pagado),
                    UI.pesos(subtotal),
                    hoy == null ? "-" : UI.pesos(hoy),
                    difer});
        }
        tituloDetalle.setText("Detalle del pedido #" + p.getIdPedido()
                + "   |   Total: " + UI.pesos(p.getTotal())
                + "   |   \"Precio pagado\" quedo congelado al momento de la compra");
    }

    // ------------------------------------------------------------------

    private void nuevoPedido() {
        if (ventana.getUsuariosCache().isEmpty() || ventana.getProductosCache().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Todavia se estan cargando los datos. Proba de nuevo.",
                    "Esperando datos", JOptionPane.WARNING_MESSAGE);
            return;
        }
        DialogoCheckout d = new DialogoCheckout(SwingUtilities.getWindowAncestor(this), api,
                ventana.getUsuariosCache(), ventana.getProductosCache());
        d.setVisible(true);
        if (d.getPedidoCreado() != null) {
            Pedido nuevo = d.getPedidoCreado();
            recargar();
            ventana.recargarTodo();
            UI.info(this, "Pedido #" + nuevo.getIdPedido() + " confirmado.\n\n"
                    + "Total calculado por el servidor: " + UI.pesos(nuevo.getTotal()) + "\n\n"
                    + "El servidor tambien:\n"
                    + "  - descontó el stock de cada vino\n"
                    + "  - guardó el precio de hoy en cada renglón\n"
                    + "  - registró un movimiento de SALIDA por cada vino");
        }
    }

    private void cambiarEstado(EstadoPedido nuevo) {
        Pedido p = seleccionado();
        if (p == null) {
            JOptionPane.showMessageDialog(this, "Primero elegi un pedido de la tabla de arriba.",
                    "Sin seleccion", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String aviso = nuevo == EstadoPedido.CANCELADO
                ? "Cancelar el pedido #" + p.getIdPedido() + ".\n\n"
                  + "El servidor va a DEVOLVER el stock de cada vino automaticamente\n"
                  + "y a registrar un movimiento de AJUSTE por cada uno.\n\n¿Confirmas?"
                : "Marcar el pedido #" + p.getIdPedido() + " como ENTREGADO.\n\n¿Confirmas?";
        if (!UI.confirmar(this, aviso)) return;

        UI.enSegundoPlano(this, () -> api.cambiarEstadoPedido(p.getIdPedido(), nuevo), actualizado -> {
            recargar();
            ventana.recargarTodo();
            ventana.estado("Pedido #" + p.getIdPedido() + " -> " + actualizado.getEstado());
            if (nuevo == EstadoPedido.CANCELADO) {
                UI.info(this, "Pedido cancelado.\n\n"
                        + "Fijate en la pestana Catalogo: el stock volvio a subir.\n"
                        + "Y en Movimientos: aparecen los AJUSTE de la devolucion.");
            }
        });
    }
}
