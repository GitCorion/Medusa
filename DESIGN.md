---
name: "Medusa TV"
description: "Sistema visual real de Medusa TV para Android TV: DM Sans, cian de foco, arte a sangre, cero blur en tiempo real y foco de alto contraste para una interfaz de diez pies."
colors:
  void-black: "#04060a"
  abyss-surface: "#0a0f1d"
  abyss-surface-elevated: "#10162a"
  surface-card: "#080d1a"
  surface-chip: "#0d1424"
  surface-trigger: "#0f182b"
  glass-idle: "#18ffffff"
  glass-selected: "#28ffffff"
  scrim-black: "#eb04060a"
  focus-cyan: "#00d2ff"
  focus-ink: "#04060a"
  abyssal-cobalt: "#0b5eff"
  glacial-aqua: "#8bffff"
  amber-cinema: "#e5a44d"
  gold-nitrate: "#d4af37"
  rating-gold: "#facc15"
  text-primary: "#ffffff"
  text-secondary: "#a1a1aa"
  text-tertiary: "#71717a"
  error: "#ef4444"
typography:
  fontFamily: "DM Sans, sans-serif"
  hero-display: "32sp / 38sp, Bold"
  display-large: "36sp / 42sp, Bold"
  section-title: "20sp / 26sp, Bold"
  headline: "24sp / 30sp, Bold"
  title: "16sp / 22sp, SemiBold"
  body: "13sp / 19sp, Regular"
  label: "12sp / 16sp, SemiBold"
rounded:
  badge: "6–9dp"
  chip: "8dp"
  poster: "10dp"
  detail-poster: "12dp"
  capsule: "18dp"
  dialog: "24dp"
  modal: "29dp"
  circle: "9999dp"
spacing:
  gutter: "48dp"
  card-gap: "16dp"
  title-to-shelf: "24dp"
  shelf-bottom: "24dp"
components:
  topbar-icon:
    backgroundColor: "transparent"
    textColor: "{colors.text-secondary}"
    rounded: "18dp"
    iconSize: "18dp"
  topbar-icon-focus:
    backgroundColor: "{colors.focus-cyan}"
    textColor: "{colors.focus-ink}"
    rounded: "18dp"
    iconSize: "18dp"
  chip:
    height: "36dp"
    backgroundColor: "{colors.surface-chip}"
    rounded: "{rounded.chip}"
  chip-focus:
    backgroundColor: "{colors.focus-cyan}"
    textColor: "{colors.focus-ink}"
    border: "none"
  poster:
    backgroundColor: "{colors.surface-card}"
    rounded: "{rounded.poster}"
    focusBorder: "1.5dp"
    focusBorderColor: "{colors.focus-cyan}"
---

# Sistema de diseño real: Medusa TV

## 1. Principio

Medusa TV es una interfaz de salón para Jellyfin. Se usa a tres metros, con un mando de
cinco teclas y una pantalla OLED. El arte del contenido manda; el chrome desaparece. Cada
elemento que no comunica **foco, jerarquía o dato** se elimina.

La app se construye en Kotlin y Compose TV. Las pantallas son nativas: el único WebView es
el reproductor de tráilers de YouTube. La interfaz no usa `Modifier.blur()` ni
`RenderEffect`.

**Fuente de verdad:** `ui/theme/Color.kt`, `ui/theme/Type.kt`, `ui/theme/TvDesignSystem.kt`,
`ui/components/TvTopBar.kt`, `ui/components/MediaCard.kt` y `ui/screens/medusa/MedusaTagRail.kt`.
Este documento resume esos valores; si cambia un token, se cambia también aquí y en
`docs/ESTUDIO.md`.

## 2. Color

El fondo es tinta abisal (`#04060A`). El foco es el cian eléctrico del logotipo
(`#00D2FF`). El cobalto y el aqua aportan profundidad; el ámbar y el oro quedan para
favoritos, valoraciones y visto. Los neutros son zinc y grafito, no grises fríos de plantilla.

| Token | Hex | Uso | Contraste sobre abismo |
|---|---|---|---|
| Tinta abisal | `#04060A` | Fondo raíz y tinta sobre acentos | — |
| Agua profunda | `#0A0F1D` | Superficie base | 1,1:1 (superficie) |
| Agua elevada | `#10162A` | Controles y superficies elevadas | 1,1:1 (superficie) |
| Tarjeta | `#080D1A` | Fondo de carátulas | 1,1:1 (superficie) |
| Chip | `#0D1424` | Chips y filtros en reposo | 1,1:1 (superficie) |
| Cian Medusa | `#00D2FF` | Foco, selección, progreso | 11,3:1 |
| Tinta de foco | `#04060A` | Texto e iconos sobre cian | 11,3:1 |
| Cobalto abisal | `#0B5EFF` | Gradientes y brillos; no texto | 3,9:1 |
| Aqua glaciar | `#8BFFFF` | Reflejos y detalles | 17,2:1 |
| Ámbar cine | `#E5A44D` | Favoritos y valoraciones | 9,4:1 |
| Oro nitrato | `#D4AF37` | Visto y sello cinematográfico | 9,6:1 |
| Nota | `#FACC15` | Estrellas y notas | 13,2:1 |
| Blanco puro | `#FFFFFF` | Texto principal | 20,3:1 |
| Zinc | `#A1A1AA` | Metadatos y texto secundario | 7,9:1 |
| Grafito | `#71717A` | Micro-datos y separadores | 4,2:1 |

El cobalto no se usa como texto. El grafito se reserva a micro-datos y separadores. Sobre
superficie clara, el texto siempre es tinta abisal.

## 3. Tipografía

Una sola familia: **DM Sans**, variable y OFL. No hay serif decorativa en la UI. Los
ClearLogo del contenido son la única voz display variable.

| Rol | Tamaño / línea | Peso | Uso |
|---|---|---|---|
| Hero display | 32 / 38 sp | Bold | Título del hero sin ClearLogo |
| Display large | 36 / 42 sp | Bold | Cabeceras excepcionales |
| Section title | 20 / 26 sp | Bold | Títulos de fila y cabeceras |
| Headline | 24 / 30 sp | Bold | Título de ficha y estados |
| Title | 16 / 22 sp | SemiBold | Filas, listas y botones |
| Body | 13 / 19 sp | Regular | Sinopsis y textos largos |
| Label | 12 / 16 sp | SemiBold | Chips, píldoras y metadatos |
| Film codec | 11 / 14 sp | Bold | `T2:E4`, `4K HDR`, códecs |
| Rating score | 12 / 14 sp | Black | Nota numérica |
| Ranked display | 114 sp | Black | Números del Top 10 |

El texto se escribe en caja de frase. Prohibido: serif editorial, mayúsculas decorativas,
tracking amplio, separadores con punto medio y flechas pegadas al texto.

## 4. Forma, foco y elevación

| Elemento | Radio |
|---|---|
| Insignia | 6–9 dp según tamaño (`TvCornerRadius`) |
| Chip y filtro | 8 dp (`TvChipDefaults.Shape`) |
| Póster de carrusel | 10 dp |
| Póster de ficha | 12 dp |
| Cápsula del dock | 18 dp |
| Contenedor del dock | 22 dp |
| Diálogo | 24 dp |
| Modal | 29 dp |
| Círculo | 999 dp |

**Foco en tarjetas:** borde de acento de **1,5 dp** sobre el póster, `zIndex(10f)` y el
título de `0.75f` a plena luminosidad. Las tarjetas vecinas conservan su opacidad. No hay
escala de tarjeta ni spotlight.

**Foco en chips y botones:** relleno sólido con el acento y texto/icono en tinta de foco.
El reposo no tiene borde gris. `TvChipDefaults.border()` devuelve `Border.None`; no se usan
marcos blancos.

**Dock:** cinco cápsulas de 18 dp de radio e iconos de 18 dp. El foco no exige OK. La barra
admite solo iconos, solo texto o ambos.

**Botón táctil:** `TvBackButton` mide 36 dp de alto y usa el radio de chip (8 dp). Solo se
muestra en tablet, en Marca, Género y Especiales. En TV el retroceso es la tecla física
`BACK`.

## 5. Layout

- Gutter lateral: **48 dp**.
- Separación entre tarjetas de un carrusel: **16 dp**.
- Título de fila a primer ítem: **24 dp**.
- Aire inferior entre filas: **24 dp**.
- Hero de Inicio: **430 dp** de alto, backdrop 16:9 a sangre, scrim y una sola acción
  primaria.
- Pósters de carrusel: 2:3. Continuar viendo y colecciones: 16:9.
- El título y los metadatos de una tarjeta viven fuera del póster, sobre el fondo.
- En tablet, `WindowSizeClass` adapta anchos y añade navegación táctil. En TV no hay
  botones de volver en pantalla.

## 6. Las pantallas

### Inicio
Hero a sangre con ClearLogo o DM Sans bold, metadatos, una acción primaria y paginación.
Debajo: géneros favoritos, plataformas, Continuar viendo, Top 10 y filas de biblioteca,
sagas, Trakt y recomendaciones. El hero rota cada 12 s cuando no tiene foco; al enfocar sus
acciones, la rotación se detiene.

### Descubrir
Cuatro filtros de formato: **Todos** (ojo), **Catálogo** (cuadrícula), **Películas**
(claqueta) y **Series** (TV). Debajo, un **rail horizontal de temáticas** generado por
co-ocurrencia real del catálogo. Cada etiqueta seleccionada se suma como filtro **AND** y
aparece como píldora activa. `BACK` deshace la última; pulsar una etiqueta ya activa
desconecta esa y las posteriores. Los resultados se leen en un carrusel inferior con
contador, título/año/nota del enfocado y los atajos `[ABAJO] Navegar películas` y
`[BACK] Deshacer`.

No es una constelación, una nebulosa ni un muro mosaico: es un rail de temáticas.

### Favoritos
Filtros de formato y orden sobre la colección, con el recuento visible. Pósters con
insignias de visto y favorito. El estado vacío explica cómo añadir títulos.

### Buscar
Teclado de diez pies a la izquierda; sugerencias y resultados a la derecha. Escribir no
cambia de pantalla. El micrófono y el modo **Teclado auto** son accesibles desde las teclas.
El modo auto atenúa las letras que no pueden continuar ningún título y se recuerda entre
sesiones.

### Ficha
Backdrop a sangre, póster 2:3 a la izquierda y metadatos editoriales a la derecha. Acciones
de reproducir, favorito, tráiler y visto; sinopsis sin panel contenedor; temporadas y
episodios; títulos de la misma saga y recomendaciones.

### Reproductor
Media3 + FFmpeg. HUD de scrubber, selector de audio/subtítulos, siguiente episodio,
salto de intro/recap/créditos con Media Segments, buffer de preparación TOD y frecuencia de
refresco automática opcional.

### Ajustes
Barra lateral con ocho grupos: Servidor Jellyfin, Plataformas y Cadenas, Biblioteca y
Trakt, Reproducción, Audio y Sonido, Subtítulos, Interfaz y Pantalla y Acerca de. El canal
OTA Estable/Beta vive en Acerca de.

### Marca, Género y Especiales
Rejillas de catálogo con orden y filtros. En táctil muestran `TvBackButton`; en TV se sale
con `BACK`. Especiales puede usar partículas estacionales opcionales.

### Tablet
La capa táctil reutiliza los mismos componentes, con objetivos más grandes y botón de
volver cuando la pantalla no tiene una affordance física. Sigue siendo deuda una pasada
táctil dedicada.

## 7. Movimiento

- **Cero blur en tiempo real.** `Modifier.blur()` y `RenderEffect` están prohibidos.
- **Cero escala por tarjeta y cero atenuación de vecinas.** El foco se lee por borde,
  `zIndex`, relleno sólido y luminosidad del título.
- El chrome no tiene animaciones autónomas. Excepciones acotadas:
  - el hero rota cada 12 s solo cuando no tiene foco;
  - las partículas estacionales son opcionales y se escalan según el dispositivo o el modo
    de rendimiento;
  - hay pulsos de carga en Favoritos y Buscar.
- La interfaz respeta `rememberReducedMotion()` y la escala de animación del sistema.
- Los carruseles usan claves estables y no animan a las tarjetas fuera del foco.

## 8. Do / Don't

### Do
- Usar `BACK` como retroceso en TV.
- Mantener un solo foco activo por pantalla.
- Poner título y metadatos fuera del póster.
- Usar relleno sólido en chips y botones al enfocar.
- Usar borde de 1,5 dp solo en tarjetas.
- Mantener el gutter de 48 dp y el arte a sangre.
- Respetar `rememberReducedMotion()`.

### Don't
- Añadir bordes blancos o marcos grises de 1 dp en reposo.
- Escalar tarjetas o atenuar a sus vecinas.
- Usar `Modifier.blur()` o `RenderEffect`.
- Poner botones de “Volver” en TV.
- Introducir serif editorial, mayúsculas decorativas o puntos medios.
- Reintroducir metáforas espaciales (constelaciones, nebulosas) o “muro mosaico” en
  Descubrir: la pantalla real es un rail de temáticas.
- Convertir el tráiler en una excusa para usar WebView en la interfaz.

## 9. Deuda conocida

- `TvPalette` conserva nombres históricos (`TextSecondaryCool`, `FocusInk`) que ya no
  describen sus valores.
- El modelo interno `MedusaMosaicTag` conserva “Mosaic” aunque la UI sea un rail; es deuda
  de nombres, no de comportamiento.
- No hay benchmark automático de frames ni prueba automática de contraste en CI.
- La capa táctil de tablet merece una pasada propia.
- `docs/catalogs/` no está versionado; el workflow de catálogos es manual.
