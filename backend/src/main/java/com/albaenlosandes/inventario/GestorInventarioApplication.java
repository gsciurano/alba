package com.albaenlosandes.inventario;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Punto de entrada de la aplicacion.
 * SpringApplication.run(...) levanta el contexto de Spring:
 * crea y administra los controllers, services, repositories
 * y la conexion a MySQL (inyeccion de dependencias).
 *
 * @EnableAsync habilita los metodos marcados con @Async, que Spring
 * ejecuta en otro hilo. Lo usa el envio de correo: sin esto, cada
 * compra esperaria al servidor de correo antes de contestarle a la
 * persona, y la anotacion @Async se ignoraria en silencio.
 */
@SpringBootApplication
@EnableAsync
public class GestorInventarioApplication {

    public static void main(String[] args) {
        SpringApplication.run(GestorInventarioApplication.class, args);
    }
}
