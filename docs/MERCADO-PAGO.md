# Cobrar con Mercado Pago

El código ya está hecho. Lo que falta es **tu credencial**: sin ella la ficha
no aparece en el checkout y el sitio sigue andando con tarjeta y transferencia,
como hasta ahora.

Mientras esto sea un trabajo de facultad va la credencial de **prueba**: el
circuito es el real y la plata no se mueve.

---

## 1) Sacar la credencial de prueba

1. Entrá a **https://www.mercadopago.com.ar/developers** con tu cuenta de
   Mercado Pago (si no tenés, es gratis crearla).
2. **Tus integraciones** → **Crear aplicación**. Nombre: `Alba en los Andes`.
   Producto: *Pagos online* → *Checkout Pro*.
3. Dentro de la aplicación: **Credenciales** → solapa **Prueba**.
4. Copiá el **Access Token** (no la Public Key).

> **Cuidado: el prefijo ya no distingue nada.** Antes las credenciales de
> prueba empezaban con `TEST-`; hoy las de prueba y las productivas empiezan
> las dos con `APP_USR-`, y lo único que las separa es **de qué solapa las
> copiaste**: *Prueba* o *Producción*. Mirá la solapa, no el texto.
>
> El sitio asume prueba por defecto y por eso muestra el aviso de "no se cobra
> plata de verdad". Si alguna vez se cobra en serio, se arranca con
> `MP_MODO=produccion ./arrancar.sh` y las credenciales de la solapa
> *Producción*.

---

## 2) Guardarlo una sola vez

```bash
security add-generic-password -U -s mercadopago-alba -a access-token -w
```

Pega el token cuando lo pida y listo: queda en el llavero de macOS, cifrado y
fuera de la carpeta del proyecto. `arrancar.sh` lo saca de ahí cada vez, igual
que las claves del correo. **Nunca va al repositorio**: un access token
publicado es una credencial de cobro publicada.

---

## 3) Correr la base nueva

El pago agrega un valor al `ENUM` de `medio_pago` y dos columnas. Se corre
**una sola vez**:

```bash
mysql -u root -p gestor_inventario < sql/agregar_mercado_pago.sql
```

Si te olvidás de esto, el pedido falla al guardarse con un error de MySQL que
dice que `'MERCADO_PAGO'` no es un valor válido de la columna.

---

## 4) Arrancar y probar

```bash
cd backend && ./arrancar.sh
```

Al arrancar tiene que decir:

```
Mercado Pago en modo PRUEBA: no se cobra plata de verdad.
  (el token tiene que ser el de la solapa 'Prueba' del panel de Mercado Pago)
  Sin MP_URL_PUBLICA: despues de pagar hay que apretar 'Ya pagué' en la confirmacion.
```

Después, en http://localhost:8080:

1. Agregá un vino al carrito y andá a **Finalizar compra**.
2. Elegí **Mercado Pago**. Aparece el aviso de modo de prueba.
3. **Pagar** → te lleva al sitio de Mercado Pago.
4. Pagá con una **tarjeta de prueba** (abajo).
5. Volvé a http://localhost:8080/tienda/pedido/*(el número de tu pedido)* y
   apretá **"Ya pagué, verificar"**. El pedido pasa a pagado, aparece el
   número de pago y sale el comprobante por correo.

### Con qué cuenta se paga

En modo de prueba **no se paga con tu cuenta real de Mercado Pago**: si entrás
con ella, el pago se rechaza porque serías vendedor y comprador a la vez.

Dos caminos:

- **Pagar como invitado**, sin iniciar sesión, con una tarjeta de prueba. Es el
  más rápido.
- **Entrar con el usuario de prueba** que figura en esa misma pantalla de
  Credenciales (`TESTUSER...` y su contraseña). Conviene en una **ventana de
  incógnito**, para no pisar tu sesión real.

### Tarjetas de prueba

Las publica Mercado Pago y cambian de vez en cuando: la lista al día está en
**Tus integraciones → (tu aplicación) → Cuentas de prueba / Tarjetas de
prueba**. El criterio que no cambia:

| Para que el pago salga | Qué poner en el nombre del titular |
|---|---|
| Aprobado | `APRO` |
| Rechazado por fondos | `FUND` |
| Pendiente | `CONT` |

Con cualquier fecha futura y cualquier código de seguridad.

---

## Por qué hay que apretar "Ya pagué"

Mercado Pago **no acepta `localhost`** como dirección de vuelta: no puede
mandarte de nuevo a una dirección que solo existe en tu máquina. Así que el
sitio no espera que vuelvas solo — te pregunta a Mercado Pago cuando se lo
pedís.

Si algún día el sitio tiene una dirección de internet (o querés probar con un
túnel tipo ngrok), se pone así y la vuelta pasa a ser automática:

```bash
MP_URL_PUBLICA='https://lo-que-sea.ngrok-free.app' ./arrancar.sh
```

---

## Qué hace el sitio y qué no

**Sí:**
- Arma la preferencia con el total que calculó el servidor, no el navegador.
- Guarda el número de pago de Mercado Pago en el pedido, como respaldo.
- Le pregunta a Mercado Pago si el pago está aprobado **y si el monto alcanza**:
  un pago aprobado por menos plata que el total no se acepta.
- Si Mercado Pago no contesta al abrir el pago, cancela el pedido recién creado
  y devuelve el stock.
- Manda el comprobante **recién cuando el pago está confirmado**.

**No:**
- No guarda ni ve datos de tarjeta: eso pasa dentro de Mercado Pago.
- No tiene webhook. Si el cliente paga y nunca vuelve al sitio, el pedido queda
  "esperando confirmación" hasta que alguien aprete el botón. En el panel se ve
  como **"Mercado Pago sin pagar"**.
- No hace devoluciones desde el panel: eso se hace en Mercado Pago, con el
  número de pago que quedó guardado en el pedido.

---

## Cuando algo no anda

| Lo que ves | Qué pasa |
|---|---|
| La ficha de Mercado Pago no aparece | No hay token. Mirá lo que dice al arrancar |
| "No pudimos abrir el pago de Mercado Pago" | El token es inválido o venció. El pedido se canceló solo |
| Error de MySQL al confirmar la compra | Falta correr `sql/agregar_mercado_pago.sql` |
| "Mercado Pago todavía no nos confirma el pago" | O no pagaste, o tardó. Esperá unos segundos y volvé a apretar |
| El pago es de prueba pero quiero ver el error real | `grep -i "mercado pago" backend/arranque.log` |
