package com.albaenlosandes.inventario.escritorio;

import com.albaenlosandes.inventario.model.*;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * PESTANA "CANCELACIONES": la ficha del reclamo, para la bodega.
 *
 * ESTA PANTALLA NO ARMA SOLICITUDES. El cliente las manda desde la pagina
 * web, con su formulario y sus preguntas frecuentes. Aca la bodega ve lo
 * que llego y lo resuelve.
 *
 * Muestra tres cosas, que son las que hacen falta para poder comunicarse
 * con la persona y solucionarle el problema:
 *
 *   1. DATOS DE CONTACTO  -> nombre, email y telefono de quien cancelo
 *   2. EL FORMULARIO      -> el motivo que eligio y lo que escribio
 *   3. EL PEDIDO          -> numero, fecha, total, estado y que vinos eran
 *
 * Lo unico que se puede hacer desde aca es responderle: aprobar o rechazar.
 */
public class PanelSolicitudes extends JPanel {

    private final ApiCliente api;
    private final VentanaPrincipal ventana;

    private final DefaultTableModel modelo = UI.modelo(
            "N", "Pedido", "Cliente", "Motivo", "Estado", "Pedida el", "Resuelta el", "Resolvio");
    private final JTable tabla = UI.tabla(modelo);

    private final JTextArea ficha = new JTextArea();
    private final JLabel resumen = UI.ayuda(" ");
    private final JLabel filtro = UI.etiqueta("Mostrando: todas las solicitudes");

    private List<SolicitudCancelacion> solicitudes = List.of();

    public PanelSolicitudes(ApiCliente api, VentanaPrincipal ventana) {
        this.api = api;
        this.ventana = ventana;
        setLayout(new BorderLayout(0, 10));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        add(construirNorte(), BorderLayout.NORTH);

        ficha.setEditable(false);
        ficha.setFont(new Font("Monospaced", Font.PLAIN, 12));
        ficha.setBackground(Color.WHITE);
        ficha.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        ficha.setText("  Elegi una solicitud de la tabla para ver los datos de contacto\n"
                    + "  y el formulario que completo el cliente.");

        JPanel abajo = new JPanel(new BorderLayout(0, 6));
        abajo.setOpaque(false);
        abajo.add(UI.etiqueta("Ficha del reclamo"), BorderLayout.NORTH);
        abajo.add(UI.scroll(ficha), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, UI.scroll(tabla), abajo);
        split.setResizeWeight(0.42);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);

        add(construirSur(), BorderLayout.SOUTH);

        tabla.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) mostrarFicha();
        });

        colorearPorEstado();
        recargar();
    }

    // ------------------------------------------------------------------

    private JPanel construirNorte() {
        JPanel izq = new JPanel(new GridLayout(0, 1, 0, 2));
        izq.setOpaque(false);
        izq.add(UI.titulo("Solicitudes de cancelacion"));
        izq.add(UI.ayuda("Las manda el cliente desde la pagina web. Aca se ven sus datos "
                + "de contacto y lo que completo, para poder comunicarse y resolverlo."));
        izq.add(filtro);

        JButton bTodas     = UI.boton("Ver todas", false);
        JButton bPendientes= UI.boton("Solo las que esperan respuesta", false);
        JButton bCliente   = UI.boton("Filtrar por cliente", false);
        bTodas.addActionListener(e -> recargar());
        bPendientes.addActionListener(e -> cargar(api::solicitudesPendientes, "las que esperan respuesta"));
        bCliente.addActionListener(e -> filtrarPorCliente());

        // Los botones van en su PROPIA fila, debajo del texto: si van al costado
        // pisan la explicacion cuando la ventana no es muy ancha.
        JPanel norte = new JPanel(new BorderLayout(0, 4));
        norte.setOpaque(false);
        norte.add(izq, BorderLayout.NORTH);
        norte.add(UI.fila(bTodas, bPendientes, bCliente), BorderLayout.CENTER);
        return norte;
    }

    private JPanel construirSur() {
        JButton bAprobar  = UI.boton("Aprobar y cancelar el pedido", true);
        JButton bRechazar = UI.boton("Rechazar", false);
        JButton bCopiar   = UI.boton("Copiar email del cliente", false);

        bAprobar.addActionListener(e -> resolver(true));
        bRechazar.addActionListener(e -> resolver(false));
        bCopiar.addActionListener(e -> copiarEmail());

        JPanel cont = new JPanel(new BorderLayout());
        cont.setOpaque(false);
        cont.add(UI.fila(bAprobar, bRechazar, Box.createHorizontalStrut(16), bCopiar), BorderLayout.WEST);
        JPanel der = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        der.setOpaque(false); der.add(resumen);
        cont.add(der, BorderLayout.EAST);
        return cont;
    }

    private void colorearPorEstado() {
        tabla.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                                                                     boolean foco, int fila, int col) {
                Component c = super.getTableCellRendererComponent(t, v, sel, foco, fila, col);
                String estado = String.valueOf(t.getValueAt(fila, 4));
                boolean abierta = "PENDIENTE".equals(estado) || "EN_REVISION".equals(estado);
                if (!sel) {
                    c.setBackground(abierta ? UI.FONDO_ALERTA
                                            : (fila % 2 == 0 ? Color.WHITE : UI.FONDO_SUAVE));
                    c.setForeground(col == 4 ? switch (estado) {
                        case "APROBADA"  -> UI.VERDE;
                        case "RECHAZADA" -> UI.ROJO;
                        default          -> UI.AMBAR;
                    } : UI.TEXTO);
                }
                setFont(col == 4 ? UI.FUENTE_NEG : UI.FUENTE);
                return c;
            }
        });
    }

    // ------------------------------------------------------------------

    private interface Consulta { List<SolicitudCancelacion> ejecutar() throws Exception; }

    private void cargar(Consulta c, String etiqueta) {
        UI.enSegundoPlano(this, c::ejecutar, lista -> {
            solicitudes = lista;
            modelo.setRowCount(0);
            for (SolicitudCancelacion s : lista) {
                Usuario u = s.getUsuario();
                modelo.addRow(new Object[]{
                        s.getIdSolicitud(),
                        s.getPedido() == null ? "" : "#" + s.getPedido().getIdPedido(),
                        u == null ? "" : u.getNombre() + " " + u.getApellido(),
                        s.getMotivo(),
                        s.getEstado(),
                        UI.fecha(s.getFechaSolicitud()),
                        UI.fecha(s.getFechaResolucion()),
                        Boolean.TRUE.equals(s.getResueltaAutomaticamente()) ? "el sistema"
                                : (s.getResueltoPor() == null ? "" : s.getResueltoPor().getNombre())});
            }
            ficha.setText("  Elegi una solicitud de la tabla para ver los datos de contacto\n"
                        + "  y el formulario que completo el cliente.");
            filtro.setText("Mostrando: " + etiqueta);

            long abiertas = lista.stream().filter(SolicitudCancelacion::estaAbierta).count();
            resumen.setText(lista.size() + " solicitudes  |  " + abiertas + " esperando respuesta");
            ventana.estado("Cancelaciones: " + lista.size() + " (" + etiqueta + ")");
        });
    }

    public void recargar() {
        cargar(api::listarSolicitudes, "todas las solicitudes");
    }

    private SolicitudCancelacion seleccionada() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) return null;
        return solicitudes.get(tabla.convertRowIndexToModel(fila));
    }

    /**
     * Arma la ficha completa del reclamo: con quien hablar, por que motivo
     * y sobre que pedido.
     */
    private void mostrarFicha() {
        SolicitudCancelacion s = seleccionada();
        if (s == null) return;

        Usuario u = s.getUsuario();
        Pedido p = s.getPedido();
        StringBuilder b = new StringBuilder();

        b.append("SOLICITUD #").append(s.getIdSolicitud())
         .append("        estado: ").append(s.getEstado())
         .append("        recibida: ").append(UI.fecha(s.getFechaSolicitud())).append("\n");
        b.append("=".repeat(96)).append("\n\n");

        // ---- 1. Con quien hay que comunicarse ----
        b.append("DATOS DE CONTACTO DEL CLIENTE\n");
        if (u == null) {
            b.append("   (sin datos)\n");
        } else {
            b.append("   Nombre .... ").append(u.getNombre()).append(" ").append(u.getApellido()).append("\n");
            b.append("   Email ..... ").append(UI.nvl(u.getEmail())).append("\n");
            b.append("   Telefono .. ").append(u.getTelefono() == null || u.getTelefono().isBlank()
                    ? "(no cargado)" : u.getTelefono()).append("\n");
            b.append("   Cliente desde ").append(UI.fecha(u.getFechaAlta()))
             .append("     rol: ").append(u.getRol()).append("\n");
        }
        b.append("\n");

        // ---- 2. Lo que completo en el formulario ----
        b.append("FORMULARIO QUE COMPLETO\n");
        b.append("   Motivo elegido ... ").append(s.getMotivo()).append("\n");
        b.append("   Lo que escribio:\n");
        String com = (s.getComentario() == null || s.getComentario().isBlank())
                ? "(no escribio nada)" : s.getComentario();
        for (String linea : envolver(com, 84)) b.append("      ").append(linea).append("\n");
        b.append("\n");

        // ---- 3. El pedido en cuestion ----
        b.append("PEDIDO RECLAMADO\n");
        if (p == null) {
            b.append("   (sin datos)\n");
        } else {
            b.append("   Numero .... #").append(p.getIdPedido())
             .append("        fecha: ").append(UI.fecha(p.getFecha()))
             .append("        estado actual: ").append(p.getEstado()).append("\n");
            b.append("   Total ..... ").append(UI.pesos(p.getTotal()))
             .append("        pago: ").append(p.getMedioPago()).append("\n");
            if (p.getDetalles() != null && !p.getDetalles().isEmpty()) {
                b.append("   Contenido:\n");
                for (DetallePedido d : p.getDetalles()) {
                    b.append("      ").append(d.getCantidad()).append(" x ")
                     .append(d.getProducto() == null ? "" : d.getProducto().getNombre())
                     .append("   @ ").append(UI.pesos(d.getPrecioUnitario())).append("\n");
                }
            }
        }
        b.append("\n");

        // ---- 4. Como se resolvio (si ya se resolvio) ----
        b.append("RESOLUCION\n");
        if (s.estaAbierta()) {
            b.append("   Todavia sin responder. Usa los botones de abajo para aprobarla o rechazarla.\n");
        } else {
            b.append("   Estado .... ").append(s.getEstado())
             .append("        el ").append(UI.fecha(s.getFechaResolucion())).append("\n");
            b.append("   Resolvio .. ").append(Boolean.TRUE.equals(s.getResueltaAutomaticamente())
                    ? "el sistema (cancelacion automatica dentro de las 24 horas)"
                    : (s.getResueltoPor() == null ? "-"
                       : s.getResueltoPor().getNombre() + " " + s.getResueltoPor().getApellido())).append("\n");
            b.append("   Respuesta que se le dio:\n");
            for (String linea : envolver(UI.nvl(s.getRespuesta()), 84)) {
                b.append("      ").append(linea).append("\n");
            }
        }

        ficha.setText(b.toString());
        ficha.setCaretPosition(0);
    }


    /** Parte un texto largo en lineas, para que la ficha se lea prolija. */
    private static List<String> envolver(String texto, int ancho) {
        java.util.List<String> lineas = new java.util.ArrayList<>();
        for (String parrafo : texto.split("\n")) {
            StringBuilder actual = new StringBuilder();
            for (String palabra : parrafo.split("\\s+")) {
                if (actual.length() + palabra.length() + 1 > ancho) {
                    lineas.add(actual.toString());
                    actual = new StringBuilder();
                }
                if (!actual.isEmpty()) actual.append(' ');
                actual.append(palabra);
            }
            lineas.add(actual.toString());
        }
        return lineas;
    }

    // ------------------------------------------------------------------

    private void copiarEmail() {
        SolicitudCancelacion s = seleccionada();
        if (s == null || s.getUsuario() == null) {
            JOptionPane.showMessageDialog(this, "Primero elegi una solicitud de la tabla.",
                    "Sin seleccion", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String email = s.getUsuario().getEmail();
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(email), null);
        ventana.estado("Email copiado al portapapeles: " + email);
    }

    /** Responderle al cliente: aprobar (cancela el pedido) o rechazar. */
    private void resolver(boolean aprobar) {
        SolicitudCancelacion s = seleccionada();
        if (s == null) {
            JOptionPane.showMessageDialog(this, "Primero elegi una solicitud de la tabla.",
                    "Sin seleccion", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (!s.estaAbierta()) {
            JOptionPane.showMessageDialog(this,
                    "La solicitud #" + s.getIdSolicitud() + " ya fue resuelta (" + s.getEstado() + ").",
                    "Ya resuelta", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        List<Usuario> admins = ventana.getUsuariosCache().stream()
                .filter(u -> u.getRol() == Rol.ADMIN).toList();
        if (admins.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No hay ningun usuario ADMIN cargado.",
                    "Sin administrador", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JTextArea txt = new JTextArea(4, 36);
        txt.setLineWrap(true); txt.setWrapStyleWord(true); txt.setFont(UI.FUENTE);
        txt.setText(aprobar
                ? "Aprobamos tu cancelacion. El pedido queda cancelado y se te reintegra el total."
                : "No podemos cancelar este pedido porque ya esta en preparacion. "
                  + "Te contactamos para coordinar una solucion.");

        JComboBox<Usuario> cbAdmin = new JComboBox<>(admins.toArray(new Usuario[0]));
        cbAdmin.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i,
                                                                    boolean se, boolean f) {
                super.getListCellRendererComponent(l, v, i, se, f);
                if (v instanceof Usuario u) setText(u.getNombre() + " " + u.getApellido());
                return this;
            }
        });

        JPanel arriba = new JPanel(new GridLayout(0, 1, 0, 3));
        arriba.add(UI.etiqueta((aprobar ? "APROBAR" : "RECHAZAR") + " la solicitud #" + s.getIdSolicitud()
                + "   (pedido #" + s.getPedido().getIdPedido() + ")"));
        if (aprobar) arriba.add(UI.ayuda("Al aprobarla, el servidor cancela el pedido y el stock vuelve solo."));
        arriba.add(UI.etiqueta("Respuesta para el cliente:"));

        JPanel form = new JPanel(new BorderLayout(0, 6));
        form.add(arriba, BorderLayout.NORTH);
        form.add(new JScrollPane(txt), BorderLayout.CENTER);
        JPanel pie = new JPanel(new BorderLayout(6, 0));
        pie.add(UI.etiqueta("Resuelve:"), BorderLayout.WEST);
        pie.add(cbAdmin, BorderLayout.CENTER);
        form.add(pie, BorderLayout.SOUTH);

        if (JOptionPane.showConfirmDialog(this, form, aprobar ? "Aprobar" : "Rechazar",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;

        Usuario admin = (Usuario) cbAdmin.getSelectedItem();
        UI.enSegundoPlano(this,
                () -> api.resolverSolicitud(s.getIdSolicitud(), aprobar, txt.getText().trim(),
                        admin.getIdUsuario()),
                r -> {
                    recargar();
                    ventana.recargarTodo();
                    UI.info(this, "Solicitud #" + r.getIdSolicitud() + " -> " + r.getEstado()
                            + (aprobar ? "\n\nEl pedido quedo cancelado y el stock volvio al inventario." : ""));
                });
    }

    private void filtrarPorCliente() {
        List<Usuario> usuarios = ventana.getUsuariosCache();
        if (usuarios.isEmpty()) return;
        JComboBox<Usuario> cb = new JComboBox<>(usuarios.toArray(new Usuario[0]));
        cb.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i,
                                                                    boolean s, boolean f) {
                super.getListCellRendererComponent(l, v, i, s, f);
                if (v instanceof Usuario u) setText(u.getNombre() + " " + u.getApellido()
                        + "   <" + u.getEmail() + ">");
                return this;
            }
        });
        if (JOptionPane.showConfirmDialog(this, cb, "Ver las solicitudes de un cliente",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        Usuario u = (Usuario) cb.getSelectedItem();
        cargar(() -> api.solicitudesDeUsuario(u.getIdUsuario()),
                "solicitudes de " + u.getNombre() + " " + u.getApellido());
    }
}
