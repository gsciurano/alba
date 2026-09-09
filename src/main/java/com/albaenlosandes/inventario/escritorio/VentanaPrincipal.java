package com.albaenlosandes.inventario.escritorio;

import com.albaenlosandes.inventario.model.Producto;
import com.albaenlosandes.inventario.model.Usuario;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * LA VENTANA. Arma las pestanas y coordina a los paneles.
 *
 * Tambien guarda dos "caches": la lista de usuarios y la de vinos.
 * Sirven para llenar los desplegables (el carrito necesita saber que vinos
 * hay y quienes son los clientes) sin volver a pedirselos al servidor cada
 * vez que se abre un dialogo.
 */
public class VentanaPrincipal extends JFrame {

    private final ApiCliente api;

    private final JTabbedPane pestanas = new JTabbedPane();
    private final JLabel barraEstado = new JLabel(" ");
    private final JLabel etiquetaConexion = new JLabel();

    private PanelResumen panelResumen;
    private PanelProductos panelProductos;
    private PanelUsuarios panelUsuarios;
    private PanelPedidos panelPedidos;
    private PanelMovimientos panelMovimientos;
    private PanelSolicitudes panelSolicitudes;

    private List<Usuario> usuariosCache = List.of();
    private List<Producto> productosCache = List.of();

    public VentanaPrincipal(ApiCliente api) {
        super("Gestor de Inventario - Alba en los Andes");
        this.api = api;

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1180, 720);
        setMinimumSize(new Dimension(980, 600));
        setLocationRelativeTo(null);

        add(construirEncabezado(), BorderLayout.NORTH);
        add(construirPestanas(), BorderLayout.CENTER);
        add(construirBarraEstado(), BorderLayout.SOUTH);

        cargarCaches();
    }

    // ------------------------------------------------------------------

    private JPanel construirEncabezado() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(UI.PRINCIPAL);
        p.setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18));

        JPanel izq = new JPanel(new GridLayout(0, 1, 0, 2));
        izq.setOpaque(false);
        JLabel t = new JLabel("ALBA EN LOS ANDES");
        t.setFont(new Font("SansSerif", Font.BOLD, 20));
        t.setForeground(Color.WHITE);
        JLabel s = new JLabel("Gestor de inventario y ventas");
        s.setFont(new Font("SansSerif", Font.PLAIN, 13));
        s.setForeground(UI.PRINCIPAL_SUAVE);
        izq.add(t); izq.add(s);
        p.add(izq, BorderLayout.WEST);

        etiquetaConexion.setFont(new Font("SansSerif", Font.PLAIN, 12));
        etiquetaConexion.setForeground(UI.PRINCIPAL_SUAVE);
        etiquetaConexion.setHorizontalAlignment(SwingConstants.RIGHT);
        p.add(etiquetaConexion, BorderLayout.EAST);
        return p;
    }

    private JTabbedPane construirPestanas() {
        panelResumen     = new PanelResumen(this);
        panelProductos   = new PanelProductos(api, this);
        panelUsuarios    = new PanelUsuarios(api, this);
        panelPedidos     = new PanelPedidos(api, this);
        panelMovimientos = new PanelMovimientos(api, this);
        panelSolicitudes = new PanelSolicitudes(api, this);

        pestanas.setFont(UI.FUENTE_NEG);
        pestanas.addTab("  Resumen  ", panelResumen);
        pestanas.addTab("  Catalogo  ", panelProductos);
        pestanas.addTab("  Clientes  ", panelUsuarios);
        pestanas.addTab("  Pedidos  ", panelPedidos);
        pestanas.addTab("  Movimientos  ", panelMovimientos);
        pestanas.addTab("  Cancelaciones  ", panelSolicitudes);
        return pestanas;
    }

    private JPanel construirBarraEstado() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(UI.FONDO_SUAVE);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UI.BORDE),
                BorderFactory.createEmptyBorder(6, 14, 6, 14)));
        barraEstado.setFont(new Font("SansSerif", Font.PLAIN, 12));
        barraEstado.setForeground(UI.TEXTO_TENUE);
        p.add(barraEstado, BorderLayout.WEST);

        JButton bRecargar = UI.boton("Recargar todo", false);
        bRecargar.addActionListener(e -> recargarTodo());
        JPanel der = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        der.setOpaque(false); der.add(bRecargar);
        p.add(der, BorderLayout.EAST);
        return p;
    }

    // ------------------------------------------------------------------
    //  ESTADO Y CACHES
    // ------------------------------------------------------------------

    public void estado(String texto) {
        barraEstado.setText(texto);
    }

    public void conexionOk(boolean ok) {
        etiquetaConexion.setText((ok ? "Conectado a  " : "SIN CONEXION con  ") + api.getBaseUrl());
    }

    public List<Usuario> getUsuariosCache()  { return usuariosCache; }
    public List<Producto> getProductosCache() { return productosCache; }
    public void setUsuariosCache(List<Usuario> l)  { this.usuariosCache = l; }

    /** Trae usuarios y vinos para llenar los desplegables de los formularios. */
    public void cargarCaches() {
        UI.enSegundoPlano(this, api::listarUsuarios, l -> { usuariosCache = l; panelResumen.refrescar(); });
        UI.enSegundoPlano(this, api::listarProductos, l -> { productosCache = l; panelResumen.refrescar(); });
    }

    /** Refresca las cuatro pestanas. Se usa despues de un checkout o una cancelacion. */
    public void recargarTodo() {
        cargarCaches();
        panelProductos.recargar();
        panelUsuarios.recargar();
        panelPedidos.recargar();
        panelMovimientos.recargar();
        panelSolicitudes.recargar();
        estado("Todo actualizado desde el servidor.");
    }

    // ------------------------------------------------------------------
    //  NAVEGACION ENTRE PESTANAS
    // ------------------------------------------------------------------

    public void mostrarMovimientosDe(Producto p) {
        panelMovimientos.filtrarPorProducto(p);
        pestanas.setSelectedIndex(4);
    }

    public void mostrarPedidosDe(Usuario u) {
        panelPedidos.filtrarPorUsuario(u);
        pestanas.setSelectedIndex(3);
    }

}
