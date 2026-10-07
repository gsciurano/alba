package com.albaenlosandes.inventario.escritorio;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Utilidades visuales compartidas por todos los paneles.
 * Esta clase existe para no repetir 40 veces el mismo codigo de colores,
 * botones y tablas. Nada de logica de negocio: solo apariencia y ayudas.
 */
public final class UI {

    private UI() { }

    // ------------------------------------------------------------------
    //  PALETA. Todos los colores de la aplicacion salen de aca: cambiando
    //  estas ocho lineas cambia el aspecto de las cinco pestanas de una vez.
    //  (Es la misma idea que una hoja de estilos en una pagina web.)
    // ------------------------------------------------------------------
    public static final Color PRINCIPAL       = new Color(0x34, 0x43, 0x4D); // azul pizarra oscuro
    public static final Color PRINCIPAL_CLARO = new Color(0x4C, 0x60, 0x6D); // seleccion de filas
    public static final Color PRINCIPAL_SUAVE = new Color(0xB9, 0xC6, 0xCE); // textos sobre el encabezado
    public static final Color FONDO_SUAVE     = new Color(0xF1, 0xF4, 0xF6); // filas alternadas y pies
    public static final Color BORDE           = new Color(0xD2, 0xDA, 0xDF);
    public static final Color TEXTO           = new Color(0x2A, 0x33, 0x39);
    public static final Color TEXTO_TENUE     = new Color(0x64, 0x72, 0x7A);
    public static final Color VERDE           = new Color(0x1E, 0x7A, 0x54);
    public static final Color ROJO            = new Color(0xB3, 0x3A, 0x2E);
    public static final Color AMBAR           = new Color(0xA8, 0x74, 0x12);
    public static final Color FONDO_ALERTA    = new Color(0xFA, 0xF0, 0xDA);

    public static final Font FUENTE       = new Font("SansSerif", Font.PLAIN, 13);
    public static final Font FUENTE_NEG   = new Font("SansSerif", Font.BOLD, 13);
    public static final Font FUENTE_TITULO= new Font("SansSerif", Font.BOLD, 17);

    private static final NumberFormat PESOS = NumberFormat.getNumberInstance(new Locale("es", "AR"));
    static { PESOS.setMinimumFractionDigits(2); PESOS.setMaximumFractionDigits(2); }

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public static String pesos(BigDecimal v) {
        return v == null ? "" : "$ " + PESOS.format(v);
    }

    /** Version corta, sin centavos: para los numeros grandes de las tarjetas del resumen. */
    public static String pesosCorto(BigDecimal v) {
        if (v == null) return "";
        NumberFormat f = NumberFormat.getNumberInstance(new Locale("es", "AR"));
        f.setMaximumFractionDigits(0);
        return "$ " + f.format(v);
    }

    public static String fecha(LocalDateTime f) {
        return f == null ? "" : f.format(FECHA);
    }

    /** Texto vacio en lugar de null, para no imprimir "null" en pantalla. */
    public static String nvl(String s) {
        return s == null ? "" : s;
    }

    // ======================================================================
    //  COMPONENTES CON ESTILO
    // ======================================================================

    public static JButton boton(String texto, boolean principal) {
        JButton b = new JButton(texto);
        b.setFont(FUENTE_NEG);
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        if (principal) {
            b.setBackground(PRINCIPAL);
            b.setForeground(Color.WHITE);
        } else {
            b.setBackground(Color.WHITE);
            b.setForeground(PRINCIPAL);
            b.setBorderPainted(true);
            b.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDE),
                    BorderFactory.createEmptyBorder(7, 15, 7, 15)));
        }
        return b;
    }

    public static JLabel titulo(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(FUENTE_TITULO);
        l.setForeground(PRINCIPAL);
        return l;
    }

    public static JLabel etiqueta(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(FUENTE);
        l.setForeground(TEXTO);
        return l;
    }

    public static JLabel ayuda(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(new Font("SansSerif", Font.ITALIC, 12));
        l.setForeground(TEXTO_TENUE);
        return l;
    }

    public static JPanel fila(Component... hijos) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        p.setOpaque(false);
        for (Component c : hijos) p.add(c);
        return p;
    }

    /** Tabla de solo lectura, con cebra y encabezado bordo. */
    public static JTable tabla(DefaultTableModel modelo) {
        JTable t = new JTable(modelo) {
            @Override public Component prepareRenderer(javax.swing.table.TableCellRenderer r, int fila, int col) {
                Component c = super.prepareRenderer(r, fila, col);
                if (!isRowSelected(fila)) c.setBackground(fila % 2 == 0 ? Color.WHITE : FONDO_SUAVE);
                return c;
            }
        };
        t.setFont(FUENTE);
        t.setRowHeight(26);
        t.setGridColor(BORDE);
        t.setSelectionBackground(PRINCIPAL_CLARO);
        t.setSelectionForeground(Color.WHITE);
        t.setAutoCreateRowSorter(true);   // click en el encabezado = ordenar
        JTableHeader h = t.getTableHeader();
        h.setFont(FUENTE_NEG);
        h.setBackground(PRINCIPAL);
        h.setForeground(Color.WHITE);
        h.setReorderingAllowed(false);
        ((DefaultTableCellRenderer) h.getDefaultRenderer()).setHorizontalAlignment(SwingConstants.LEFT);
        return t;
    }

    /** Modelo de tabla que NO deja editar las celdas: se edita por formulario. */
    public static DefaultTableModel modelo(String... columnas) {
        return new DefaultTableModel(columnas, 0) {
            @Override public boolean isCellEditable(int f, int c) { return false; }
        };
    }

    public static JScrollPane scroll(Component c) {
        JScrollPane s = new JScrollPane(c);
        s.setBorder(BorderFactory.createLineBorder(BORDE));
        s.getViewport().setBackground(Color.WHITE);
        return s;
    }

    // ======================================================================
    //  MENSAJES
    // ======================================================================

    public static void info(Component padre, String texto) {
        JOptionPane.showMessageDialog(padre, texto, "Listo", JOptionPane.INFORMATION_MESSAGE);
    }

    public static boolean confirmar(Component padre, String texto) {
        return JOptionPane.showConfirmDialog(padre, texto, "Confirmar",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /**
     * Muestra un error del servidor. Si el servidor mando la lista de campos
     * mal cargados (validacion), los enumera uno por uno.
     */
    public static void error(Component padre, ApiException e) {
        StringBuilder sb = new StringBuilder(e.getMessage());
        if (e.esErrorDeValidacion()) {
            sb.append("\n");
            for (Map.Entry<String, String> c : e.getCampos().entrySet()) {
                sb.append("\n  • ").append(c.getKey()).append(": ").append(c.getValue());
            }
        }
        String titulo = switch (e.getEstado()) {
            case 0   -> "Sin conexion con el servidor";
            case 400 -> "Datos invalidos";
            case 404 -> "No encontrado";
            case 409 -> "Conflicto con la base de datos";
            case 500 -> "Error del servidor";
            default  -> "Error";
        };
        JOptionPane.showMessageDialog(padre, sb.toString(), titulo, JOptionPane.ERROR_MESSAGE);
    }

    // ======================================================================
    //  TAREAS EN SEGUNDO PLANO
    // ======================================================================

    /**
     * Ejecuta una llamada al servidor SIN congelar la ventana.
     *
     * Swing dibuja todo desde un unico hilo (el Event Dispatch Thread). Si se
     * llama al servidor desde ahi, la ventana se queda tildada hasta que llegue
     * la respuesta. SwingWorker resuelve eso: 'doInBackground' corre en otro
     * hilo y 'done' vuelve al hilo de Swing para actualizar la pantalla.
     *
     * @param tarea     lo que hay que pedirle al servidor (corre en segundo plano)
     * @param alTerminar que hacer con el resultado (corre en el hilo de Swing)
     */
    public static <T> void enSegundoPlano(Component padre, Callable<T> tarea, Consumer<T> alTerminar) {
        padre.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception {
                return tarea.call();
            }
            @Override protected void done() {
                padre.setCursor(Cursor.getDefaultCursor());
                try {
                    alTerminar.accept(get());
                } catch (Exception ex) {
                    Throwable causa = ex.getCause() != null ? ex.getCause() : ex;
                    if (causa instanceof ApiException api) error(padre, api);
                    else JOptionPane.showMessageDialog(padre, String.valueOf(causa.getMessage()),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}
