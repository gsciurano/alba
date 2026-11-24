package com.albaenlosandes.inventario.config;

import com.albaenlosandes.inventario.model.Rol;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * ==================================================================
 *  QUIEN PUEDE ENTRAR A DONDE
 * ==================================================================
 *
 * Este archivo es el que convierte "el navegador dice que es admin" en
 * "el servidor sabe que es admin". Antes de esta etapa, la guarda del
 * panel era una linea de JavaScript (admin.js:86) que mandaba a
 * ingresar a quien no tuviera el rol anotado en localStorage: alcanzaba
 * con escribir el rol a mano en las herramientas del navegador, o con
 * llamar a la API directamente, para saltearla.
 *
 * ------------------------------------------------------------------
 *  LAS DOS PUERTAS DE ESTE SISTEMA, Y POR QUE SE TRATAN DISTINTO
 * ------------------------------------------------------------------
 *
 * 1) EL SITIO WEB (/, /vinos, /tienda, /cuenta, /admin ...)
 *    Lo usa una persona con un navegador. Tiene sesion con cookie y
 *    token CSRF. Es lo que se protege aca.
 *
 * 2) LA API JSON (/api/**)
 *    La usa tambien la APLICACION DE ESCRITORIO en Swing que vive en el
 *    paquete 'escritorio' (15 clases, ApiCliente incluido). No es un
 *    navegador: no guarda cookies de sesion ni puede mandar un token
 *    CSRF. Si le exigieramos autenticacion o CSRF, el escritorio deja
 *    de funcionar por completo.
 *
 *    Por eso /api/** queda ABIERTO, igual que estaba antes de esta
 *    migracion. Es importante entender que esto NO es una regresion:
 *    esos endpoints ya estaban abiertos y cualquiera podia llamarlos.
 *    La diferencia es que ahora esta escrito, en un solo lugar y a la
 *    vista, en vez de ser una omision silenciosa.
 *
 *    COMO SE CERRARIA, el dia que corresponda: darle al escritorio su
 *    propia credencial (una clave de API en una cabecera, o
 *    autenticacion basica sobre HTTPS) y cambiar el permitAll de abajo
 *    por esa comprobacion. Es un cambio de dos lineas aca mas el
 *    correspondiente en ApiCliente. No se hace ahora porque tocar el
 *    escritorio esta fuera del alcance de esta entrega.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filtros(HttpSecurity http) throws Exception {
        http
            /*
             * -------- CSRF --------
             * Protege contra que otro sitio haga que tu navegador mande
             * un formulario a este servidor usando tu sesion. Queda
             * ACTIVO para todo el sitio web: Thymeleaf agrega el token
             * solo en cada <form th:action>, y las peticiones de HTMX lo
             * mandan por cabecera (ver el <meta> y el hx-headers del
             * fragmento de cabeza).
             *
             * Se apaga SOLO para /api/**, porque ahi del otro lado hay
             * una aplicacion de escritorio que no tiene de donde sacar
             * el token. El riesgo que eso abre es el que ya existia.
             */
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))

            .authorizeHttpRequests(reglas -> reglas
                /*
                 * La API, abierta: la consume el escritorio. Ver la nota
                 * larga de arriba.
                 */
                .requestMatchers("/api/**").permitAll()

                /*
                 * Archivos estaticos: hojas de estilo, imagenes, la
                 * librería htmx y los JavaScript que todavia sobreviven.
                 */
                .requestMatchers("/css/**", "/js/**", "/assets/**", "/data/**",
                                 "/favicon.ico", "/error").permitAll()

                /*
                 * LAS DIRECCIONES VIEJAS, abiertas porque solo redirigen.
                 * index.html, tienda.html y las demas no son archivos: son
                 * los 301 de CompatibilidadController a las direcciones
                 * nuevas. Dejarlas pasar no muestra nada que la pagina de
                 * destino no muestre igual.
                 *
                 * ANTES ESTO ERA UN "/*.html" SUELTO, y ahi estaba el
                 * agujero: la unica pagina .html que de verdad existe es
                 * admin.html, el panel, asi que ese permitAll lo dejaba
                 * abierto a cualquiera. La regla de abajo lo cierra.
                 */
                .requestMatchers("/index.html", "/finca.html", "/nosotros.html",
                                 "/contacto.html", "/privacidad.html", "/terminos.html",
                                 "/arrepentimiento.html", "/tienda.html", "/vinos.html",
                                 "/vino.html", "/cuenta.html").permitAll()

                /* Todo lo publico del sitio. */
                .requestMatchers("/", "/vinos", "/vinos/**", "/tienda", "/tienda/**",
                                 "/finca", "/nosotros", "/contacto", "/legal/**").permitAll()

                /* El ingreso y el alta tienen que ser accesibles sin sesion. */
                .requestMatchers("/cuenta", "/cuenta/ingresar", "/cuenta/registro",
                                 "/cuenta/google", "/cuenta/recupero/**").permitAll()

                /*
                 * EL PANEL: solo ADMIN. Esta es la linea que reemplaza a
                 * la guarda de pantalla de admin.js:86. Quien no sea
                 * administrador no recibe el HTML del panel: no es que lo
                 * reciba y se lo escondan.
                 *
                 * ESTAN LAS DOS FORMAS DE PEDIRLO: /admin.html, que es el
                 * archivo que todavia vive en static/ y es el panel de
                 * verdad, y /admin/** para cuando el panel se migre a
                 * Thymeleaf y pase a tener sus propias direcciones. La
                 * primera se va el dia que se borre el archivo.
                 */
                .requestMatchers("/admin.html", "/admin/**").hasRole(Rol.ADMIN.name())

                /* El resto de "Mi cuenta" exige haber ingresado. */
                .requestMatchers("/cuenta/**").authenticated()

                .anyRequest().permitAll()
            )

            /*
             * -------- INGRESO --------
             * No se usa la pantalla de ingreso que trae Spring Security:
             * el sitio ya tiene la suya, con el diseno de la bodega y el
             * boton de Google.
             *
             * usernameParameter("email"): el formulario pide el email, no
             * un "username" inventado.
             */
            .formLogin(ingreso -> ingreso
                .loginPage("/cuenta")
                .loginProcessingUrl("/cuenta/ingresar")
                .usernameParameter("email")
                .passwordParameter("password")
                .failureUrl("/cuenta?error")
                .successHandler(aDondeVaCadaUno())
                .permitAll()
            )

            /*
             * -------- SALIR --------
             * Por POST y no por GET: cerrar la sesion cambia algo en el
             * servidor, y ademas asi lleva token CSRF. Es el motivo por
             * el que el boton "Salir" del encabezado esta adentro de un
             * <form> y no es un enlace.
             */
            .logout(salida -> salida
                .logoutUrl("/cuenta/salir")
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            );

        return http.build();
    }

    /**
     * A donde va cada uno despues de ingresar.
     *
     * Reproduce la decision que antes tomaba el navegador en
     * cuenta.js:262 ("si el rol es ADMIN, admin.html; si no, index.html"),
     * con una diferencia que importa: ahora el rol lo mira el servidor
     * sobre la sesion que acaba de crear, no sobre un dato que mando el
     * navegador.
     *
     * Un cliente entra al sitio por la puerta principal, igual que
     * cualquier visitante pero ya con su nombre arriba; mandarlo a "Mis
     * pedidos" seria asumir que entra solo a revisar una compra, cuando
     * lo habitual es que venga a mirar vinos.
     */
    private AuthenticationSuccessHandler aDondeVaCadaUno() {
        return (pedido, respuesta, autenticacion) -> {
            boolean esAdmin = autenticacion.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_" + Rol.ADMIN.name()));
            /*
             * OJO CON ESTA DIRECCION: tiene que ser una que exista. El
             * panel todavia es el archivo static/admin.html, no una ruta
             * de Thymeleaf, asi que mandar a "/admin" terminaba en un 404
             * justo despues de un ingreso correcto. Cuando el panel se
             * migre, aca va a decir "/admin".
             */
            respuesta.sendRedirect(pedido.getContextPath() + (esAdmin ? "/admin.html" : "/"));
        };
    }
}
