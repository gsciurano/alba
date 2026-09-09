package com.albaenlosandes.inventario.escritorio;

import com.albaenlosandes.inventario.model.Linea;
import com.albaenlosandes.inventario.model.Producto;
import com.albaenlosandes.inventario.model.Usuario;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.util.List;

/**
 * PESTANA "CATALOGO": el ABM completo de vinos.
 *
 * Cubre las 4 operaciones CRUD sobre productos:
 *   Crear    -> boton "Nuevo vino"        -> POST   /api/productos
 *   Leer     -> la tabla y los filtros    -> GET    /api/productos...
 *   Modificar-> boton "Editar"            -> PUT    /api/productos/{id}
 *   Borrar   -> boton "Dar de baja"       -> DELETE /api/productos/{id}
 *
 * Ademas muestra dos reglas de negocio del servidor:
 *   - el aviso de stock minimo (fila en ambar)
 *   - el ingreso de mercaderia, que sube stock Y deja trazabilidad
 */
public class PanelProductos extends JPanel {

    private final ApiCliente api;
    private final VentanaPrincipal ventana;

    private final DefaultTableModel modelo = UI.modelo(
            "ID", "Vino", "Linea", "Varietal", "Anada", "Precio", "Stock", "Minimo", "Estado");
    private final JTable tabla = UI.tabla(modelo);

    private final JTextField campoBuscar = new JTextField(16);
    private final JComboBox<Object> comboLinea = new JComboBox<>();
    private final JTextField campoPrecio = new JTextField(8);
    private final JLabel resumen = UI.ayuda(" ");

    /** Copia local de lo ultimo que trajo el servidor, para saber que fila es que vino. */
    private List<Producto> productos = List.of();

    public PanelProductos(ApiCliente api, VentanaPrincipal ventana) {
        this.api = api;
        this.ventana = ventana;
        setLayout(new BorderLayout(0, 10));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        add(construirBarraSuperior(), BorderLayout.NORTH);
        add(UI.scroll(tabla), BorderLayout.CENTER);
        add(construirBarraInferior(), BorderLayout.SOUTH);

        pintarFilasBajoMinimo();
        recargar();
    }

    // ------------------------------------------------------------------
    //  BARRAS
    // ------------------------------------------------------------------

    private JPanel construirBarraSuperior() {
        JPanel cont = new JPanel(new BorderLayout());
        cont.setOpaque(false);

        JPanel izq = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        izq.setOpaque(false);
        izq.add(UI.titulo("Catalogo de vinos"));
        cont.add(izq, BorderLayout.NORTH);

        comboLinea.addItem("Todas las lineas");
        for (Linea l : Linea.values()) comboLinea.addItem(l);
        comboLinea.setFont(UI.FUENTE);

        JButton bBuscar   = UI.boton("Buscar", false);
        JButton bLinea    = UI.boton("Filtrar linea", false);
        JButton bPrecio   = UI.boton("Precio desde", false);
        JButton bAlertas  = UI.boton("Ver solo alertas", false);
        JButton bTodos    = UI.boton("Ver todo", false);

        bBuscar.addActionListener(e -> buscarPorNombre());
        campoBuscar.addActionListener(e -> buscarPorNombre());
        bLinea.addActionListener(e -> filtrarPorLinea());
        bPrecio.addActionListener(e -> filtrarPorPrecio());
        campoPrecio.addActionListener(e -> filtrarPorPrecio());
        bAlertas.addActionListener(e -> cargar(api::alertasStock, "vinos por debajo del stock minimo"));
        bTodos.addActionListener(e -> recargar());

        JPanel filtros = UI.fila(
                UI.etiqueta("Nombre:"), campoBuscar, bBuscar,
                Box.createHorizontalStrut(10),
                UI.etiqueta("Linea:"), comboLinea, bLinea,
                Box.createHorizontalStrut(10),
                UI.etiqueta("Precio desde:"), campoPrecio, bPrecio,
                Box.createHorizontalStrut(10),
                bAlertas, bTodos);
        cont.add(filtros, BorderLayout.CENTER);
        return cont;
    }

    private JPanel construirBarraInferior() {
        JButton bNuevo    = UI.boton("Nuevo vino", true);
        JButton bEditar   = UI.boton("Editar", false);
        JButton bBaja     = UI.boton("Dar de baja", false);
        JButton bIngreso  = UI.boton("Ingresar mercaderia", false);
        JButton bTrazas   = UI.boton("Ver movimientos", false);

        bNuevo.addActionListener(e -> abrirFormulario(null));
        bEditar.addActionListener(e -> { Producto p = seleccionado(); if (p != null) abrirFormulario(p); });
        bBaja.addActionListener(e -> darDeBaja());
        bIngreso.addActionListener(e -> ingresarMercaderia());
        bTrazas.addActionListener(e -> {
            Producto p = seleccionado();
            if (p != null) ventana.mostrarMovimientosDe(p);
        });

        JPanel cont = new JPanel(new BorderLayout());
        cont.setOpaque(false);
        cont.add(UI.fila(bNuevo, bEditar, bBaja, Box.createHorizontalStrut(16), bIngreso, bTrazas),
                BorderLayout.WEST);
        JPanel der = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        der.setOpaque(false);
        der.add(resumen);
        cont.add(der, BorderLayout.EAST);
        return cont;
    }

    // ------------------------------------------------------------------
    //  CARGA DE DATOS
    // ------------------------------------------------------------------

    /** Interfaz corta para poder pasar "que le pido al servidor" como parametro. */
    private interface Consulta { List<Producto> ejecutar() throws Exception; }

    private void cargar(Consulta consulta, String descripcion) {
        UI.enSegundoPlano(this, consulta::ejecutar, lista -> {
            productos = lista;
            modelo.setRowCount(0);
            for (Producto p : lista) {
                boolean bajo = p.getStockActual() != null && p.getStockMinimo() != null
                        && p.getStockActual() < p.getStockMinimo();
                modelo.addRow(new Object[]{
                        p.getIdProducto(),
                        p.getNombre(),
                        p.getLinea(),
                        p.getVarietal(),
                        p.getAnada() == null ? "" : p.getAnada(),
                        UI.pesos(p.getPrecio()),
                        p.getStockActual(),
                        p.getStockMinimo(),
                        bajo ? "REPONER" : "OK"
                });
            }
            resumen.setText(lista.size() + " " + descripcion);
            ventana.estado("Catalogo actualizado: " + lista.size() + " " + descripcion);
        });
    }

    public void recargar() {
        cargar(api::listarProductos, "vinos activos");
    }

    private void buscarPorNombre() {
        String t = campoBuscar.getText().trim();
        if (t.isEmpty()) { recargar(); return; }
        cargar(() -> api.buscarProductos(t), "vinos que contienen \"" + t + "\"");
    }

    private void filtrarPorLinea() {
        Object sel = comboLinea.getSelectedItem();
        if (sel instanceof Linea l) cargar(() -> api.productosPorLinea(l), "vinos de la linea " + l);
        else recargar();
    }

    private void filtrarPorPrecio() {
        String t = campoPrecio.getText().trim().replace(",", ".");
        if (t.isEmpty()) { recargar(); return; }
        try {
            BigDecimal desde = new BigDecimal(t);
            cargar(() -> api.productosDesdePrecio(desde), "vinos de " + UI.pesos(desde) + " para arriba");
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "\"" + t + "\" no es un numero valido.",
                    "Precio invalido", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** Pinta en ambar las filas de vinos que hay que reponer. */
    private void pintarFilasBajoMinimo() {
        tabla.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                                                                     boolean foco, int fila, int col) {
                Component c = super.getTableCellRendererComponent(t, v, sel, foco, fila, col);
                Object estado = t.getValueAt(fila, 8);
                boolean reponer = "REPONER".equals(estado);
                if (!sel) {
                    c.setBackground(reponer ? UI.FONDO_ALERTA
                                            : (fila % 2 == 0 ? Color.WHITE : UI.FONDO_SUAVE));
                    c.setForeground(reponer && col == 8 ? UI.AMBAR : UI.TEXTO);
                }
                setFont(reponer && col == 8 ? UI.FUENTE_NEG : UI.FUENTE);
                return c;
            }
        });
    }

    // ------------------------------------------------------------------
    //  ACCIONES
    // ------------------------------------------------------------------

    /** Traduce la fila elegida en pantalla al Producto correspondiente. */
    private Producto seleccionado() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            JOptionPane.showMessageDialog(this, "Primero elegi un vino de la tabla.",
                    "Sin seleccion", JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        int real = tabla.convertRowIndexToModel(fila);   // por si la tabla esta ordenada
        return productos.get(real);
    }

    private void abrirFormulario(Producto existente) {
        DialogoProducto d = new DialogoProducto(SwingUtilities.getWindowAncestor(this), api, existente);
        d.setVisible(true);
        if (d.seGuardo()) {
            recargar();
            ventana.estado(existente == null ? "Vino creado." : "Vino modificado.");
        }
    }

    private void darDeBaja() {
        Producto p = seleccionado();
        if (p == null) return;
        boolean ok = UI.confirmar(this,
                "Dar de baja \"" + p.getNombre() + "\".\n\n"
                        + "Es una BAJA LOGICA: el vino deja de aparecer en el catalogo\n"
                        + "pero la fila NO se borra de la base. Los pedidos historicos\n"
                        + "que lo incluyen siguen intactos.\n\n¿Confirmas?");
        if (!ok) return;
        UI.enSegundoPlano(this, () -> { api.eliminarProducto(p.getIdProducto()); return null; },
                r -> { recargar(); ventana.estado("Baja logica aplicada a " + p.getNombre()); });
    }

    private void ingresarMercaderia() {
        Producto p = seleccionado();
        if (p == null) return;

        List<Usuario> admins = ventana.getUsuariosCache();
        if (admins.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Todavia no se cargaron los usuarios. Proba de nuevo.",
                    "Esperando datos", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JTextField cantidad = new JTextField("12");
        JTextField motivo   = new JTextField("Reposicion de mercaderia");
        JComboBox<Usuario> quien = new JComboBox<>(admins.toArray(new Usuario[0]));
        quien.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i,
                                                                    boolean s, boolean f) {
                super.getListCellRendererComponent(l, v, i, s, f);
                if (v instanceof Usuario u) setText(u.getNombre() + " " + u.getApellido() + "  (" + u.getRol() + ")");
                return this;
            }
        });

        JPanel form = new JPanel(new GridLayout(0, 1, 4, 4));
        form.add(UI.etiqueta("Vino: " + p.getNombre() + "  (stock actual: " + p.getStockActual() + ")"));
        form.add(UI.etiqueta("Cantidad de botellas que ingresan:")); form.add(cantidad);
        form.add(UI.etiqueta("Motivo:"));                            form.add(motivo);
        form.add(UI.etiqueta("Responsable del movimiento:"));         form.add(quien);

        int r = JOptionPane.showConfirmDialog(this, form, "Ingreso de mercaderia",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;

        int cant;
        try {
            cant = Integer.parseInt(cantidad.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "La cantidad tiene que ser un numero entero.",
                    "Cantidad invalida", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Usuario u = (Usuario) quien.getSelectedItem();

        UI.enSegundoPlano(this,
                () -> api.ingresarStock(p.getIdProducto(), cant, u.getIdUsuario(), motivo.getText().trim()),
                actualizado -> {
                    recargar();
                    ventana.estado("Ingreso registrado: +" + cant + " de " + p.getNombre());
                    UI.info(this, "Stock actualizado: " + actualizado.getStockActual() + " botellas.\n\n"
                            + "El servidor tambien registro un movimiento de ENTRADA.\n"
                            + "Podes verlo en la pestana Movimientos.");
                });
    }
}
