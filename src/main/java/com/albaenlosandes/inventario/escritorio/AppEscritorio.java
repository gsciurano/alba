package com.albaenlosandes.inventario.escritorio;

import javax.swing.*;
import java.awt.*;

/**
 * ==========================================================================
 *  PUNTO DE ENTRADA DE LA VERSION DE ESCRITORIO.
 * ==========================================================================
 *
 * OJO: este main NO es el de Spring. Son dos programas distintos:
 *
 *   1) GestorInventarioApplication  -> el SERVIDOR. Hay que arrancarlo PRIMERO.
 *   2) AppEscritorio (esta clase)   -> el CLIENTE. Se arranca despues.
 *
 * Si se abre el cliente sin el servidor prendido, avisa y no rompe nada.
 *
 * Se le puede pasar otra direccion por parametro, para apuntar a un servidor
 * que este en otra computadora:
 *     java ... AppEscritorio http://192.168.0.15:8080
 * Eso es lo que demuestra que la aplicacion no es "monolitica": cliente y
 * servidor se comunican por red y se pueden separar en dos maquinas.
 */
public class AppEscritorio {

    public static final String URL_POR_DEFECTO = "http://localhost:8080";

    public static void main(String[] args) {
        String url = (args.length > 0 && !args[0].isBlank()) ? args[0] : URL_POR_DEFECTO;

        // Look and feel del sistema: que la ventana se vea como el resto del SO.
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            UIManager.put("Table.showGrid", true);
        } catch (Exception ignorado) { }

        ApiCliente api = new ApiCliente(url);

        /*
         * invokeLater: toda la interfaz de Swing tiene que construirse desde el
         * hilo de eventos (Event Dispatch Thread), no desde el main. Es la regla
         * basica de Swing y evita errores raros de dibujado.
         */
        SwingUtilities.invokeLater(() -> {
            VentanaPrincipal ventana = new VentanaPrincipal(api);
            ventana.setVisible(true);
            ventana.estado("Conectando con " + url + " ...");

            // Prueba de conexion en segundo plano, para no congelar la ventana.
            new SwingWorker<Boolean, Void>() {
                @Override protected Boolean doInBackground() { return api.hayConexion(); }
                @Override protected void done() {
                    boolean ok;
                    try { ok = get(); } catch (Exception e) { ok = false; }
                    ventana.conexionOk(ok);
                    if (ok) {
                        ventana.estado("Conectado. Datos cargados desde el servidor.");
                    } else {
                        ventana.estado("Sin conexion con el servidor.");
                        JOptionPane.showMessageDialog(ventana,
                                "No se pudo conectar con el servidor en:\n   " + url + "\n\n"
                                + "Antes de abrir esta ventana hay que ejecutar la clase\n"
                                + "GestorInventarioApplication (el backend Spring Boot).\n\n"
                                + "La ventana queda abierta: cuando el servidor este arriba,\n"
                                + "usa el boton \"Recargar todo\" de abajo a la derecha.",
                                "Servidor no disponible", JOptionPane.WARNING_MESSAGE);
                    }
                }
            }.execute();
        });
    }
}
