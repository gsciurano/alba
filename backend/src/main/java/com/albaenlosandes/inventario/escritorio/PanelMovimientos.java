package com.albaenlosandes.inventario.escritorio;

import com.albaenlosandes.inventario.model.MovimientoStock;
import com.albaenlosandes.inventario.model.Producto;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * PESTANA "MOVIMIENTOS": la trazabilidad, o inventario perpetuo.
 *
 * Es la unica pestana SIN botones de crear, editar ni borrar, y eso es a proposito.
 * Es un libro de registro: si se pudiera editar, no serviria para nada.
 * Los movimientos no se cargan a mano; los genera el servidor solo, cada vez
 * que el stock cambia por cualquier motivo:
 *
 *    ENTRADA -> ingreso de mercaderia
 *    SALIDA  -> una venta
 *    AJUSTE  -> una cancelacion o una correccion
 *
 * Si te preguntan "por que esta tabla no tiene CRUD completo", la respuesta
 * es esta: es un registro de auditoria, no un dato que el usuario administre.
 */
public class PanelMovimientos extends JPanel {

    private final ApiCliente api;
    private final VentanaPrincipal ventana;

    private final DefaultTableModel modelo = UI.modelo(
            "ID", "Fecha", "Tipo", "Cantidad", "Vino", "Responsable", "Motivo", "Pedido");
    private final JTable tabla = UI.tabla(modelo);
    private final JLabel resumen = UI.ayuda(" ");
    private final JLabel filtro = UI.etiqueta("Mostrando: todos los movimientos");

    public PanelMovimientos(ApiCliente api, VentanaPrincipal ventana) {
        this.api = api;
        this.ventana = ventana;
        setLayout(new BorderLayout(0, 10));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        JPanel norte = new JPanel(new BorderLayout());
        norte.setOpaque(false);
        JPanel izq = new JPanel(new GridLayout(0, 1, 0, 2));
        izq.setOpaque(false);
        izq.add(UI.titulo("Movimientos de stock (trazabilidad)"));
        izq.add(UI.ayuda("Solo lectura: el servidor los genera solo. No se crean ni se editan a mano."));
        izq.add(filtro);
        norte.add(izq, BorderLayout.WEST);

        JButton bTodos = UI.boton("Ver todos", false);
        bTodos.addActionListener(e -> recargar());
        norte.add(UI.fila(bTodos), BorderLayout.EAST);

        add(norte, BorderLayout.NORTH);
        add(UI.scroll(tabla), BorderLayout.CENTER);

        JPanel sur = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        sur.setOpaque(false); sur.add(resumen);
        add(sur, BorderLayout.SOUTH);

        colorearPorTipo();
        recargar();
    }

    private void colorearPorTipo() {
        tabla.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                                                                     boolean foco, int fila, int col) {
                Component c = super.getTableCellRendererComponent(t, v, sel, foco, fila, col);
                if (!sel) c.setBackground(fila % 2 == 0 ? Color.WHITE : UI.FONDO_SUAVE);
                String tipo = String.valueOf(t.getValueAt(fila, 2));
                if (!sel && (col == 2 || col == 3)) {
                    c.setForeground(switch (tipo) {
                        case "ENTRADA" -> UI.VERDE;
                        case "SALIDA"  -> UI.ROJO;
                        default        -> UI.AMBAR;
                    });
                    setFont(UI.FUENTE_NEG);
                } else {
                    if (!sel) c.setForeground(UI.TEXTO);
                    setFont(UI.FUENTE);
                }
                return c;
            }
        });
    }

    private interface Consulta { List<MovimientoStock> ejecutar() throws Exception; }

    private void cargar(Consulta c, String etiqueta) {
        UI.enSegundoPlano(this, c::ejecutar, lista -> {
            modelo.setRowCount(0);
            for (MovimientoStock m : lista) {
                modelo.addRow(new Object[]{
                        m.getIdMovimiento(),
                        UI.fecha(m.getFecha()),
                        m.getTipo(),
                        (m.getCantidad() != null && m.getCantidad() > 0 ? "+" : "") + m.getCantidad(),
                        m.getProducto() == null ? "" : m.getProducto().getNombre(),
                        m.getUsuario() == null ? "" : m.getUsuario().getNombre() + " " + m.getUsuario().getApellido(),
                        m.getMotivo(),
                        m.getPedidoRelacionado() == null ? "" : "#" + m.getPedidoRelacionado().getIdPedido()});
            }
            filtro.setText("Mostrando: " + etiqueta);
            resumen.setText(lista.size() + " movimientos");
            ventana.estado("Movimientos: " + lista.size() + " (" + etiqueta + ")");
        });
    }

    public void recargar() {
        cargar(api::listarMovimientos, "todos los movimientos");
    }

    /** Lo llama la pestana de catalogo con el boton "Ver movimientos". */
    public void filtrarPorProducto(Producto p) {
        cargar(() -> api.movimientosDeProducto(p.getIdProducto()),
                "historial de " + p.getNombre());
    }
}
