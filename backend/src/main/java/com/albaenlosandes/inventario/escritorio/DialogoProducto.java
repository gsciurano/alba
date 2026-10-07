package com.albaenlosandes.inventario.escritorio;

import com.albaenlosandes.inventario.model.Linea;
import com.albaenlosandes.inventario.model.Producto;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;

/**
 * Formulario de alta y modificacion de un vino.
 *
 * DETALLE IMPORTANTE PARA EXPLICAR:
 * el formulario manda SIEMPRE el objeto completo, incluidos los campos que el
 * usuario no toco. Si mandara solo lo modificado, el servidor recibiria los
 * demas campos vacios y los pisaria. Por eso, cuando se edita, primero se
 * cargan todos los valores actuales del vino en la pantalla.
 *
 * Las validaciones NO estan aca: las hace el servidor. Si algo esta mal,
 * responde 400 con la lista de campos y este dialogo la muestra. Asi la
 * regla vive en un solo lugar y la pagina web va a comportarse igual.
 */
public class DialogoProducto extends JDialog {

    private final ApiCliente api;
    private final Producto original;      // null = alta; distinto de null = edicion
    private boolean guardado = false;

    private final JTextField txNombre      = new JTextField(24);
    private final JComboBox<Linea> cbLinea = new JComboBox<>(Linea.values());
    private final JTextField txVarietal    = new JTextField(24);
    private final JTextField txAnada       = new JTextField(6);
    private final JTextField txPrecio      = new JTextField(10);
    private final JTextField txStock       = new JTextField(6);
    private final JTextField txMinimo      = new JTextField(6);
    private final JTextField txImagen      = new JTextField(24);
    private final JTextArea  txDescripcion = new JTextArea(4, 24);
    private final JCheckBox  chActivo      = new JCheckBox("Activo (aparece en el catalogo)");

    public DialogoProducto(Window padre, ApiCliente api, Producto existente) {
        super(padre, existente == null ? "Nuevo vino" : "Editar vino", ModalityType.APPLICATION_MODAL);
        this.api = api;
        this.original = existente;

        setContentPane(construir());
        if (existente != null) cargarDatos(existente);
        else { chActivo.setSelected(true); txStock.setText("0"); txMinimo.setText("10"); }

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

        f = agregar(cuerpo, g, f, "Nombre *", txNombre);
        f = agregar(cuerpo, g, f, "Linea *", cbLinea);
        f = agregar(cuerpo, g, f, "Varietal *", txVarietal);
        f = agregar(cuerpo, g, f, "Anada", txAnada);
        f = agregar(cuerpo, g, f, "Precio *", txPrecio);
        f = agregar(cuerpo, g, f, "Stock actual *", txStock);
        f = agregar(cuerpo, g, f, "Stock minimo *", txMinimo);
        f = agregar(cuerpo, g, f, "URL de imagen", txImagen);

        txDescripcion.setLineWrap(true);
        txDescripcion.setWrapStyleWord(true);
        txDescripcion.setFont(UI.FUENTE);
        f = agregar(cuerpo, g, f, "Descripcion", new JScrollPane(txDescripcion));

        g.gridx = 1; g.gridy = f++;
        chActivo.setBackground(Color.WHITE);
        chActivo.setFont(UI.FUENTE);
        cuerpo.add(chActivo, g);

        g.gridx = 1; g.gridy = f;
        cuerpo.add(UI.ayuda("Los campos con * son obligatorios. Los valida el servidor."), g);

        JButton bGuardar = UI.boton(original == null ? "Crear vino" : "Guardar cambios", true);
        JButton bCancelar = UI.boton("Cancelar", false);
        bGuardar.addActionListener(e -> guardar());
        bCancelar.addActionListener(e -> dispose());

        JPanel pie = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        pie.setBackground(UI.FONDO_SUAVE);
        pie.add(bCancelar);
        pie.add(bGuardar);

        JPanel raiz = new JPanel(new BorderLayout());
        raiz.add(cuerpo, BorderLayout.CENTER);
        raiz.add(pie, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(bGuardar);
        return raiz;
    }

    private int agregar(JPanel p, GridBagConstraints g, int fila, String etiqueta, Component campo) {
        g.gridx = 0; g.gridy = fila; g.fill = GridBagConstraints.NONE;
        p.add(UI.etiqueta(etiqueta), g);
        g.gridx = 1; g.fill = GridBagConstraints.HORIZONTAL;
        if (campo instanceof JTextField t) t.setFont(UI.FUENTE);
        p.add(campo, g);
        return fila + 1;
    }

    private void cargarDatos(Producto p) {
        txNombre.setText(UI.nvl(p.getNombre()));
        cbLinea.setSelectedItem(p.getLinea());
        txVarietal.setText(UI.nvl(p.getVarietal()));
        txAnada.setText(p.getAnada() == null ? "" : String.valueOf(p.getAnada()));
        txPrecio.setText(p.getPrecio() == null ? "" : p.getPrecio().toPlainString());
        txStock.setText(String.valueOf(p.getStockActual()));
        txMinimo.setText(String.valueOf(p.getStockMinimo()));
        txImagen.setText(UI.nvl(p.getImagenUrl()));
        txDescripcion.setText(UI.nvl(p.getDescripcion()));
        chActivo.setSelected(Boolean.TRUE.equals(p.getActivo()));
    }


    private void guardar() {
        Producto p = new Producto();
        p.setNombre(vacioANull(txNombre.getText()));
        p.setLinea((Linea) cbLinea.getSelectedItem());
        p.setVarietal(vacioANull(txVarietal.getText()));
        p.setDescripcion(vacioANull(txDescripcion.getText()));
        p.setImagenUrl(vacioANull(txImagen.getText()));
        p.setActivo(chActivo.isSelected());

        // Los numeros se controlan aca solo para no mandar basura al servidor.
        // La validacion de verdad (obligatorio, no negativo, rango) la hace el backend.
        try {
            p.setAnada(txAnada.getText().isBlank() ? null : Integer.valueOf(txAnada.getText().trim()));
            p.setPrecio(txPrecio.getText().isBlank() ? null
                    : new BigDecimal(txPrecio.getText().trim().replace(",", ".")));
            p.setStockActual(txStock.getText().isBlank() ? null : Integer.valueOf(txStock.getText().trim()));
            p.setStockMinimo(txMinimo.getText().isBlank() ? null : Integer.valueOf(txMinimo.getText().trim()));
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                    "Anada, precio, stock y minimo tienen que ser numeros.",
                    "Numero invalido", JOptionPane.WARNING_MESSAGE);
            return;
        }

        UI.enSegundoPlano(this,
                () -> original == null ? api.crearProducto(p)
                                       : api.actualizarProducto(original.getIdProducto(), p),
                resultado -> { guardado = true; dispose(); });
    }

    private static String vacioANull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
