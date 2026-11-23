# Estudio de Medusa TV

**Una interfaz a tres metros**

Capa visual de `main` (2.4.0), 23 de noviembre de 2026 · Estable publicada: 2.3.12 · Madrid

> Este documento es la versión larga del estudio publicado en
> <https://gitcorion.github.io/Medusa/>. Allí puedes probar el foco con las flechas del teclado y
> ver las capturas a tamaño completo. Aquí están el método, las decisiones y la deuda.

## 1. Ficha

| | |
|---|---|
| Producto | Medusa TV, cliente Jellyfin nativo para Android TV y tablet |
| Repositorio | <https://github.com/GitCorion/Medusa> |
| Distribución | APK firmado en GitHub Releases, actualización OTA desde la app |
| Plataforma | Android 8.0+, `minSdk 26`, `targetSdk` 36 |
| Construcción | Kotlin 2.0.21, Compose TV, Hilt, Room 22, Media3 |
| Biblioteca de prueba | Jellyfin local; capturas con la biblioteca sincronizada |
| Capturas | Televisor de 1920 × 1080, capa visual de `main` (2.4.0) |
| Licencia | GPL-3.0 |

La interfaz es **cinematográfica y de diez pies**: arte a sangre, un dock de iconos, un foco de
alto contraste y una sola familia tipográfica. La identidad abisal vive en el arte, la luz y la
tipografía; el comportamiento es el que el usuario espera de una tele.

## 2. Método

El estudio se hizo sobre cuatro fuentes, no sobre una maqueta:

1. **Código real.** Lectura de `ui/theme`, `TvTopBar`, tarjetas, navegación, ajustes, reproductor y
   capas de datos. Los tokens que aparecen aquí son los que renderiza `Color.kt`.
2. **Televisor real.** Capturas con `adb` de la app instalada en un Android TV a 1080p, con una
   biblioteca de prueba grande. Nada de composiciones en Figma.
3. **Registro de commits.** Las tres pasadas del 22 y 23 de noviembre se citan con su hora y su
   intención. Un estudio de diseño también debe incluir lo que se descartó.
4. **Auditoría de diseño.** La crítica interna del 22 de noviembre, con sus hallazgos de
   accesibilidad, contraste y semántica. La deuda declarada del final sale de ahí.

**Límites.** No hay pruebas con usuarios externos, ni medición automática de frame time, ni una
muestra amplia de dispositivos. Las conclusiones de rendimiento son de diseño y de observación en
un televisor, no un benchmark estadístico.

## 3. El problema: tres metros

La app se usa desde un sofá. Eso cambia cuatro cosas:

- **La vista.** A tres metros, un texto de 12 px ocupa menos de cuatro milímetros. La densidad de
  un dashboard no sirve; hace falta jerarquía.
- **La mano.** El mando tiene D-pad, OK y BACK. No hay hover, no hay scroll fino y no hay puntero.
  El foco es el cursor y tiene que estar siempre visible.
- **La sala.** La pantalla compite con una lámpara, una ventana o el reflejo de una OLED. El
  contraste no es un adorno.
- **El hardware.** Un dongle de gama baja no perdona blur en tiempo real ni animaciones de
  composición por tarjeta. La fluidez es una restricción de producto.

De ahí sale la regla que gobierna el resto: **si un elemento no comunica foco, jerarquía o dato,
se elimina**. Y una segunda, aprendida en las pasadas intermedias: **si una interacción no es la
que el usuario espera de una tele, no se inventa**.

## 4. Principios y tensiones

### 4.1. Cine primero, tele después

El arte ocupa la pantalla y el chrome desaparece. La navegación es un dock de iconos de 18 dp; el
foco no exige OK y BACK sigue siendo el retroceso. Los filtros son chips compactos, las tarjetas
tienen radio de 10 dp y las insignias, 4 dp. No hay marcos complejos ni metáforas literarias.

### 4.2. El arte manda

Los pósters y backdrops van a sangre. El título y el año viven fuera de la carátula; Continuar
viendo usa el card oficial con título y progreso sobre el arte. No hay cajones grises con texto
incrustado ni bordes de 1 px en reposo.

### 4.3. Un solo foco

Exactamente un elemento lleva el borde de 1,5 dp y el `zIndex(10f)`. En reposo no se simula foco.
Esto evita el ruido de las interfaces que iluminan todas las tarjetas a la vez y hace inequívoca
la posición del mando.

### 4.4. La tensión: 60 fps frente a feedback

La primera versión atenuaba las tarjetas no enfocadas y escalaba la enfocada. Se veía bien en un
monitor y costaba composición en la tele; además, el escalado provocaba temblor horizontal en los
carruseles. La decisión final elimina las dos cosas:

- **Tarjetas:** borde de 1,5 dp + `zIndex`, sin escala.
- **Vecinas:** mantienen su opacidad al 100 %; no hay spotlight.
- **Título:** pasa de `0.75f` a plena luminosidad.

Es menos "vistoso" que un spotlight y más estable. Esa es la decisión central del producto.

## 5. Tres pasadas

El 22 y el 23 de noviembre de 2026 la interfaz cambió tres veces. Los commits son reales:

| Hora | Commit | Decisión |
|---|---|---|
| 22 nov, 21:27 | `8430b41` | **La paleta abisal.** El negro digital y el cian neón de plantilla se sustituyen por tinta abisal y una luz bioluminiscente. La paleta sobrevive; el acento termina en el cian `#00D2FF` del logotipo. |
| 22 nov, 21:27 | `2d6958f` | **Navegación tipográfica.** Subrayados y pestañas de texto. Se prueba en la tele y se descarta: la navegación vuelve al dock de iconos. |
| 22 nov, 21:28 | `f47e297` | **Hero batimétrico.** Arcos estáticos, anillo de contacto y un pulso de sonar. La profundidad se veía bien, pero la metáfora no aportaba orientación y añadía una animación autónoma. Se descarta. |
| 22 nov, 22:05 | `43ab861` | **Legibilidad en la tele real.** Ajustes después de mirar la interfaz a tres metros: tamaños, contraste y objetivos. Esta pasada sobrevive entera. |
| 22 nov, 22:11 | `886ec79` | **De editorial a cinematográfico.** Se retira Fraunces. La interfaz se queda con una sans de instrumento; la versión final usa DM Sans. |
| 22 nov, 22:56 | `8ed14d9` | **Material TV.** Tabs pill, foco 1,1x, chips de 48 dp y overscan. La convención de componentes no era la identidad; se retira. |
| 23 nov, 13:43 | `507fa6d` | **La identidad vuelve al cine.** DM Sans, cian `#00D2FF`, dock de iconos, borde de 1,5 dp y cero escala. La paleta gana cobalto, aqua y oro. |

La lección no es "las metáforas son malas". Es que una interfaz de tele tiene un contrato de
interacción que el usuario ya conoce. La identidad puede ser específica; el comportamiento no
debería obligar a reaprender el mando.

## 6. El sistema

### 6.1. Color

El fondo es tinta abisal (`#04060A`). La luz principal es el cian eléctrico del logotipo
(`#00D2FF`); el cobalto y el aqua aportan profundidad; el ámbar y el oro quedan para favoritos,
valoraciones y visto. Los neutros son zinc y grafito, no grises fríos de plantilla.

| Token | Hex | Uso | Contraste sobre abismo |
|---|---|---|---|
| Tinta abisal | `#04060A` | Fondo raíz y tinta sobre acentos | — |
| Agua profunda | `#0A0F1D` | Superficie base | 1,1:1 (superficie) |
| Agua elevada | `#10162A` | Controles y tarjetas | 1,1:1 (superficie) |
| Cian Medusa | `#00D2FF` | Foco, selección, progreso | 11,3:1 |
| Cobalto abisal | `#0B5EFF` | Gradientes y brillos; no texto | 3,9:1 |
| Aqua glaciar | `#8BFFFF` | Reflejos y detalles | 17,2:1 |
| Ámbar cine | `#E5A44D` | Favoritos y valoraciones | 9,4:1 |
| Oro nitrato | `#D4AF37` | Visto y sello cinematográfico | 9,6:1 |
| Nota | `#FACC15` | Estrellas y notas | 13,2:1 |
| Blanco puro | `#FFFFFF` | Texto principal | 20,3:1 |
| Zinc | `#A1A1AA` | Metadatos y texto secundario | 7,9:1 |
| Grafito | `#71717A` | Micro-datos y separadores | 4,2:1 |

El cobalto no se usa como texto: 3,9:1 no llega a AA. El grafito se reserva a micro-datos y
separadores. Sobre papel de estudio, el texto siempre es tinta abisal.

### 6.2. Tipografía

Una sola familia: **DM Sans**, variable y OFL. Cubre titulares, secciones, UI, metadatos y cuerpo.
La escala:

| Rol | Tamaño | Peso |
|---|---|---|
| Display / hero | 36 px | 700 |
| Headline | 24 px | 700 |
| Title | 16 px | 600 |
| Body | 13 px | 400 |
| Label | 11 px | 600 |

Prohibido: serif editorial, mayúsculas decorativas, tracking amplio, separadores con punto medio,
flechas pegadas al texto y monocromo para datos por defecto. El texto se escribe en caja de frase.
Los ClearLogo del contenido son la única voz display variable.

### 6.3. Forma y foco

| Elemento | Radio |
|---|---|
| Insignia | 4 px |
| Chip | 10 px |
| Tarjeta | 10 px |
| Cápsula del dock | 18 px |
| Círculo | 999 px |

Anatomía del foco:

1. Borde de **1,5 px** cian sobre el póster y `zIndex(10f)`.
2. Título a plena luminosidad; las tarjetas vecinas conservan su opacidad.
3. Dock de iconos de 18 px en cápsulas de 18 px de radio.
4. Sin escala de tarjeta, sin spotlight, sin blur.

### 6.4. Movimiento

- No hay animaciones autónomas, bucles, sonar ni partículas decorativas en la UI.
- La interfaz solo responde al mando. Sin foco, no hay movimiento.
- Respeta la escala de animación del sistema. Con movimiento reducido, la interfaz se queda quieta.
- Cero blur en tiempo real.

## 7. Las pantallas

### Inicio

Hero a sangre con scrim, ClearLogo, metadatos, una acción primaria y paginación. La barra superior
es un dock de iconos; el foco no rellena bloques. **Lo que funciona:** jerarquía inmediata y una
sola acción principal. **Lo que vigilar:** el equilibrio entre el arte y los metadatos cuando el
título es muy largo, y el autoavance del carrusel.

### Inicio, filas

Pósters con el título fuera; Continuar viendo con título y progreso sobre el arte. Las plataformas
son placas discretas. **Lo que funciona:** el arte manda y la fila se lee de un vistazo. **Lo que
vigilar:** la densidad de metadatos y el contraste de los badges sobre arte claro.

### Descubrir

Filtros de formato y chips de género, con resultados en la misma pantalla. La tarjeta enfocada
lleva borde de 1,5 px. **Lo que funciona:** exploración sin cambiar de contexto. **Lo que vigilar:**
la carga cognitiva de filtro + chips + resultados; el estado vacío debe explicar qué hacer.

### Ficha

Póster a la izquierda, metadatos editoriales a la derecha y acciones circulares. La sinopsis
respira sobre el fondo; no hay panel que la encierre. **Lo que funciona:** se lee como una página
de cine, no como un formulario. **Lo que vigilar:** el contraste de metadatos sobre backdrops
luminosos y el estado de visto/favorito.

### Buscar

Teclado a la izquierda, sugerencias y resultados a la derecha. Escribir no cambia de pantalla.
**Lo que funciona:** teclas grandes y separación clara entre teclado y resultados. **Lo que
vigilar:** la precisión del D-pad en la rejilla y el orden de las sugerencias.

### Favoritos

Filtros y orden sobre la colección, no en un menú aparte. **Lo que funciona:** una colección
personal se siente como una estantería. **Lo que vigilar:** corazones y notas sobre el póster sin
tapar el arte.

### Ajustes

Barra lateral y grupos sobre superficie elevada, con el ítem activo resaltado. **Lo que funciona:**
el estado del servidor y la sincronización se leen de un vistazo. **Lo que vigilar:** la jerga
técnica y la longitud de la columna en pantallas pequeñas.

## 8. Accesibilidad

Lo que ya está:

- Contraste AA en los textos principales: blanco 20,3:1, aqua 17,2:1, cian 11,3:1, oro 9,6:1,
  ámbar 9,4:1, zinc 7,9:1.
- Foco visible en todos los elementos interactivos; no se depende del color solamente.
- Dock de iconos con etiquetas en el modo texto o ambos.
- Movimiento reducido respetado desde el sistema.
- Teclas físicas y `BACK` en TV; botón táctil de 38 px en tablet.
- Subtítulos con tamaño y fondo configurables.
- Textos en español e inglés.

Lo que falta o hay que vigilar:

- La capa táctil de tablet merece una pasada propia; el dock de 18 px se pensó para un mando.
- La semántica de selección en algunos rails puede mejorar (`Role.Checkbox`/`selected`) para
  lectores de pantalla.
- Los metadatos más pequeños sobre backdrops claros necesitan comprobación caso por caso.
- No hay pruebas automáticas de contraste ni de frames en CI.

## 9. La ingeniería que se ve

La fluidez no sale del renderizado: sale de decidir qué no se dibuja.

- **Cero blur.** `Modifier.blur()` y `RenderEffect` están prohibidos. La profundidad viene del arte,
  el scrim y la jerarquía, no de un shader.
- **Caché en cascada.** Inicio intenta memoria, snapshot en fichero, Room y generación en vivo, en
  ese orden. El feed tiene TTL de 15 minutos y se emite antes de completar la generación.
- **Reproducción con fallback.** `.strm` resuelto en el servidor, remux, DirectStream, transcode y
  transcodificación completa. La lista de candidatos nunca queda vacía. Con [TOD](https://github.com/GitCorion/TOD) y el
  [plugin Catalogo](https://github.com/GitCorion/Medusa-Jellyfin-Plugin), la app consulta el
  estado, pide `Prepare` y espera `stream_ready` antes de montar el player.
- **DeviceProfile serializado con `encodeDefaults`.** Sin eso, Jellyfin sustituye el perfil por
  `Type: Audio`, listas vacías y códecs vacíos. Hay una prueba golden.
- **Trabajo de fondo acotado.** Sincronización, indexado y enriquecimiento en workers separados,
  con concurrencia limitada y pausa cuando la app está visible o reproduciendo.
- **OTA verificada.** GitHub como único origen, 150 MB máximo, SHA-256 obligatorio, manifiesto con
  caducidad de siete días y kill-switch.

## 10. Lo que se descartó

| Se descartó | Motivo |
|---|---|
| Navegación tipográfica con subrayado | La tele espera un dock de iconos; el foco no debe exigir OK. |
| Corriente ondulada, sonar y batimetría | Metáfora propia que no aportaba orientación y añadía animación autónoma. |
| Capa Material TV con tabs pill y escala 1,1x | La convención de componentes no era la identidad; añadía movimiento y ruido. |
| Serif editorial (Fraunces) | El hero leía como una portada de noticias; la única voz display son los ClearLogo. |
| Atenuación de tarjetas no enfocadas | Coste de composición y pérdida de contexto en el carrusel. |
| Escalado de tarjetas | Temblor horizontal y coste en hardware de entrada. |
| Blur en tiempo real | 60 fps innegociables en dongles de gama baja. |
| Botones "Volver" en TV | El mando tiene BACK; un botón en pantalla es ruido. |
| Tarjetas grises con texto incrustado | El arte es el contenido; la caja lo encierra y lo apaga. |

## 11. Deuda declarada

1. **Tablet táctil.** El dock de 18 px y el foco de borde se pensaron para un mando. Falta una
   pasada de dedo, gestos y tamaños táctiles.
2. **Nombres heredados.** `TvPalette` conserva `TextSecondaryCool` o `FocusInk`, nombres que ya no
   describen sus valores actuales. Es deuda de nombres, no de color.
3. **Medición de frames.** La regla de cero blur está en el código y en las revisiones, pero no hay
   un benchmark automático en CI.
4. **Catálogos en el árbol.** El workflow nocturno genera `docs/catalogs`, pero la carpeta está
   excluida por `.gitignore` en este árbol. Un fork debe versionarla o la app caerá al fallback de
   TMDB para las regiones sin catálogo.

## 12. Cómo reproducir el estudio

```bash
# Capturas de la app en un dispositivo conectado
adb exec-out screencap -p > captura.png

# Servir el estudio en local
python3 -m http.server 8123 -d docs

# Abrir http://127.0.0.1:8123/
```

El estudio web usa la misma DM Sans que la app (`app/src/main/res/font/dm_sans.ttf`), los mismos
tokens de `Color.kt` y capturas reales. No hay fuentes externas ni peticiones a terceros: la página
se puede abrir sin conexión una vez cargada.

## 13. Créditos

Jellyfin, TMDB y Trakt son proyectos independientes. *This product uses the TMDB API but is not
endorsed or certified by TMDB.* DM Sans se distribuye bajo SIL OFL. El cartel de *Night of the
Living Dead* (1968) es de dominio público. El resto de pósters pertenece a sus titulares y se
muestra con fines de documentación.
