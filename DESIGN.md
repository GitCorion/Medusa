---
name: "Medusa TV"
description: "Interfaz real de Medusa TV para Android TV: navegación por iconos, arte full-bleed, carrusel de tags y foco de alto contraste cian."
colors:
  void-black: "#06080d"
  surface-dark: "#0c1017"
  surface-elevated: "#141b26"
  glass-idle: "#16ffffff"
  glass-selected: "#28ffffff"
  scrim-black: "#cc06080d"
  focus-cyan: "#00f2fe"
  focus-dark: "#06080d"
  amber-rating: "#f59e0b"
  text-primary: "#f8fafc"
  text-secondary: "#94a3b8"
  text-tertiary: "#64748b"
typography:
  display:
    fontFamily: "Inter, Roboto, sans-serif"
    fontSize: "36px"
    fontWeight: 700
    lineHeight: 1.15
  headline:
    fontFamily: "Inter, Roboto, sans-serif"
    fontSize: "24px"
    fontWeight: 700
    lineHeight: 1.2
  title:
    fontFamily: "Inter, Roboto, sans-serif"
    fontSize: "14px"
    fontWeight: 600
    lineHeight: 1.3
  body:
    fontFamily: "Inter, Roboto, sans-serif"
    fontSize: "13px"
    fontWeight: 400
    lineHeight: 1.5
  label:
    fontFamily: "Inter, Roboto, sans-serif"
    fontSize: "11px"
    fontWeight: 600
    letterSpacing: "0.05em"
rounded:
  badge: "4px"
  chip: "10px"
  card: "12px"
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
    rounded: "{rounded.circle}"
    size: "38px"
  topbar-icon-button-focus:
    backgroundColor: "{colors.focus-cyan}"
    textColor: "{colors.focus-dark}"
    rounded: "{rounded.circle}"
    size: "38px"
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

Medusa TV es una interfaz de streaming 10-foot real para Android TV y pantallas OLED. El diseño no se basa en marcos complejos, cajas grises ni metáforas literarias, sino en **lo que realmente se renderiza en pantalla**: carátulas cinematográficas a sangre, navegación por iconos compactos, foco de alto contraste instantáneo y cero elementos superfluos.

Toda la aplicación vive sobre un fondo negro cinematográfico puro (`#06080D`). El foco activo es inequívoco: exactamente un elemento en pantalla se ilumina en `Cyan` (`#00F2FE`) con iconos/texto oscuros `#06080D` para lectura perfecta a 3 metros, mientras que las tarjetas inactivas de la fila en foco se atenúan al `65%` de opacidad.

**Pilares reales de la interfaz:**
- **Barra Superior Minimalista:** Logotipo de Medusa a la izquierda y 5 iconos circulares a la derecha (Inicio, Brújula/Descubrir, Corazón/Favoritos, Lupa/Buscar, Ajustes). Sin etiquetas de texto ruidosas.
- **Filtros por Iconos:** Selector de formato compacto con 4 iconos: Ojo (Todos), Cuadrícula (Catálogo), Claqueta (Películas) y Pantalla TV (Series).
- **Carrusel de Tags (Descubrir):** Pantalla de exploración directa con chips temáticos (Aventura, Animación, Ciencia Ficción...) y carrusel inferior de resultados en tiempo real.
- **Carátulas a Sangre:** Los pósteres verticales (2:3) y panorámicas (16:9) no tienen cajas grises pegadas debajo. El título y año se muestran sobre el fondo negro exterior con autoscroll marquee al recibir foco.
- **Navegación 100% con Mando:** Sin botones de "Atrás" ocupando espacio en la tele. El retroceso lo gestiona la tecla física `BACK` del mando de TV.

## Colors

La paleta real extraída directamente del código fuente y los temas de la app:

### Foco y Acento Principal
- **Focus Cyan** (`#00F2FE`): El color del mando. Borde exterior de 2dp en tarjetas activas, relleno sólido en iconos de navegación seleccionados y barra de progreso de reproducción.
- **Focus Dark Content** (`#06080D`): Tinta negra profunda utilizada dentro de botones e iconos con fondo cian para garantizar máximo contraste (15.8:1).

### Secundarios y Calificaciones
- **Amber Rating** (`#F59E0B`): Estrella e insignias numéricas de puntuación (ej. `★ 8.7`).
- **Accent Alternativos (Ajustes):** La app permite cambiar el acento cian por *Ámbar Cine* (`#F59E0B`) o *Blanco Puro* (`#FFFFFF`) / *Monocromático*.

### Superficies y Neutros
- **Void Black** (`#06080D`): Fondo raíz de toda la app. Negro profundo para paneles OLED.
- **Surface Dark** (`#0C1017`): Fondo del teclado en pantalla y superficies elevadas secundarias.
- **Surface Elevated** (`#141B26`): Fondo base de carga para carátulas y pósteres.
- **Glass Idle** (`#16FFFFFF` / `Color(0x18FFFFFF)`): Cristal translúcido para botones y chips en reposo. Cero bordes grises de 1px.
- **Glass Selected** (`#28FFFFFF`): Estado activo para chips de formato y pestañas seleccionadas.
- **Text Primary** (`#F8FAFC`): Blanco limpio para títulos y etiquetas principales.
- **Text Secondary** (`#94A3B8`): Gris pizarra para metadatos (año, género, temporadas) e iconos en reposo.
- **Text Tertiary** (`#64748B`): Gris atenuado para etiquetas secundarias e indicadores.

## Typography

Tipografía nativa del sistema (Inter / Roboto) renderizada con contraste nítido para 1080p y 4K:

- **Display Hero (36sp, Bold):** Título de película/serie en el Hero Banner cuando no hay logotipo transparente ClearLogo. Marquee continuo si supera una línea.
- **Section Headline (20sp - 24sp, Bold):** Títulos de carrusel (`Continuar viendo`, `Top 10 Hoy`, `Favoritos`, `120 TITLES`).
- **Card Title (14sp, SemiBold):** Título de película debajo del póster. En reposo es tenue (`#94A3B8`); al enfocar se ilumina en blanco con autoscroll marquee.
- **Metadata Body (13sp - 14sp, Regular):** Sinopsis y descripción de contenido (máximo 5 líneas con elipsis).
- **Badge / Pill Label (10sp - 11sp, SemiBold):** Píldoras de formato, año (`2026`), tipo (`SERIES`), código de episodio (`T1:E4`) y calificación por edad (`TV-MA`, `ES-16`).

## Layout y Estructura de Pantallas

### 1. Barra Superior Universal (`TvTopBar`)
- **Izquierda:** Logotipo "MEDUSA" con medusa bioluminiscente (`52.dp` altura).
- **Centro:** Indicador circular discreto de sincronización si hay datos actualizándose en segundo plano.
- **Derecha:** 5 botones circulares (`CircleShape`, `38.dp`):
  1. `Inicio` (Icono Casa)
  2. `Descubrir` (Icono Brújula)
  3. `Favoritos` (Icono Corazón)
  4. `Buscar` (Icono Lupa)
  5. `Ajustes` (Icono Deslizadores / Tune, con punto verde si hay actualización disponible)
- **Comportamiento en Home:** Flota sobre el Hero Banner con un gradiente superior negro y se oculta suavemente al hacer scroll hacia abajo para no colisionar con las filas de plataformas.

### 2. Pantalla Principal (`HomeScreen`)
- **Hero Billboard:** Banner panorámico con póster grande, logotipo ClearLogo, metadatos y botones de acción (`Reproducir`, `Info`).
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

## Shapes y Elevación

- **Insignias y Pills:** `4.dp` de radio. Fondo semitransparente oscuro con texto blanco/ámbar.
- **Botones y Filtros:** `10.dp` de radio. Fondo cristal `0x18FFFFFF` en reposo; cian sólido en foco.
- **Pósteres y Carátulas:** `12.dp` de radio. Arte 100% a sangre sin marcos.
- **Colecciones:** `14.dp` con efecto visual de doble solapa superior que indica que es una saga múltiple.
- **Foco Z-Index:** Al hacer foco, la tarjeta escala a `1.05f`, asigna `zIndex(10f)` y muestra borde cian de `2.dp`. Las demás tarjetas de la fila bajan su opacidad a `0.65f`.
- **Cero Blur en tiempo real:** Prohibido usar `Modifier.blur()` o `RenderEffect` para garantizar 60 FPS estables en Android TV.

## Do's y Don'ts

### Do:
- **Do** usar la tecla física `BACK` del mando de TV para cualquier acción de retroceso.
- **Do** mantener la barra superior como una fila de iconos circulares limpios en la esquina derecha.
- **Do** colocar el título y metadatos de las tarjetas fuera de la imagen del póster sobre el fondo negro.
- **Do** atenuar las tarjetas no enfocadas de la fila activa (`alpha = 0.65f`) para resaltar el elemento enfocado.
- **Do** aplicar autoscroll marquee en títulos largos solo cuando la tarjeta tiene el foco del D-Pad.

### Don't:
- **Don't** poner botones de "Volver" o "Back" en pantalla cuando se ejecuta en Android TV.
- **Don't** añadir bordes grises de 1px a tarjetas o botones en reposo (*cero border slop*).
- **Don't** meter los pósteres dentro de cajones rectangulares grises con textos incrustados.
- **Don't** usar efectos de desenfoque (`blur`) pesados que bajen los FPS en el hardware de la televisión.
- **Don't** usar textos largos o etiquetas descriptivas en los iconos del menú superior en TV.
