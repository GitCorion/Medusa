---
name: "Medusa TV"
description: "Interfaz cinematográfica de Medusa TV para Android TV: DM Sans, cian bioluminiscente del logotipo oficial, arte full-bleed, cero real-time blur y foco de alto contraste para 10-foot UI."
colors:
  void-black: "#04060a"
  surface-dark: "#0a0f1d"
  surface-elevated: "#10162a"
  glass-idle: "#18ffffff"
  glass-selected: "#28ffffff"
  scrim-black: "#eb04060a"
  focus-cyan: "#00d2ff"
  focus-dark: "#04060a"
  abyssal-cobalt: "#0b5eff"
  glacial-aqua: "#8bffff"
  amber-cinema: "#e5a44d"
  gold-watched: "#d4af37"
  text-primary: "#f7f5f0"
  text-primary: "#ffffff"
  text-secondary: "#a1a1aa"
  text-tertiary: "#71717a"
typography:
  display:
    fontFamily: "DM Sans, sans-serif"
    fontSize: "36px"
    fontWeight: 700
    lineHeight: 1.15
  headline:
    fontFamily: "DM Sans, sans-serif"
    fontSize: "24px"
    fontWeight: 700
    lineHeight: 1.2
  title:
    fontFamily: "DM Sans, sans-serif"
    fontSize: "16px"
    fontWeight: 600
    lineHeight: 1.3
  body:
    fontFamily: "DM Sans, sans-serif"
    fontSize: "13px"
    fontWeight: 400
    lineHeight: 1.5
  label:
    fontFamily: "DM Sans, sans-serif"
    fontSize: "11px"
    fontWeight: 600
    letterSpacing: "0.04em"
rounded:
  badge: "4px"
  chip: "10px"
  card: "10px"
  circle: "9999px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "16px"
  lg: "24px"
  gutter: "48px"
components:
  topbar-icon-button:
    backgroundColor: "transparent"
    textColor: "{colors.text-secondary}"
    rounded: "18px"
    iconSize: "18px"
  topbar-icon-button-focus:
    backgroundColor: "{colors.focus-cyan}"
    textColor: "{colors.focus-dark}"
    rounded: "18px"
    iconSize: "18px"
  format-filter-icon:
    backgroundColor: "{colors.glass-idle}"
    textColor: "{colors.text-secondary}"
    rounded: "{rounded.chip}"
    padding: "6px 12px"
  format-filter-icon-selected:
    backgroundColor: "{colors.glass-selected}"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.chip}"
    padding: "6px 12px"
  media-card-poster:
    backgroundColor: "{colors.surface-elevated}"
    rounded: "{rounded.card}"
    width: "150px"
    height: "225px"
  media-card-cw:
    backgroundColor: "{colors.surface-elevated}"
    rounded: "{rounded.card}"
    width: "280px"
    height: "158px"
---

# Sistema de Diseño Real: Medusa TV

## Overview

**Enfoque Visual: "Cinematic TV Leanback"**

Medusa TV es una interfaz de streaming 10-foot real para Android TV y pantallas OLED. El diseño no se basa en marcos complejos, cajas grises ni metáforas literarias, sino en **lo que realmente se renderiza en pantalla**: carátulas cinematográficas a sangre, navegación por iconos compactos, tipografía neutra de alta legibilidad (DM Sans), foco bioluminiscente inequívoco y cero elementos superfluos.

Toda la aplicación vive sobre un fondo negro cinematográfico puro (`#04060A`). El foco activo es inequívoco: exactamente un elemento en pantalla se ilumina en `Cian Medusa` (`#00D2FF`) con iconos/texto oscuros `#04060A` para lectura perfecta a 3 metros. Desde v2.3.12 las tarjetas inactivas **mantienen su opacidad y luminosidad al 100%** (se eliminó el spotlight por coste de GPU); el foco se señala mediante `zIndex(10f)`, un borde de acento de `1.5.dp` sobre el póster y el texto del título que pasa de `0.75f` a `1.0f`.

**Pilares reales de la interfaz:**
- **Tipografía con Voz Cinematográfica:** `DM Sans` en toda la interfaz (displays, títulos, cuerpo y etiquetas), con titulares en Bold y tracking neutro. `DM Serif Display` se retiró de la UI por legibilidad a 3 m.
- **Barra Superior Minimalista:** Logotipo de Medusa a la izquierda y 5 iconos compactos de 18 dp en cápsulas de 18 dp de radio a la derecha (Inicio, Brújula/Descubrir, Corazón/Favoritos, Lupa/Buscar, Ajustes). Sin etiquetas de texto ruidosas.
- **Filtros por Iconos:** Selector de formato compacto con 4 iconos: Ojo (Todos), Cuadrícula (Catálogo), Claqueta (Películas) y Pantalla TV (Series).
- **Carrusel de Tags (Descubrir):** Pantalla de exploración directa con chips temáticos (Aventura, Animación, Ciencia Ficción...) y carrusel inferior de resultados en tiempo real.
- **Carátulas a Sangre:** Los pósteres verticales (2:3) y panorámicas (16:9) no tienen cajas grises pegadas debajo. El título y año se muestran sobre el fondo negro exterior con autoscroll marquee al recibir foco.
- **Navegación 100% con Mando:** Sin botones de "Atrás" ocupando espacio en la tele. El retroceso lo gestiona la tecla física `BACK` del mando de TV.

## Colors

La paleta real extraída directamente del código fuente y los temas de la app:

### Foco y Acento Principal
- **Focus Cyan** (`#00D2FF`): El color del mando. Cian bioluminiscente idéntico al isotipo oficial de Medusa. Borde de `1.5.dp` sobre el póster de la tarjeta activa, relleno sólido en iconos de navegación seleccionados y barra de progreso.
- **Focus Dark Content** (`#04060A`): Tinta negra profunda utilizada dentro de botones e iconos con fondo activo para garantizar máximo contraste (15.8:1).
- **Abyssal Cobalt** (`#0B5EFF`): Azul cobalto oceánico de los tentáculos del isotipo, utilizado en gradientes espaciales y brillos secundarios.
- **Glacial Aqua** (`#8BFFFF`): Reflejos y corona luminosa de la medusa para detalles técnicos.

### Secundarios y Calificaciones
- **Amber Cinema** (`#E5A44D`): Calidez dorada halógena de 3200K, utilizada para insignias numéricas y favoritos.
- **Gold Nitrate** (`#D4AF37`): Sello dorado de época para contenido visto/reproducido.
- **Accent Alternativos (Ajustes):** La app permite cambiar el acento cian por *Ámbar Cine* (`#E5A44D`) o *Blanco Puro* (`#FFFFFF`) / *Monocromático*.

### Superficies y Neutros
- **Void Black** (`#06080D`): Fondo raíz de toda la app. Negro profundo para paneles OLED.
- **Surface Dark** (`#0C1017`): Fondo del teclado en pantalla y superficies elevadas secundarias (`TvPalette.SurfaceCard`).
- **Surface Elevated** (`#18171E`): Fondo base de carga para carátulas y pósteres.
- **Glass Idle** (`#18FFFFFF`): Cristal translúcido para botones y chips en reposo. Cero bordes grises de 1px.
- **Glass Selected** (`#28FFFFFF`): Estado activo para chips de formato y pestañas seleccionadas.
- **Text Primary** (`#F7F5F0`): Blanco marfil cálido para lectura descansada a 3 metros.
- **Text Secondary** (`#A39E93`): Gris pergamino cinematográfico para metadatos (año, género, temporadas).
- **Text Tertiary** (`#827D73`): Grafito atenuado para micro-metadatos (4.9:1 sobre abismo, WCAG AA).

## Typography

Tipografía embebida (DM Sans) renderizada con contraste nítido para 1080p y 4K:

- **Display Hero (30sp - 36sp, DM Sans Bold):** Título de película/serie en el Hero Banner cuando no hay logotipo transparente ClearLogo.
- **Ranked Display (tamaño adaptativo, DM Sans Bold):** Cifras sólidas para el Top 10 de hoy.
- **Section Headline (19sp, DM Sans Bold):** Títulos de carrusel (`Continuar viendo`, `Top 10 hoy`, `Favoritos`).
- **Card Title (12sp - 14sp, DM Sans SemiBold):** Título de película debajo del póster. En reposo marfil tenue; al enfocar blanco puro con autoscroll marquee.
- **Film Codec Badge (11sp, DM Sans Bold):** Especificaciones técnicas y códigos de episodio (`T2:E4`, `4K HDR`). Calibrado a 11sp para lectura descansada a 3m.
- **Badge / Pill Label (10sp - 11sp, SemiBold):** Píldoras de formato, año (`2026`), tipo (`SERIES`), código de episodio (`T1:E4`) y calificación por edad (`TV-MA`, `ES-16`).
- **Etiquetas y metadatos:** las etiquetas de sección, cabeceras de Ajustes y chips de género van en caja oracional, sin tracking artificial. Los metadatos se separan por espacio estructural (`2026   PELÍCULA`), nunca con puntos medios.

## Layout y Estructura de Pantallas

### 1. Barra Superior Universal (`TvTopBar`)
- **Izquierda:** Logotipo "MEDUSA" con medusa bioluminiscente (`52.dp` altura).
- **Centro:** Indicador circular discreto de sincronización si hay datos actualizándose en segundo plano.
- **Derecha:** 5 botones en cápsula (radio `18.dp`, icono `18.dp`):
  1. `Inicio` (Icono Casa)
  2. `Descubrir` (Icono Brújula)
  3. `Favoritos` (Icono Corazón)
  4. `Buscar` (Icono Lupa)
  5. `Ajustes` (Icono Deslizadores / Tune, con punto verde si hay actualización disponible)
- **Comportamiento en Home:** Flota sobre el Hero Banner con un gradiente superior negro y se oculta suavemente al hacer scroll hacia abajo para no colisionar con las filas de plataformas.

### 2. Pantalla Principal (`HomeScreen`)
- **Hero Billboard:** Banner panorámico 16:9 con backdrop, logotipo ClearLogo, sinopsis y botones de acción (`Reproducir`, `Info`).
- **Regla de material del hero:** solo entran títulos con **backdrop 16:9 real y sinopsis real** (no el texto de relleno de enriquecimiento). Sin ambos, el título vive en las filas del catálogo: la portada nunca recorta un póster 2:3 ni inventa un fondo. Los ClearLogo tienen prioridad.
- **Fila Favorite Genres:** Chips horizontales compactos (`Comedia`, `Familia`, `Suspense`, `Drama`, `Terror`...).
- **Fila Plataformas:** Logos de servicios de streaming (`Netflix`, `Prime Video`, `Disney+`, `Apple TV+`, `Max`, `Crunchyroll`).
- **Carruseles de Contenido:**
  - `Continuar viendo`: Tarjetas panorámicas 16:9 (`280.dp x 158.dp`) con barra de progreso cian flotante y tag de episodio (`T2:E4`).
  - `Top 10 Hoy`: Tarjetas verticales con números gigantes tipográficos (1 al 10) con gradiente cian detrás del póster.
  - Filas dinámicas y por género con pósteres verticales 2:3.

### 3. Pantalla Descubrir (`MedusaScreen`)
- **Filtro de formato superior:** 4 botones de icono: `[Ojo: Todos]` `[Cuadrícula: Catálogo]` `[Claqueta: Películas]` `[TV: Series]`.
- **Carrusel horizontal de Tags:** Chips temáticos (`Aventura`, `Ciencia Ficción`, `Animación`, `Fantasía`, `Drama`, `Comedia`...).
- **Línea de estado:** Contador de títulos encontrados y atajo del mando (`[DOWN] Browse movies · [BACK] Undo`).
- **Resultados en tiempo real:** Carrusel horizontal de pósteres que se actualiza al instante según el tag activo.

### 4. Ficha de Detalle (`DetailScreen`)
- **Layout Cinemático Unificado:** Pantalla completa sin cortes verticales. El fondo proyecta el backdrop del título con gradiente negro.
- **Cabecera Hero:** Póster 2:3 a la izquierda (`210.dp x 315.dp`), a la derecha logotipo ClearLogo, píldoras de metadatos (`★ 7.1`, año, género, estado), fila de botones (`Reproducir`, `Favoritos`, `Trailer`, `Visto`) y sinopsis editorial.
- **Carruseles Inferiores:** Filas de episodios (por temporadas), títulos de la misma saga/colección y recomendaciones que ocupan el 100% del ancho de pantalla.
- **Navegación:** Sin botón de atrás visible en TV (retroceso con tecla `BACK` del mando). En tablet, botón circular de 38dp transparente en la esquina superior izquierda.

### 5. Pantalla Buscar (`SearchScreen`)
- **Teclado integrado:** rejilla D-Pad con tecla enfocada en cápsula cian. El modo por defecto es **Libre** (todas las letras siempre visibles).
- **Modo Auto (opt-in):** atenúa las letras que no pueden continuar ningún título del catálogo. Se activa manualmente desde el selector del campo y **se recuerda entre sesiones** (DataStore `search_keyboard_auto`). Las letras atenuadas siguen siendo legibles y no reciben foco.

## Shapes y Elevación

- **Insignias y Pills:** `4.dp` de radio. Fondo semitransparente oscuro con texto blanco/ámbar.
- **Botones y Filtros:** `10.dp` de radio. Fondo cristal `0x18FFFFFF` en reposo; cian sólido en foco.
- **Pósteres y Carátulas:** `10.dp` de radio en carriles y `12.dp` en la ficha. Arte 100% a sangre sin marcos.
- **Colecciones:** `14.dp` con efecto visual de doble solapa superior que indica que es una saga múltiple.
- **Foco Z-Index:** Al hacer foco, la tarjeta asigna `zIndex(10f)` y muestra un borde de acento de `1.5.dp` sobre el póster. **No hay escalado (`focusedScale = 1.0f`) ni atenuación de hermanos**: la decisión v2.3.12 priorizó 60 FPS estables y evitar temblores horizontales en carruseles. El título dentro de la tarjeta sí pasa de `alpha 0.75f` a `1.0f`.
- **Cero Blur en tiempo real:** Prohibido usar `Modifier.blur()` o `RenderEffect` para garantizar 60 FPS estables en Android TV.

## Do's y Don'ts

### Do:
- **Do** usar la tecla física `BACK` del mando de TV para cualquier acción de retroceso.
- **Do** mantener la barra superior como una fila de iconos compactos en cápsulas en la esquina derecha.
- **Do** usar foco de relleno sólido (`focusedContainerColor = focusColor`, `focusedContentColor = focusContent`, `border = Border.None`) para todos los chips, filtros, botones y pestañas: cápsula de color sólido (Cian Medusa) sin marcos blancos externos.
- **Do** colocar el título y metadatos de las tarjetas fuera de la imagen del póster sobre el fondo negro.
- **Do** mantener las tarjetas no enfocadas a opacidad completa y apoyar el foco en `zIndex(10f)` + borde de acento de `1.5.dp` sobre la carátula.
- **Do** aplicar autoscroll marquee en títulos largos solo cuando la tarjeta tiene el foco del D-Pad.

### Don't:
- **Don't** añadir bordes blancos alrededor de chips, pestañas de navegación o botones al recibir foco (cero marcos blancos; el foco es la cápsula cian sólida con tinta oscura, igual que en Ajustes).
- **Don't** poner botones de "Volver" o "Back" en pantalla cuando se ejecuta en Android TV.
- **Don't** añadir bordes grises de 1px a tarjetas o botones en reposo (*cero border slop*).
- **Don't** meter los pósteres dentro de cajones rectangulares grises con textos incrustados.
- **Don't** usar efectos de desenfoque (`blur`) pesados que bajen los FPS en el hardware de la televisión.
- **Don't** reintroducir animaciones de `alpha` por tarjeta (spotlight `0.65f`) ni escalados en los carruseles: se eliminaron en v2.3.12 por coste de composición y temblor horizontal.
- **Don't** usar textos largos o etiquetas descriptivas en los iconos del menú superior en TV.
