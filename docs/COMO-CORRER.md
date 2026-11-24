# Cómo correr el programa

Todo el sitio sale de **un solo servidor**. No hace falta levantar nada aparte
para el frontend: las páginas las arma Spring Boot.

---

## El camino corto (3 pasos)

### 1. Que MySQL esté prendido

System Settings → **MySQL** → el botón tiene que decir *Stop MySQL Server*
(eso quiere decir que ya está corriendo). Si dice *Start*, apretalo.

### 2. Arrancar el servidor

Desde VS Code, con el proyecto abierto:

**Cmd+Shift+B**

Es la tarea por defecto. Abre una terminal abajo y empieza a escribir. Está
listo cuando aparece:

```
Started GestorInventarioApplication in 8.0 seconds
```

> Si preferís la terminal a mano:
> ```bash
> cd backend
> ./arrancar.sh
> ```

### 3. Abrir el navegador

**http://localhost:8080**

Y listo. Desde ahí se recorre todo: la portada, la tienda, los vinos, el
carrito, la compra, Mi cuenta.

---

## Para entrar al sitio

| Cuenta | Email | Contraseña | A dónde entra |
|---|---|---|---|
| Cliente | `juan@mail.com` | `Cliente123` | La tienda, Mi cuenta, las compras |
| Administrador | `admin@albaenlosandes.com` | `Admin123` | El panel de administración |

Se ingresa en **http://localhost:8080/cuenta**. Al administrador el sitio lo
manda solo a `/admin.html`; al cliente, a la portada.

Son credenciales de demo, están en `sql/gestor_inventario_alba.sql`.

---

## Las direcciones principales

| Para ver | Dirección |
|---|---|
| Portada | http://localhost:8080/ |
| La tienda | http://localhost:8080/tienda |
| Los vinos | http://localhost:8080/vinos |
| El carrito | http://localhost:8080/tienda/carrito |
| Ingresar / Mi cuenta | http://localhost:8080/cuenta |
| Panel de administración | http://localhost:8080/admin.html |
| La finca | http://localhost:8080/finca |
| Contacto | http://localhost:8080/contacto |

---

## Para apagarlo

En la terminal donde está corriendo: **Ctrl+C**.

Si quedó colgado de una corrida anterior y al arrancar dice
*"port 8080 already in use"*:

```bash
lsof -ti:8080 | xargs kill
```

(También está como tarea de VS Code: *Apagar el servidor*.)

---

## Si toco un archivo, ¿hace falta reiniciar?

Sí, siempre. Spring Boot no recarga en caliente. Después de cambiar una
plantilla, un CSS, un JavaScript o cualquier `.java`: **Ctrl+C y arrancar de
nuevo**.

---

## Cuando algo no anda

| Lo que ves | Qué pasa | Qué hacer |
|---|---|---|
| Todas las páginas tiran error 500 | MySQL apagado, o la clave no llegó | Prender MySQL y arrancar con `./arrancar.sh` (no con `mvnw` suelto) |
| "port 8080 already in use" | Quedó una corrida vieja | `lsof -ti:8080 \| xargs kill` |
| El cambio que hice no aparece | No reiniciaste | Ctrl+C y arrancar otra vez |
| Los comprobantes por correo no llegan | La red bloquea el puerto SMTP | Ver [`MEJORAS-PENDIENTES.md`](MEJORAS-PENDIENTES.md); con la clave de API de Brevo guardada sale por el 443 |
| El botón de Google no aparece | Falta autorizar `http://localhost:8080` en Google Cloud | Se ingresa igual con email y contraseña |
| El panel te devuelve a Mi cuenta | Entraste con una cuenta que no es ADMIN | Ingresar con `admin@albaenlosandes.com` |
| No aparece la ficha de Mercado Pago | Falta la credencial | Ver [`MERCADO-PAGO.md`](MERCADO-PAGO.md); el sitio anda igual con tarjeta y transferencia |

---

## Qué hace `arrancar.sh` y por qué conviene usarlo

Antes de levantar Spring Boot saca las claves del correo del llavero de macOS y
las pone como variables de entorno. Si arrancás con `./mvnw spring-boot:run`
directo, el sitio anda igual pero **los correos no salen** (comprobantes de
compra y el código del recupero de contraseña), y eso no da ningún error
visible: es el síntoma más confuso del proyecto.

Además guarda el log en `backend/arranque.log`, así que un error que pasó hace
rato se puede buscar después:

```bash
grep -i comprobante backend/arranque.log
```
