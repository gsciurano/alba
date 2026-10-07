package com.albaenlosandes.inventario;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de la aplicacion.
 * SpringApplication.run(...) levanta el contexto de Spring:
 * crea y administra los controllers, services, repositories
 * y la conexion a MySQL (inyeccion de dependencias).
 */
@SpringBootApplication
public class GestorInventarioApplication {

    public static void main(String[] args) {
        SpringApplication.run(GestorInventarioApplication.class, args);
    }
}
