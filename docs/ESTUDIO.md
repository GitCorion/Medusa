# Estudio de Medusa TV

**Una interfaz a tres metros**

Versión documentada: 2.3.12 (`versionCode 1000159`) · 22 de noviembre de 2026 · Madrid

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
| Biblioteca de prueba | 15.325 títulos (12.978 películas, 2.347 series) |
| Capturas | Televisor de 1920 × 1080, app v2.3.12 |
| Licencia | GPL-3.0 |

## 2. Método

El estudio se hizo sobre cuatro fuentes, no sobre una maqueta:

1. **Código real.** Lectura de `ui/theme`, `OceanIdentity`, tarjetas, navegación, ajustes,
   reproductor y capas de datos. Los tokens que aparecen aquí son los que renderiza `Color.kt`,
   no una aproximación de marketing.
2. **Televisor real.** Capturas tomadas con `adb` de la app instalada en un Android TV a 1080p,
   con una biblioteca de prueba grande. Nada de composiciones en Figma.
3. **Registro de commits.** Los cinco commits del 22 de noviembre que cambiaron la identidad se
   citan con su hora y su intención. Un estudio de diseño debe incluir lo que se descartó.
4. **Auditoría de diseño.** La crítica interna del 22 de noviembre, con sus hallazgos de
   accesibilidad, contraste y semántica. La deuda declarada del final sale de ahí.

**Límites.** No hay pruebas con usuarios externos, ni medición automática de frame time, ni una
muestra de dispositivos amplia. Las conclusiones de rendimiento son de diseño y de observación en
un televisor, no un benchmark estadístico.

## 3. El problema: tres metros

La app se usa desde un sofá. Eso cambia cuatro cosas:

- **La vista.** A tres metros, un texto de 12 px ocupa menos de cuatro milímetros. La densidad de
  un dashboard no sirve; hace falta jerarquía.
- **La mano.** El mando tiene D-pad, OK y BACK. No hay hover, no hay scroll fino y no hay un
  puntero. El foco es el cursor y tiene que estar siempre visible.
- **La sala.** La pantalla compite con una lámpara, una ventana o el reflejo de una OLED. El
  contraste no es un adorno.
- **El hardware.** Un dongle de gama baja no perdona blur en tiempo real ni animaciones de
  composición por tarjeta. La fluidez es una restricción de producto.

De ahí sale la regla que gobierna el resto: **si un elemento no comunica foco, jerarquía o dato,
se elimina**.

## 4. Principios y tensiones

### 4.1. El arte manda, el chrome desaparece

Los pósteres y backdrops van a sangre. El título y el año viven fuera de la carátula, sobre el
vacío. No hay cajones grises con texto incrustado ni bordes de 1 px en reposo. Cuando el arte es
el contenido, la interfaz no compite con él.

### 4.2. Una sola luz

Exactamente un elemento lleva la corriente o el anillo de foco. En reposo no se simula foco. Esto
evita el ruido de las interfaces que iluminan todas las tarjetas a la vez y obliga a que la
posición del mando sea inequívoca.

### 4.3. La identidad está en la estructura

La medusa no es solo un logo en la esquina. Es una corriente que subraya lo activo, arcos
batimétricos estáticos que dan profundidad, un pulso de sonar al entrar y una paleta de agua
profunda. Si quitas el logo, la interfaz sigue siendo reconocible.

### 4.4. La tensión: 60 fps frente a feedback

La versión anterior atenuaba las tarjetas no enfocadas y escalaba la enfocada. Se veía bien en un
monitor y costaba composición en la tele; además, el escalado provocaba temblor horizontal en los
carruseles. Desde v2.3.12 se eliminó el spotlight y el escalado. El foco se comunica con un anillo
de 1,5 dp, `zIndex(10f)` y el título que sube de sedimento a pergamino. Es menos "vistoso" y más
estable. Esa es la decisión central del producto.

## 5. Del editorial al cinematográfico

El 22 de noviembre de 2026 la identidad cambió de rumbo en una tarde. Los commits son reales:

| Hora | Commit | Decisión |
|---|---|---|
| 21:27 | `8430b41` | **Identidad abisal.** El negro digital y el cian neón se sustituyen por tinta abisal, corriente turquesa y violeta de medusa. Los neutros pasan de gris frío a pergamino y sedimento. |
| 21:27 | `2d6958f` | **Navegación tipográfica.** Las píldoras rellenas se convierten en pestañas con una corriente bajo la activa. El foco deja de ser un bloque. |
| 21:28 | `f47e297` | **Hero batimétrico.** Arcos estáticos, anillo de contacto y un solo pulso de sonar. Profundidad sin blur. |
| 22:05 | `43ab861` | **Legibilidad en la tele real.** Ajustes después de mirar la interfaz a tres metros, no en el monitor. |
| 22:11 | `886ec79` | **De editorial a cinematográfico.** Se retira Fraunces. Space Grotesk queda como única voz y el hero deja de parecer una portada de periódico. |

La lección no es "las serif son malas". Es que una serif editorial, con reglas largas y mucho
contraste, hacía que el hero leyera como una noticia y no como una película. La única voz display
variable son los ClearLogo del contenido; el resto es instrumento.

## 6. El sistema

### 6.1. Color

La paleta abandona el negro plano + cian neón de plantilla. El fondo es tinta abisal con sesgo
verde-azul; la luz principal es bioluminiscencia turquesa; el violeta de medusa es la segunda
firma; el ámbar queda reservado a las valoraciones y el coral a errores y acciones destructivas.

| Token | Hex | Uso | Contraste sobre abismo |
|---|---|---|---|
| Tinta abisal | `#050A0E` | Fondo raíz y tinta sobre acentos | — |
| Agua profunda | `#0C1C22` | Superficies base y teclado | 1,1:1 (superficie) |
| Agua elevada | `#10232A` | Controles y superficies elevadas | 1,2:1 (superficie) |
| Corriente | `#64E0D3` | Foco, progreso, selección | 12,5:1 |
| Violeta medusa | `#B9A7E3` | Sincronía y favoritos | 9,2:1 |
| Ámbar halógeno | `#F2B441` | Solo valoraciones | 10,8:1 |
| Coral | `#E8836B` | Errores y acciones destructivas | 7,5:1 |
| Pergamino | `#EDE8DC` | Texto principal | 16,3:1 |
| Sedimento | `#8C9A96` | Metadatos y texto secundario | 6,8:1 |
| Grafito húmedo | `#6E7C78` | Micro-datos | 4,6:1 |

Sobre pergamino, el texto siempre es tinta abisal. La corriente no se usa como color de texto
sobre pergamino: su contraste ahí es 1,3:1. Se usa como línea, relleno con tinta oscura o acento de
foco.

### 6.2. Tipografía

Una sola familia: **Space Grotesk**, variable y OFL. Cubre titulares, secciones, UI, metadatos y
cuerpo. La escala:

| Rol | Tamaño | Peso |
|---|---|---|
| Hero / ficha | 34–38 sp | 700 |
| Sección | 21 sp | 700 |
| Tarjeta | 12 sp | 600 |
| Cuerpo | 13–15 sp | 400 |
| Etiqueta | 11–12 sp | 500–600 |

Prohibido en la app: serif editorial, reglas de columna que cruzan la pantalla, mayúsculas
decorativas, tracking amplio en etiquetas, separadores con punto medio, flechas pegadas al texto y
monocromo para datos por defecto. El texto se escribe en caja de frase.

### 6.3. Forma y foco

| Elemento | Radio |
|---|---|
| Pestaña | 0 |
| Insignia | 4 px |
| Tarjeta | 10 px |
| Diálogo | 16 px |

Anatomía del foco:

1. Anillo de **1,5 dp** en corriente, solo en el elemento activo.
2. Título de sedimento a pergamino.
3. Corriente de 1,5 dp bajo el título.
4. `zIndex(10f)`, sin escalar la tarjeta ni atenuar a sus vecinas.

### 6.4. Movimiento

- Un **pulso de sonar** al entrar en Inicio. One-shot, 1,8 s, nunca en bucle.
- La corriente crece en respuesta al foco o a la acción del mando. No hay animaciones autónomas.
- `rememberReducedMotion()` lee `ANIMATOR_DURATION_SCALE`; si es 0, la interfaz se queda quieta.
- Cero fade-slide por sección, cero escalado de tarjeta, cero blur en tiempo real.

## 7. Las pantallas

### Inicio

El hero ocupa la primera pantalla. La navegación flota sin competir con el arte. Debajo, las filas
dejan que el póster mande y el texto viva fuera. La fila de plataformas usa placas discretas, no
tarjetas idénticas. **Lo que funciona:** jerarquía inmediata y una sola acción primaria. **Lo que
vigilar:** el carrusel del hero y el equilibrio entre el arte y los metadatos cuando el título es
muy largo.

### Descubrir

Filtro de formato, rail de temáticas y resultados en la misma pantalla. La corriente marca el
filtro activo; no hay chips rellenos. El contador y los atajos de mando están a la derecha.
**Lo que funciona:** exploración sin cambiar de contexto. **Lo que vigilar:** la carga cognitiva de
tener filtro, rail y resultados a la vez; el estado vacío tiene que explicar qué hacer.

### Ficha

Póster a la izquierda, metadatos editoriales a la derecha. La sinopsis respira sobre el fondo; no
hay panel que la encierre. Los estados de progreso y favorito son visibles sin ocupar la acción
principal. **Lo que funciona:** la ficha se lee como una página de revista de cine, no como un
formulario. **Lo que vigilar:** el contraste de los metadatos pequeños sobre backdrops luminosos.

### Buscar

Teclado a la izquierda, resultados y sugerencias a la derecha. Escribir no cambia de pantalla.
**Lo que funciona:** el foco del teclado y el de los resultados están separados por zonas claras.
**Lo que vigilar:** la precisión del D-pad en la rejilla de letras y el orden de las sugerencias.

### Favoritos

Filtros de formato y orden sobre la colección, no en un menú aparte. La portada sigue siendo la
protagonista. **Lo que funciona:** una colección personal se siente como una estantería, no como
una tabla. **Lo que vigilar:** los corazones y las notas sobre el póster no deben tapar el arte.

### Ajustes

Barra lateral plana, grupos tipográficos y cero tarjetas contenedoras. La corriente vertical marca
el ítem activo. **Lo que funciona:** el estado del servidor y la sincronización se leen de un
vistazo. **Lo que vigilar:** la jerga técnica ("scrobble", "passthrough") y la longitud de la
columna en pantallas pequeñas.

### Marcas y especiales

Los hubs de plataforma y los eventos estacionales reutilizan la gramática: arte a sangre, títulos
fuera, corriente en el filtro. **Lo que funciona:** una marca no necesita un tema propio para
sentirse parte del producto. **Lo que vigilar:** que el color de la marca no compita con la
corriente de foco.

## 8. Accesibilidad

Lo que ya está:

- Contraste AA en los textos principales: pergamino 16,3:1, corriente 12,5:1, sedimento 6,8:1,
  grafito 4,6:1.
- Foco visible en todos los elementos interactivos; no se depende del color solamente.
- Movimiento reducido respetado desde el sistema.
- Teclas físicas y `BACK` en TV; botón táctil de 38 dp en tablet.
- Subtítulos con tamaño y fondo configurables.
- Textos en español e inglés.

Lo que falta o hay que vigilar:

- La capa táctil de tablet merece una pasada propia: el modo solo iconos se pensó para un mando,
  no para un dedo.
- La semántica de selección en algunos rails puede mejorar (`Role.Checkbox`/`selected`) para
  lectores de pantalla.
- Los metadatos más pequeños sobre backdrops claros necesitan una comprobación caso por caso.
- No hay pruebas automáticas de contraste ni de frames en CI.

## 9. La ingeniería que se ve

La fluidez no sale del renderizado: sale de decidir qué no se dibuja.

- **Cero blur.** `Modifier.blur()` y `RenderEffect` están prohibidos. La profundidad se dibuja con
  `Canvas` y `Path` estáticos.
- **Caché en cascada.** Inicio intenta memoria, snapshot en fichero, Room y generación en vivo, en
  ese orden. El feed tiene TTL de 15 minutos y se emite antes de completar la generación.
- **Reproducción con fallback.** `.strm` resuelto en el servidor, remux, DirectStream, transcode y
  transcodificación completa. La lista de candidatos nunca queda vacía.
- **DeviceProfile serializado con `encodeDefaults`.** Sin eso, Jellyfin sustituye el perfil por
  `Type: Audio`, listas vacías y códecs vacíos. Hay una prueba golden.
- **Trabajo de fondo acotado.** Sincronización, indexado y enriquecimiento en workers separados,
  con concurrencia limitada y pausa cuando la app está visible o reproduciendo.
- **OTA verificada.** GitHub como único origen, 150 MB máximo, SHA-256 obligatorio, manifiesto con
  caducidad de siete días y kill-switch.

## 10. Lo que se descartó

| Se descartó | Motivo |
|---|---|
| Serif editorial (Fraunces) | El hero leía como una portada de noticias; la única voz display son los ClearLogo. |
| Píldoras rellenas | Convertían el foco en un bloque y competían con el arte. La corriente expresa selección sin rellenar. |
| Cian neón sobre negro puro | Plantilla de dark mode; la tinta abisal y el turquesa bioluminiscente tienen más carácter y mejor descanso visual. |
| Atenuación de tarjetas no enfocadas | Coste de composición y pérdida de contexto en el carrusel. |
| Escalado de la tarjeta enfocada | Temblor horizontal y coste en hardware de entrada. |
| Blur en tiempo real | 60 fps innegociables en dongles de gama baja. |
| Botones "Volver" en TV | El mando tiene BACK; un botón en pantalla es ruido. |
| Tarjetas grises con texto incrustado | El arte es el contenido; la caja lo encierra y lo apaga. |

## 11. Deuda declarada

1. **Tablet táctil.** El modo solo iconos y los objetivos de la barra superior se pensaron para un
   mando. Falta una pasada de dedo, gestos y tamaños táctiles.
2. **Nombres heredados.** `TvPalette` conserva `TextSecondaryCool` o `FocusInk`, nombres que ya no
   describen sus valores cálidos. Es deuda de nombres, no de color.
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

El estudio web usa el mismo TTF variable que la app (`app/src/main/res/font/space_grotesk_variable.ttf`),
los mismos tokens de `Color.kt` y capturas reales. No hay fuentes externas ni peticiones a
terceros: la página se puede abrir sin conexión una vez cargada.

## 13. Créditos

Jellyfin, TMDB y Trakt son proyectos independientes. *This product uses the TMDB API but is not
endorsed or certified by TMDB.* Space Grotesk se distribuye bajo SIL OFL. El cartel de *Night of
the Living Dead* (1968) es de dominio público. El resto de pósters pertenece a sus titulares y se
muestra con fines de documentación.
