package com.albaenlosandes.inventario.escritorio;

import com.albaenlosandes.inventario.model.Rol;
import com.albaenlosandes.inventario.model.Usuario;

import javax.swing.*;
import java.awt.*;

/**
 * Alta y modificacion de usuarios.
 *
 * PUNTO A MARCAR EN LA DEFENSA (es una debilidad conocida y hay que saberla):
 * lo que se escribe en "Contrasena" viaja tal cual y se guarda tal cual.
 * Todavia no hay cifrado. En el servidor, UsuarioService tiene marcado el
 * lugar exacto donde va el hash BCrypt cuando se agregue la seguridad.
 */
public class DialogoUsuario extends JDialog {

    private final ApiCliente api;
    private final Usuario original;
    private boolean guardado = false;

    private final JTextField txNombre   = new JTextField(20);
    private final JTextField txApellido = new JTextField(20);
    private final JTextField txEmail    = new JTextField(24);
    private final JPasswordField txPass = new JPasswordField(20);
    private final JTextField txTelefono = new JTextField(16);
    private final JComboBox<Rol> cbRol  = new JComboBox<>(Rol.values());
    private final JCheckBox chActivo    = new JCheckBox("Activo");

    public DialogoUsuario(Window padre, ApiCliente api, Usuario existente) {
        super(padre, existente == null ? "Nuevo usuario" : "Editar usuario",
                ModalityType.APPLICATION_MODAL);
        this.api = api;
        this.original = existente;
        setContentPane(construir());
        if (existente != null) {
            txNombre.setText(existente.getNombre());
            txApellido.setText(existente.getApellido());
            txEmail.setText(existente.getEmail());
            txTelefono.setText(existente.getTelefono() == null ? "" : existente.getTelefono());
            cbRol.setSelectedItem(existente.getRol());
            chActivo.setSelected(Boolean.TRUE.equals(existente.getActivo()));
        } else {
            chActivo.setSelected(true);
        }
        pack();
        setLocationRelativeTo(padre);
        setResizable(false);
    }

    public boolean seGuardo() { return guardado; }

    private JPanel construir() {
        JPanel cuerpo = new JPanel(new GridBagLayout());
        cuerpo.setBackground(Color.WHITE);
        cuerpo.setBorder(BorderFactory.createEmptyBorder(16, 18, 8, 18));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4);
        g.anchor = GridBagConstraints.WEST;
        int f = 0;
        f = fila(cuerpo, g, f, "Nombre *", txNombre);
        f = fila(cuerpo, g, f, "Apellido *", txApellido);
        f = fila(cuerpo, g, f, "Email *", txEmail);
        f = fila(cuerpo, g, f, original == null ? "Contrasena *" : "Contrasena", txPass);
        f = fila(cuerpo, g, f, "Telefono", txTelefono);
        f = fila(cuerpo, g, f, "Rol *", cbRol);
        g.gridx = 1; g.gridy = f++;
        chActivo.setBackground(Color.WHITE); chActivo.setFont(UI.FUENTE);
        cuerpo.add(chActivo, g);
        g.gridx = 1; g.gridy = f;
        cuerpo.add(UI.ayuda(original == null
                ? "El servidor valida el formato del email y que no este repetido."
                : "Dejala vacia para conservar la contrasena actual."), g);

        JButton bGuardar  = UI.boton(original == null ? "Crear usuario" : "Guardar cambios", true);
        JButton bCancelar = UI.boton("Cancelar", false);
        bGuardar.addActionListener(e -> guardar());
        bCancelar.addActionListener(e -> dispose());

        JPanel pie = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        pie.setBackground(UI.FONDO_SUAVE);
        pie.add(bCancelar); pie.add(bGuardar);

        JPanel raiz = new JPanel(new BorderLayout());
        raiz.add(cuerpo, BorderLayout.CENTER);
        raiz.add(pie, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(bGuardar);
        return raiz;
    }

    private int fila(JPanel p, GridBagConstraints g, int f, String et, Component c) {
        g.gridx = 0; g.gridy = f; g.fill = GridBagConstraints.NONE;
        p.add(UI.etiqueta(et), g);
        g.gridx = 1; g.fill = GridBagConstraints.HORIZONTAL;
        p.add(c, g);
        return f + 1;
    }

    private void guardar() {
        Usuario u = new Usuario();
        u.setNombre(nn(txNombre.getText()));
        u.setApellido(nn(txApellido.getText()));
        u.setEmail(nn(txEmail.getText()));
        u.setPasswordHash(nn(new String(txPass.getPassword())));
        u.setTelefono(nn(txTelefono.getText()));
        u.setRol((Rol) cbRol.getSelectedItem());
        u.setActivo(chActivo.isSelected());

        UI.enSegundoPlano(this,
                () -> original == null ? api.crearUsuario(u)
                                       : api.actualizarUsuario(original.getIdUsuario(), u),
                r -> { guardado = true; dispose(); });
    }

    private static String nn(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }
}
