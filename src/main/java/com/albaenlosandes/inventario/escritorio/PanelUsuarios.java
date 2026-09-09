package com.albaenlosandes.inventario.escritorio;

import com.albaenlosandes.inventario.model.Rol;
import com.albaenlosandes.inventario.model.Usuario;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * PESTANA "CLIENTES": ABM completo de usuarios (clientes y administradores).
 *
 * CRUD:  POST /api/usuarios | GET /api/usuarios | PUT /api/usuarios/{id} | DELETE /api/usuarios/{id}
 *
 * DETALLE QUE CONVIENE MOSTRAR EN LA DEFENSA:
 * la columna de contrasena no existe. No es que la ocultemos en la ventana:
 * el servidor directamente NO la manda. La entidad Usuario tiene la contrasena
 * marcada como "solo escritura", asi que puede entrar por la API pero nunca sale.
 */
public class PanelUsuarios extends JPanel {

    private final ApiCliente api;
    private final VentanaPrincipal ventana;

    private final DefaultTableModel modelo = UI.modelo(
            "ID", "Nombre", "Apellido", "Email", "Telefono", "Rol", "Alta", "Activo");
    private final JTable tabla = UI.tabla(modelo);
    private final JLabel resumen = UI.ayuda(" ");

    private List<Usuario> usuarios = List.of();

    public PanelUsuarios(ApiCliente api, VentanaPrincipal ventana) {
        this.api = api;
        this.ventana = ventana;
        setLayout(new BorderLayout(0, 10));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        JPanel norte = new JPanel(new BorderLayout());
        norte.setOpaque(false);
        norte.add(UI.fila(UI.titulo("Clientes y administradores")), BorderLayout.WEST);
        norte.add(UI.fila(UI.ayuda("La contrasena nunca viaja del servidor al cliente.")),
                BorderLayout.EAST);
        add(norte, BorderLayout.NORTH);
        add(UI.scroll(tabla), BorderLayout.CENTER);

        JButton bNuevo  = UI.boton("Nuevo usuario", true);
        JButton bEditar = UI.boton("Editar", false);
        JButton bBaja   = UI.boton("Dar de baja", false);
        JButton bPedidos= UI.boton("Ver sus pedidos", false);

        bNuevo.addActionListener(e -> abrirFormulario(null));
        bEditar.addActionListener(e -> { Usuario u = seleccionado(); if (u != null) abrirFormulario(u); });
        bBaja.addActionListener(e -> darDeBaja());
        bPedidos.addActionListener(e -> { Usuario u = seleccionado(); if (u != null) ventana.mostrarPedidosDe(u); });

        JPanel sur = new JPanel(new BorderLayout());
        sur.setOpaque(false);
        sur.add(UI.fila(bNuevo, bEditar, bBaja, Box.createHorizontalStrut(16), bPedidos), BorderLayout.WEST);
        JPanel der = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        der.setOpaque(false); der.add(resumen);
        sur.add(der, BorderLayout.EAST);
        add(sur, BorderLayout.SOUTH);

        recargar();
    }

    public void recargar() {
        UI.enSegundoPlano(this, api::listarUsuarios, lista -> {
            usuarios = lista;
            modelo.setRowCount(0);
            for (Usuario u : lista) {
                modelo.addRow(new Object[]{
                        u.getIdUsuario(), u.getNombre(), u.getApellido(), u.getEmail(),
                        u.getTelefono() == null ? "" : u.getTelefono(),
                        u.getRol(), UI.fecha(u.getFechaAlta()),
                        Boolean.TRUE.equals(u.getActivo()) ? "Si" : "No"});
            }
            resumen.setText(lista.size() + " usuarios");
            ventana.setUsuariosCache(lista);
        });
    }

    private Usuario seleccionado() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            JOptionPane.showMessageDialog(this, "Primero elegi un usuario de la tabla.",
                    "Sin seleccion", JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        return usuarios.get(tabla.convertRowIndexToModel(fila));
    }

    private void abrirFormulario(Usuario existente) {
        DialogoUsuario d = new DialogoUsuario(SwingUtilities.getWindowAncestor(this), api, existente);
        d.setVisible(true);
        if (d.seGuardo()) {
            recargar();
            ventana.estado(existente == null ? "Usuario creado." : "Usuario modificado.");
        }
    }

    private void darDeBaja() {
        Usuario u = seleccionado();
        if (u == null) return;
        if (!UI.confirmar(this, "Dar de baja a " + u.getNombre() + " " + u.getApellido() + ".\n\n"
                + "Es una BAJA LOGICA: se marca como inactivo pero se conserva\n"
                + "todo su historial de pedidos.\n\n¿Confirmas?")) return;
        UI.enSegundoPlano(this, () -> { api.eliminarUsuario(u.getIdUsuario()); return null; },
                r -> { recargar(); ventana.estado("Baja logica aplicada a " + u.getEmail()); });
    }
}
