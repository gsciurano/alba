package com.albaenlosandes.inventario.service;

import com.albaenlosandes.inventario.controller.RecuperoVencidoException;
import com.albaenlosandes.inventario.model.ProveedorIngreso;
import com.albaenlosandes.inventario.model.Usuario;
import com.albaenlosandes.inventario.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ==================================================================
 *  RECUPERO DE CONTRASENA, en cuatro pasos
 * ==================================================================
 *
 * Quien no se acuerda la contrasena no puede probar quien es con la
 * contrasena. Hay que pedirle otra cosa, y la idea es pedirle DOS, que
 * prueban cosas distintas:
 *
 *   1. EMAIL      — dice de que cuenta estamos hablando.
 *   2. TELEFONO   — se le muestra el suyo incompleto y completa los que
 *                   faltan. Prueba que SABE un dato de la cuenta.
 *   3. CODIGO     — seis numeros que salen por mail a esa casilla.
 *                   Prueba que TIENE ACCESO a la casilla.
 *   4. CONTRASENA — recien ahi se guarda la nueva.
 *
 * POR QUE NO ALCANZA CON EL TELEFONO SOLO: un telefono no es un secreto.
 * Esta en una agenda, en un aviso, se lo sabe un companero de trabajo. Y
 * los digitos tapados son cuatro, o sea diez mil combinaciones, que una
 * maquina prueba en segundos. El paso del telefono sirve para confirmar
 * quien dice ser, no para autorizar el cambio; lo que de verdad lo
 * autoriza es el codigo, porque para leerlo hay que entrar a la casilla.
 * Es el mismo reparto que usan Google y los bancos.
 *
 * LOS TRES INTENTOS NO SON DECORATIVOS. Sin ellos, cuatro digitos tapados
 * se adivinan probando: diez mil pedidos no son nada para un script. Con
 * tres, el intento se quema y hay que empezar de cero desde el email.
 *
 * ==================================================================
 *  DONDE VIVE ESTO Y POR QUE NO EN LA BASE
 * ==================================================================
 *
 * Los intentos en curso estan EN MEMORIA, en un mapa de esta clase. No
 * hay tabla ni migracion que correr.
 *
 * Lo que se pierde: si se reinicia Spring Boot, los recuperos a medias se
 * cortan y hay que volver a empezar. Dura quince minutos y lo hace una
 * persona por vez, asi que la molestia es minima.
 * Lo que se gana: nada de esto queda escrito en ningun lado. El codigo no
 * se guarda en una tabla que despues alguien consulta, y no hay filas
 * viejas acumulandose que haya que ir a limpiar.
 *
 * En un sistema con mas de un servidor esto NO alcanza: cada uno tendria
 * su propio mapa y el paso 2 caeria en una maquina que no sabe nada del
 * paso 1. Ahi va a una tabla o a un Redis. Con un solo servidor, como el
 * de esta entrega, el mapa es la opcion correcta y no una simplificacion.
 */
@Service
public class ServicioRecupero {

    /** Lo que se le muestra a la persona al empezar, sin datos de mas. */
    public record Inicio(String token, String telefonoParcial, int faltan) { }

    /** Despues de acertar el telefono: a donde se mando el codigo. */
    public record Enviado(String emailParcial) { }

    /**
     * Quince minutos. Suficiente para ir a buscar el mail sin apuro, y
     * corto para que un intento abierto y olvidado no quede dando vueltas.
     */
    private static final Duration VIDA = Duration.ofMinutes(15);

    /** Por etapa: tres al telefono, y otros tres al codigo. */
    private static final int INTENTOS = 3;

    /**
     * Cuantos digitos se tapan. Cuatro es el equilibrio: menos se adivinan
     * de memoria mirando el numero, y mas se vuelve una prueba de memoria
     * en vez de una confirmacion.
     */
    private static final int OCULTOS = 4;

    /** Los dos ultimos quedan a la vista: son los que la gente reconoce. */
    private static final int COLA_VISIBLE = 2;

    private enum Etapa { TELEFONO, CODIGO, AUTORIZADO }

    /** Un recupero a medias. Vive en el mapa hasta que se usa o vence. */
    private static final class Intento {
        final Integer idUsuario;
        final String digitosOcultos;
        final Instant vence;
        Etapa etapa = Etapa.TELEFONO;
        int quedan = INTENTOS;
        String codigo;

        Intento(Integer idUsuario, String digitosOcultos) {
            this.idUsuario = idUsuario;
            this.digitosOcultos = digitosOcultos;
            this.vence = Instant.now().plus(VIDA);
        }
    }

    /**
     * ConcurrentHashMap y no HashMap: Tomcat atiende cada pedido HTTP en
     * un hilo distinto, asi que dos personas recuperando a la vez tocan
     * este mapa al mismo tiempo. Con un HashMap comun eso corrompe la
     * estructura y el sintoma es un cuelgue imposible de reproducir.
     */
    private final Map<String, Intento> enCurso = new ConcurrentHashMap<>();

    /**
     * SecureRandom y no Random: Random es predecible. Sabiendo un par de
     * valores se calculan los siguientes, y eso aca significa adivinar el
     * codigo de otro sin haber leido ningun mail.
     */
    private final SecureRandom azar = new SecureRandom();

    private final UsuarioRepository repository;
    private final UsuarioService usuarios;
    private final ServicioCorreo correo;

    public ServicioRecupero(UsuarioRepository repository, UsuarioService usuarios,
                            ServicioCorreo correo) {
        this.repository = repository;
        this.usuarios = usuarios;
        this.correo = correo;
    }

    // ==================================================================
    //  PASO 1 — el email
    // ==================================================================

    /**
     * Busca la cuenta y devuelve el telefono tapado para que la persona
     * lo complete.
     *
     * DECISION TOMADA A CONCIENCIA: cuando el email no existe, esto lo
     * DICE. Es comodo para quien se equivoco al tipear, y tiene un costo:
     * cualquiera puede preguntar direccion por direccion y armarse la
     * lista de quienes tienen cuenta. Es el criterio contrario al del
     * login, que contesta lo mismo exista o no el email (ver
     * UsuarioService.autenticar). Si algun dia se prefiere cerrarlo, el
     * cambio es devolver siempre un telefono tapado inventado —pero
     * estable para ese email, o la diferencia se nota igual— y dejar que
     * falle en el paso 2.
     */
    public Inicio iniciar(String email) {
        limpiarVencidos();

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Escribí el email de tu cuenta.");
        }
        Usuario u = repository.findByEmail(email.trim())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No hay ninguna cuenta con el email " + email.trim() + "."));

        if (Boolean.FALSE.equals(u.getActivo())) {
            throw new IllegalArgumentException(
                    "Esa cuenta está dada de baja. Escribinos y la reactivamos.");
        }

        /*
         * Una cuenta de Google no tiene contrasena: no hay nada que
         * recuperar. Dejarla pasar seria peor que rechazarla, porque le
         * crearia una contrasena local a alguien que entra con el boton
         * y nunca va a usarla.
         */
        if (u.getProveedor() == ProveedorIngreso.GOOGLE && u.getPasswordHash() == null) {
            throw new IllegalArgumentException(
                    "Esa cuenta ingresa con Google, así que no tiene contraseña. "
                    + "Entrá con el botón \"Continuar con Google\".");
        }

        String telefono = u.getTelefono();
        if (telefono == null || soloDigitos(telefono).length() < 4) {
            throw new IllegalArgumentException(
                    "Esa cuenta no tiene un teléfono cargado, así que no se puede "
                    + "confirmar por acá. Escribinos a hola@albaenlosandes.com.");
        }

        String ocultos = digitosTapados(telefono);
        String token = nuevoToken();
        enCurso.put(token, new Intento(u.getIdUsuario(), ocultos));

        return new Inicio(token, enmascarar(telefono), ocultos.length());
    }

    // ==================================================================
    //  PASO 2 — los digitos que faltan
    // ==================================================================

    /**
     * Compara los digitos y, si estan bien, manda el codigo por mail.
     *
     * El mail se manda ACA y no en el paso siguiente para que la persona
     * ya lo tenga llegando mientras lee la pantalla.
     *
     * Este envio es SINCRONICO, al reves que el comprobante de compra,
     * que va con @Async. La diferencia es que si el comprobante no sale,
     * la compra igual se hizo; si este no sale, la persona se queda
     * esperando para siempre un codigo que nunca va a llegar. Por eso si
     * falla, falla a la vista y se avisa.
     */
    public Enviado confirmarTelefono(String token, String digitos) {
        Intento intento = vivo(token);
        if (intento.etapa != Etapa.TELEFONO) {
            throw new IllegalArgumentException("Ese paso ya está hecho.");
        }

        String escritos = soloDigitos(digitos == null ? "" : digitos);
        if (escritos.isEmpty()) {
            throw new IllegalArgumentException("Escribí los números que faltan.");
        }

        if (!escritos.equals(intento.digitosOcultos)) {
            intento.quedan--;
            if (intento.quedan <= 0) {
                enCurso.remove(token);
                throw new RecuperoVencidoException(
                        "Los números no coinciden y se acabaron los intentos. "
                        + "Empezá de nuevo desde tu email.");
            }
            throw new IllegalArgumentException(
                    "Los números no coinciden. Te " + (intento.quedan == 1
                            ? "queda 1 intento." : "quedan " + intento.quedan + " intentos."));
        }

        Usuario u = repository.findById(intento.idUsuario)
                .orElseThrow(() -> new IllegalArgumentException("Esa cuenta ya no existe."));

        intento.codigo = String.format("%06d", azar.nextInt(1_000_000));
        intento.etapa = Etapa.CODIGO;
        intento.quedan = INTENTOS;

        try {
            correo.enviarCodigoRecupero(u, intento.codigo, VIDA.toMinutes());
        } catch (Exception e) {
            // Sin codigo no hay forma de seguir, asi que el intento se tira:
            // dejarlo vivo solo serviria para que la persona pruebe codigos.
            enCurso.remove(token);
            throw new IllegalArgumentException(
                    "No pudimos mandarte el código por mail. Probá de nuevo en un rato.");
        }

        return new Enviado(enmascararEmail(u.getEmail()));
    }

    // ==================================================================
    //  PASO 3 — el codigo del mail
    // ==================================================================

    public void confirmarCodigo(String token, String codigo) {
        Intento intento = vivo(token);
        if (intento.etapa == Etapa.TELEFONO) {
            throw new IllegalArgumentException("Primero completá los números del teléfono.");
        }
        if (intento.etapa == Etapa.AUTORIZADO) {
            return;                       // ya estaba confirmado: no es un error
        }

        String escrito = soloDigitos(codigo == null ? "" : codigo);
        if (!escrito.equals(intento.codigo)) {
            intento.quedan--;
            if (intento.quedan <= 0) {
                enCurso.remove(token);
                throw new RecuperoVencidoException(
                        "El código no coincide y se acabaron los intentos. "
                        + "Empezá de nuevo desde tu email.");
            }
            throw new IllegalArgumentException(
                    "El código no coincide. Te " + (intento.quedan == 1
                            ? "queda 1 intento." : "quedan " + intento.quedan + " intentos."));
        }

        intento.etapa = Etapa.AUTORIZADO;
    }

    // ==================================================================
    //  PASO 4 — la contrasena nueva
    // ==================================================================

    /**
     * Guarda la contrasena nueva y QUEMA EL TOKEN: sirve una sola vez.
     * Si quedara vivo, quien lo tenga puede volver a cambiarla mas tarde
     * sin pasar por nada.
     *
     * Las reglas de la contrasena no se comprueban aca: las aplica
     * UsuarioService.restablecerPassword, que es el mismo lugar donde las
     * aplican el registro y el cambio desde "Mi cuenta". Tres pantallas
     * con tres reglas distintas es exactamente lo que no queremos.
     */
    public void cambiarPassword(String token, String nueva) {
        Intento intento = vivo(token);
        if (intento.etapa != Etapa.AUTORIZADO) {
            throw new IllegalArgumentException("Todavía falta confirmar el código del mail.");
        }
        usuarios.restablecerPassword(intento.idUsuario, nueva);
        enCurso.remove(token);
    }

    // ==================================================================
    //  AYUDANTES
    // ==================================================================

    /** El intento de ese token, si existe y no vencio. */
    private Intento vivo(String token) {
        limpiarVencidos();
        Intento intento = token == null ? null : enCurso.get(token);
        if (intento == null) {
            throw new RecuperoVencidoException(
                    "El pedido de recupero venció o ya se usó. Empezá de nuevo desde tu email.");
        }
        return intento;
    }

    /**
     * Saca los vencidos del mapa. Se llama al entrar, que es mas simple
     * que una tarea programada y alcanza de sobra: son unas pocas
     * entradas y el unico momento en que molestan es cuando se consultan.
     */
    private void limpiarVencidos() {
        Instant ahora = Instant.now();
        enCurso.values().removeIf(i -> i.vence.isBefore(ahora));
    }

    private String nuevoToken() {
        byte[] bytes = new byte[24];
        azar.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String soloDigitos(String texto) {
        return texto.replaceAll("\\D", "");
    }

    /**
     * Donde empieza el bloque tapado, contando sobre los DIGITOS del
     * numero y no sobre la cadena, que trae espacios, guiones y el mas.
     *
     * Se tapan cuatro digitos y los dos ultimos quedan a la vista. Si el
     * numero es corto se tapan menos, pero siempre queda al menos uno
     * visible adelante: un numero enteramente tapado no le dice nada a
     * quien lo tiene que reconocer.
     */
    private static int[] bloqueTapado(String telefono) {
        int n = soloDigitos(telefono).length();
        int cola = n >= OCULTOS + COLA_VISIBLE + 1 ? COLA_VISIBLE : 1;
        int tapados = Math.min(OCULTOS, n - cola - 1);
        return new int[]{ n - cola - tapados, tapados };
    }

    /** Los digitos que la persona tiene que escribir. */
    static String digitosTapados(String telefono) {
        int[] b = bloqueTapado(telefono);
        return soloDigitos(telefono).substring(b[0], b[0] + b[1]);
    }

    /**
     * El numero como se muestra en pantalla.
     *
     * Se respetan los espacios y los guiones del original para que
     * conserve la forma: "+54 9 261 5•• ••34" se reconoce de un vistazo,
     * y "5492615••••34" no.
     */
    static String enmascarar(String telefono) {
        int[] b = bloqueTapado(telefono);
        StringBuilder salida = new StringBuilder();
        int visto = 0;
        for (char c : telefono.toCharArray()) {
            if (!Character.isDigit(c)) { salida.append(c); continue; }
            salida.append(visto >= b[0] && visto < b[0] + b[1] ? '•' : c);
            visto++;
        }
        return salida.toString();
    }

    /**
     * El email tapado, para que la pantalla pueda decir a donde fue el
     * codigo sin escribir la direccion entera. El dominio queda entero:
     * es lo que necesita saber la persona para ir a buscarlo.
     */
    static String enmascararEmail(String email) {
        int arroba = email.indexOf('@');
        if (arroba <= 0) return email;
        String antes = email.substring(0, arroba);
        String visible = antes.substring(0, Math.min(2, antes.length()));
        return visible + "•".repeat(Math.max(3, antes.length() - visible.length()))
               + email.substring(arroba);
    }
}
