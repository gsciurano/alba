-- ============================================================
--  ACTUALIZAR EL CATALOGO AL CATALOGO REAL DE LA BODEGA
--  Fuente: https://albaenlosandes.com (tienda en pesos)
--
--  USAR ESTE SCRIPT si la base 'gestor_inventario' YA EXISTE y no
--  queres perder los datos que tenga (pedidos, movimientos, usuarios).
--  A diferencia de gestor_inventario_alba.sql, este NO borra nada:
--  solo hace UPDATE de precio, descripcion, varietal, anada e imagen,
--  buscando cada vino por su nombre.
--
--  Ejecutar:  mysql -u root -p gestor_inventario < actualizar_catalogo_real.sql
-- ============================================================
USE gestor_inventario;

-- ---------- LINEA FINCA ----------
UPDATE productos SET
  linea = 'FINCA', varietal = 'Chardonnay', anada = 2024, precio = 15000.00,
  descripcion = 'Fresco y frutado. Color amarillo verdoso brillante, aroma intenso a frutos tropicales, citricos y frutos secos. Acidez muy equilibrada. Ideal con ensaladas, pescados, mariscos y pastas con salsas suaves.',
  imagen_url = 'https://albaenlosandes.com/wp-content/uploads/2021/04/albaenlosandes-productos-chardonnay.jpg'
WHERE nombre = 'Finca Chardonnay';

UPDATE productos SET
  linea = 'FINCA', varietal = 'Malbec', anada = 2023, precio = 15000.00,
  descripcion = 'Vino joven de intenso color violaceo. Aromas a frutos rojos y negros (frutilla, mora, ciruela). En boca es jugoso, de refrescante acidez y taninos suaves. Ideal con cerdo o cordero a la parrilla y hamburguesas.',
  imagen_url = 'https://albaenlosandes.com/wp-content/uploads/2021/04/albaenlosandes-productos-finca_malbec.jpg'
WHERE nombre = 'Finca Malbec';

-- ---------- LINEA ESTATE RESERVE ----------
UPDATE productos SET
  linea = 'ESTATE_RESERVE', varietal = 'Malbec', anada = 2022, precio = 18000.00,
  descripcion = 'Malbec de color violeta intenso. Amplio aroma frutal (ciruelas y frutillas), flores, mineral y notas de chocolate y tabaco. Taninos suaves de gran volumen y final prolongado. Ideal con pollo asado, ternera y quesos.',
  imagen_url = 'https://albaenlosandes.com/wp-content/uploads/2021/04/albaenlosandes-productos-ER_malbec.jpg'
WHERE nombre = 'Estate Reserve Malbec';

UPDATE productos SET
  linea = 'ESTATE_RESERVE', varietal = 'Cabernet Franc', anada = 2022, precio = 18000.00,
  descripcion = 'Elegante Cabernet Franc de color rojo granate intenso. Notas de frutas rojas, pimiento, hierbas, especias y mineral, de estructura sedosa. Ideal con platos a base de tomate, legumbres, carnes de caza y estofados.',
  imagen_url = 'https://albaenlosandes.com/wp-content/uploads/2021/04/albaenlosandes-productos-ER_cabernet_franc.jpg'
WHERE nombre = 'Estate Reserve Cabernet Franc';

-- ---------- LINEA GRAN RESERVA ----------
UPDATE productos SET
  linea = 'GRAN_RESERVA', varietal = 'Malbec', anada = 2021, precio = 25000.00,
  descripcion = 'Intenso, de gran cuerpo e identidad. Color rojo violaceo con tonos rubi. Aroma a frutos negros, especias, hierbas, flores y notas minerales. Muy equilibrado, amplio y de final largo. Ideal con ternera asada y pastas.',
  imagen_url = 'https://albaenlosandes.com/wp-content/uploads/2025/02/albaenlosandes-productos-GR_malbec-2025.jpg'
WHERE nombre = 'Gran Reserva Malbec';

UPDATE productos SET
  linea = 'GRAN_RESERVA', varietal = 'Cabernet Franc', anada = 2021, precio = 25000.00,
  descripcion = 'Vino complejo, elegante y profundo. Color rojo granate intenso con tonos rubi. Aroma a frutos negros, morron rojo asado, especias y notas minerales. Ideal con carnes de caza asada, estofados y quesos.',
  imagen_url = 'https://albaenlosandes.com/wp-content/uploads/2021/04/albaenlosandes-productos-GR_cabernet_franc.jpg'
WHERE nombre = 'Gran Reserva Cabernet Franc';

UPDATE productos SET
  linea = 'GRAN_RESERVA', varietal = 'Cabernet Sauvignon', anada = 2021, precio = 25000.00,
  descripcion = 'Premium de gran estructura y complejidad, con la impronta calcarea del Valle de Uco. Anejamiento en barrica de roble frances.',
  imagen_url = 'https://albaenlosandes.com/wp-content/uploads/2025/02/albaenlosandes-productos-GR_cabernet_sauvignon.jpg'
WHERE nombre = 'Gran Reserva Cabernet Sauvignon';

UPDATE productos SET
  linea = 'GRAN_RESERVA', varietal = 'Chardonnay', anada = 2022, precio = 25000.00,
  descripcion = 'Blanco premium de guarda, con paso por barrica de roble frances. Expresion de altura del vinedo de Tupungato a 1.100 metros sobre el nivel del mar.',
  imagen_url = 'https://albaenlosandes.com/wp-content/uploads/2025/02/albaenlosandes-productos-GR_chardonay.jpg'
WHERE nombre = 'Gran Reserva Chardonnay';

-- ---------- EDICIONES ESPECIALES ----------
UPDATE productos SET
  linea = 'EDICION_ESPECIAL', varietal = 'Blend (Malbec, Cabernet Franc, Cabernet Sauvignon)', anada = 2020, precio = 35000.00,
  descripcion = 'Blend 70% Malbec, 15% Cabernet Franc y 15% Cabernet Sauvignon. Complejo, elegante y profundo. Color rojo violaceo con tonos rubi. Muy equilibrado, amplio y de final largo. Se sugiere decantar.',
  imagen_url = 'https://albaenlosandes.com/wp-content/uploads/2022/10/albaenlosandes-productos-la_mujer-v2.jpg'
WHERE nombre = 'La Mujer';

UPDATE productos SET
  linea = 'EDICION_ESPECIAL', varietal = 'Blend (Cabernet Franc, Malbec)', anada = 2020, precio = 35000.00,
  descripcion = 'Blend 80% Cabernet Franc y 20% Malbec. Intenso, armonioso y expresivo. Notas de frutas negras y pimenton ahumado con especias y chocolate de su paso por barrica. Gran estructura y acidez equilibrada.',
  imagen_url = 'https://albaenlosandes.com/wp-content/uploads/2025/02/albaenlosandes-productos-8m_blend-2025-1.jpg'
WHERE nombre = '8M Blend';

-- ---------- VERIFICACION ----------
SELECT id_producto, nombre, linea, varietal, anada, precio, stock_actual
FROM productos ORDER BY precio, nombre;
