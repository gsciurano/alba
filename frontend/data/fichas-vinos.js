/**
 * data/fichas-vinos.js
 * ============================================================
 * LA FICHA TECNICA DE CADA VINO.
 *
 * POR QUE ESTO NO ESTA EN LA BASE:
 * la tabla 'productos' guarda lo que cambia con el tiempo y lo que el
 * panel tiene que poder editar: precio, stock, anada, estado y una
 * descripcion corta. La ficha de cata es otra cosa: es texto editorial
 * fijo de la bodega (el suelo del vinedo, los meses de barrica, la
 * analitica del laboratorio). No cambia entre dos consultas al stock y
 * nadie la edita desde el panel, asi que meterla en MySQL obligaria a
 * agregar diez columnas de texto que ningun formulario llena.
 *
 * Es el mismo criterio que ya usaba vinos.js con el texto de cada linea:
 * lo editorial vive en el front-end, lo que se opera vive en la base.
 *
 * DE DONDE SALEN LOS DATOS:
 * de las fichas publicas de albaenlosandes.com/vinos/, una por vino.
 * Cada entrada guarda su 'origen' para poder volver a chequearla.
 *
 * COMO SE ENGANCHA CON LA BASE:
 * la clave de cada ficha es el NOMBRE exacto del vino, el mismo que
 * tiene la columna 'nombre' de la tabla productos. Si manana el admin
 * carga un vino nuevo que no esta en esta lista, la pagina de detalle
 * igual funciona: muestra los datos de la base y omite las secciones
 * que no tengan ficha. Ver fichaDe() al final del archivo.
 * ============================================================
 */

/**
 * Las diez etiquetas salen del mismo cuadro, asi que el vinedo se
 * describe una sola vez. Si se repitiera en cada ficha, corregir un
 * dato obligaria a corregirlo diez veces.
 */
export const VINEDO = {
  region:      "Tupungato, Valle de Uco, Mendoza",
  antiguedad:  "Más de 25 años",
  altura:      "1.100 metros sobre el nivel del mar",
  orientacion: "Oeste-Este, que favorece el escurrimiento natural del agua de lluvia y deshielo",
  conduccion:  "Espaldero alto",
  suelo:       "De origen aluvial, pobre, de textura franco-arenosa, con abundante piedra y " +
               "carbonato de calcio. Es un suelo calcáreo, y eso le da a los vinos elegancia y complejidad.",
  cosecha:     "Manual, en cajas plásticas de 12 kg"
};

/** Certificacion que llevan todas las etiquetas de la bodega. */
export const CERTIFICACION = "Vino vegano certificado por IRAM";

/**
 * Una entrada por vino, con el nombre de la base como clave.
 *
 *   presentacion  parrafo de apertura, para encabezar la pagina
 *   composicion   los varietales y sus porcentajes
 *   cata          vista / nariz / boca, como las publica la bodega
 *   vinificacion  que se hace con la uva al entrar a la bodega
 *   crianza       barrica: tipo, uso y meses
 *   maridaje      con que se recomienda tomarlo
 *   servicio      recomendacion al servir (solo donde la bodega la da)
 *   analitica     los numeros del laboratorio
 *   origen        la ficha publica de donde salieron los datos
 */
export const FICHAS = {

  // ---------------------------------------------------------- FINCA
  "Finca Chardonnay": {
    presentacion:
      "El blanco de entrada a la finca: fresco, frutado y de acidez bien marcada. " +
      "Fermenta en parte en acero y en parte en barrica, y no hace maloláctica, " +
      "para que la fruta llegue intacta a la copa.",
    composicion: "100% Chardonnay",
    cata: {
      vista: "Amarillo verdoso brillante.",
      nariz: "Intensa y expresiva, con notas de frutas tropicales, cítricos y frutos secos.",
      boca:  "Frutada, con una acidez muy equilibrada que alarga el final. De cuerpo amplio " +
             "y gran frescura, invita a seguir bebiendo."
    },
    vinificacion:
      "Al llegar a la bodega la uva pasa por una doble selección, en mesa y en cinta vibrante. " +
      "Se prensa el racimo entero y fermenta un 70% en tanques de acero inoxidable y un 30% en " +
      "barrica de roble francés. No se realiza fermentación maloláctica.",
    crianza: "Al menos 6 meses en barrica de roble francés.",
    maridaje: "Ensaladas, pescados, mariscos y pastas con salsas suaves.",
    analitica: { alcohol: "12,3%", acidez: "6,3 g/l", ph: "3,35", azucar: "1,85 g/l" },
    origen: "https://albaenlosandes.com/vinos/finca-chardonnay/"
  },

  "Finca Malbec": {
    presentacion:
      "El Malbec joven de la casa. Fermentación tradicional en acero y solo una parte con paso " +
      "por madera, para sumar complejidad sin taparle la fruta. Es el vino de la mesa de todos los días.",
    composicion: "100% Malbec",
    cata: {
      vista: "Color violáceo intenso.",
      nariz: "Frutos rojos y negros, sobre todo frutilla, mora y ciruela.",
      boca:  "Jugoso, de acidez refrescante y taninos suaves y equilibrados."
    },
    vinificacion:
      "Doble selección de la uva al ingresar a la bodega, en cinta a banda y vibrante. " +
      "Fermentación tradicional en tanques de acero inoxidable, con temperatura controlada " +
      "cercana a los 25 °C.",
    crianza: "El 30% del vino descansa 6 meses en barricas de roble francés de segundo uso.",
    maridaje: "Cerdo o cordero a la parrilla, y hamburguesas de cualquier tipo.",
    analitica: { alcohol: "12,3%", acidez: "5,7 g/l", ph: "3,72", azucar: "1,8 g/l" },
    origen: "https://albaenlosandes.com/vinos/finca-malbec/"
  },

  // -------------------------------------------------- ESTATE RESERVE
  "Estate Reserve Malbec": {
    presentacion:
      "Un paso más que el Finca: maceración en frío antes de fermentar y ocho meses de barrica " +
      "para el 80% del vino. Después del embotellado todavía descansa tres meses más antes de salir.",
    composicion: "100% Malbec",
    cata: {
      vista: "Color violeta intenso.",
      nariz: "Aroma frutal amplio, con ciruela y frutilla, violetas, notas minerales, chocolate y tabaco.",
      boca:  "Taninos suaves pero de mucho volumen, que dan un final largo y elegante."
    },
    vinificacion:
      "Selección de la uva en cinta a banda y vibrante. Maceración en frío a 8 °C durante cinco días " +
      "y fermentación alcohólica a temperatura controlada, cercana a los 30 °C.",
    crianza:
      "El 80% del vino pasa a barricas de roble francés de segundo uso, donde hace la fermentación " +
      "maloláctica y reposa ocho meses. Ya embotellado, descansa al menos tres meses más antes de salir " +
      "de la bodega.",
    maridaje: "Pollo asado con hierbas, ternera asada, salchichas o quesos.",
    analitica: { alcohol: "12,3%", acidez: "5,75 g/l", ph: "3,73", azucar: "1,8 g/l" },
    origen: "https://albaenlosandes.com/vinos/estate-reserve-malbec/"
  },

  "Estate Reserve Cabernet Franc": {
    presentacion:
      "El Cabernet Franc de la línea Estate Reserve, con el perfil herbal y especiado que da la " +
      "variedad en altura. Mismo trabajo de bodega que su hermano Malbec: frío, barrica de segundo " +
      "uso y descanso en botella.",
    composicion: "100% Cabernet Franc",
    cata: {
      vista: "Color rojo granate intenso.",
      nariz: "Frutas rojas (frambuesa, zarzamora), pimiento, hierbas y especias (jarilla, tomillo, " +
             "romero) y un fondo mineral a grafito.",
      boca:  "De estructura sedosa, con la acidez propia de la variedad sosteniendo el final."
    },
    vinificacion:
      "Selección de la uva en cinta a banda y vibrante. Maceración en frío a 8 °C durante cinco días " +
      "y fermentación alcohólica a temperatura controlada, cercana a los 30 °C.",
    crianza:
      "El 80% del vino hace la fermentación maloláctica y pasa ocho meses en barricas de roble francés " +
      "de segundo uso. Ya embotellado, descansa al menos tres meses más.",
    maridaje: "Platos a base de tomate, salsas con vinagre, legumbres como las lentejas beluga, " +
              "carnes de caza, estofados y empanadas.",
    analitica: { alcohol: "12,3%", acidez: "5,63 g/l", ph: "3,75", azucar: "1,8 g/l" },
    origen: "https://albaenlosandes.com/vinos/estate-reserve-cabernet-franc/"
  },

  // ---------------------------------------------------- GRAN RESERVA
  "Gran Reserva Malbec": {
    presentacion:
      "Uno de los vinos de guarda de la bodega. Se elabora en vasijas de concreto de 5.000 litros " +
      "con extracción manual, y hace doce meses de barrica de roble francés.",
    composicion: "100% Malbec",
    cata: {
      vista: "Color rojo violáceo intenso con tonos rubí.",
      nariz: "Frutos negros (ciruela, moras), especias (pimienta), hierbas, violetas y notas minerales.",
      boca:  "Intenso, de gran cuerpo e identidad. Muy equilibrado, amplio y de final largo."
    },
    vinificacion: "Elaboración tradicional en vasijas de concreto de 5.000 litros, con la extracción " +
                  "de la pulpa hecha a mano.",
    crianza: "12 meses en barrica de roble francés, donde además se produce la fermentación maloláctica.",
    maridaje: "Ternera asada, empanadas y pastas con salsas intensas.",
    analitica: { alcohol: "14,0%", acidez: "5,935 g/l", ph: "3,6", azucar: "1,80 g/l" },
    origen: "https://albaenlosandes.com/vinos/gran-reserva-malbec/"
  },

  "Gran Reserva Cabernet Franc": {
    presentacion:
      "Complejo, elegante y profundo. El Cabernet Franc llevado a doce meses de roble francés, " +
      "con el concreto como punto de partida.",
    composicion: "100% Cabernet Franc",
    cata: {
      vista: "Rojo granate intenso con tonos rubí.",
      nariz: "Frutos negros, morrón rojo asado, especias y notas minerales.",
      boca:  "Muy equilibrado, de cuerpo amplio y final largo."
    },
    vinificacion: "Elaboración tradicional en vasijas de concreto de 5.000 litros, con extracción manual.",
    crianza: "12 meses en barrica de roble francés, donde se realiza la fermentación maloláctica.",
    maridaje: "Carnes de caza asadas, estofados y quesos.",
    analitica: { alcohol: "14,0%", acidez: "5,935 g/l", ph: "3,6", azucar: "1,80 g/l" },
    origen: "https://albaenlosandes.com/vinos/gran-reserva-cabernet-franc/"
  },

  "Gran Reserva Cabernet Sauvignon": {
    presentacion:
      "El Cabernet Sauvignon de guarda de la finca, con la frescura de eucalipto y pimiento que " +
      "da la altura y el tostado que aporta el año de barrica.",
    composicion: "100% Cabernet Sauvignon",
    cata: {
      vista: "Rojo granate intenso y brillante.",
      nariz: "Notas frescas de eucalipto y pimiento verde, junto a especias, tabaco y cacao " +
             "que llegan del roble francés.",
      boca:  "Taninos presentes pero elegantes, con una acidez que le da persistencia."
    },
    vinificacion: "Elaboración tradicional en vasijas de concreto de 5.000 litros, con extracción manual.",
    crianza: "12 meses en barrica de roble francés, donde se realiza la fermentación maloláctica.",
    maridaje: "Carnes rojas estofadas o asadas y quesos maduros.",
    analitica: { alcohol: "14,0%", acidez: "5,85 g/l", ph: "3,7", azucar: "1,80 g/l" },
    origen: "https://albaenlosandes.com/vinos/gran-reserva-cabernet-sauvignon/"
  },

  "Gran Reserva Chardonnay": {
    presentacion:
      "El blanco de guarda de la bodega. Un Chardonnay de gran estructura ácida y paso por barrica, " +
      "bien distinto del Finca.",
    composicion: "100% Chardonnay",
    cata: {
      vista: "Amarillo dorado brillante.",
      nariz: "Muy compleja, con flor de azahar, piel de limón y durazno blanco. La barrica suma " +
             "notas especiadas y de vainilla.",
      boca:  "Contundente, de gran estructura ácida."
    },
    vinificacion: "Cosecha manual y selección en bodega, como el resto de las etiquetas de la finca.",
    crianza: "Con paso por barrica de roble francés.",
    maridaje: "Carnes blancas y mariscos.",
    analitica: { alcohol: "13,0%", acidez: "6,75 g/l", ph: "3,37", azucar: "2 g/l" },
    origen: "https://albaenlosandes.com/vinos/gran-reserva-chardonnay/"
  },

  // ----------------------------------------------- EDICIONES ESPECIALES
  "La Mujer": {
    presentacion:
      "Una edición especial de partida limitada. Se hace por microvinificación, en vasijas de " +
      "500 litros y con las extracciones a mano, y descansa entre doce y dieciocho meses en roble francés.",
    composicion: "70% Malbec, 15% Cabernet Franc y 15% Cabernet Sauvignon",
    cata: {
      vista: "Rojo violáceo con tonos rubí.",
      nariz: "Compleja y profunda: frutos negros (ciruela, moras), especias (pimienta), hierbas, " +
             "violetas y notas minerales.",
      boca:  "Muy equilibrada, amplia y de final largo."
    },
    vinificacion: "Microvinificación en vasijas pequeñas de 500 litros, con las extracciones hechas a mano.",
    crianza: "De 12 a 18 meses en barrica de roble francés, donde hace la fermentación maloláctica.",
    maridaje: "Carnes asadas, pastas con salsas potentes y quesos maduros.",
    servicio: "Se sugiere decantar antes de servir.",
    analitica: { alcohol: "14,0%", acidez: "5,9 g/l", ph: "3,55", azucar: "1,85 g/l" },
    origen: "https://albaenlosandes.com/vinos/la-mujer/"
  },

  "8M Blend": {
    presentacion:
      "La otra edición especial de la finca, con el Cabernet Franc al frente. Intenso, armonioso " +
      "y expresivo, también de microvinificación y hasta dieciocho meses de barrica.",
    composicion: "80% Cabernet Franc y 20% Malbec",
    cata: {
      vista: "Rojo granate intenso.",
      nariz: "Frutas negras y pimentón ahumado, con sutiles notas de especias y chocolate que " +
             "aporta la barrica.",
      boca:  "De gran estructura y acidez equilibrada."
    },
    vinificacion: "Microvinificación en vasijas pequeñas de 500 litros, con extracción manual.",
    crianza: "De 12 a 18 meses en barrica de roble francés, donde se produce la fermentación maloláctica.",
    maridaje: "Carnes de caza asadas y quesos maduros.",
    servicio: "Se sugiere decantar antes de servir.",
    analitica: { alcohol: "14,0%", acidez: "5,78 g/l", ph: "3,65", azucar: "1,80 g/l" },
    origen: "https://albaenlosandes.com/vinos/8m-blend/"
  }
};

/**
 * Devuelve la ficha de un vino, o null si no hay.
 *
 * Devolver null y no un objeto vacio es a proposito: la pagina de detalle
 * necesita distinguir "este vino tiene ficha" de "este vino es uno nuevo
 * cargado desde el panel", para no dibujar secciones en blanco.
 */
export function fichaDe(nombre) {
  return FICHAS[nombre] || null;
}
