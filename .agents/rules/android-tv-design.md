# Reglas de Diseño y Experiencia de Usuario para Android TV (Medusa)

Este documento define las directrices obligatorias de interfaz de usuario y experiencia en Android TV para el proyecto Medusa. Cualquier desarrollo o modificación de UI en esta aplicación DEBE seguir estrictamente estas directrices para evitar estéticas de "plantilla de IA genérica".

## 1. Principios Cinemáticos de Android TV (Estilo Apple TV / Infuse)
- **Cero emojis en la interfaz**: Queda estrictamente prohibido utilizar emojis de texto (como `⏪`, `⏩`, `✨`, `🎬`, etc.) en carteles, botones, barras de progreso o indicadores HUD. Se deben usar iconos vectoriales limpios de Material Icons (`Icons.Default.FastForward`, `Icons.Default.Subtitles`, etc.) o tipografía pura.
- **Paleta de color cinemática**:
  - Fondos de paneles y HUD: Negros profundos y ahumados translúcidos (`#080A12` con opacidad del 85% al 95%), nunca grises planos ni colores sólidos sin gradiente.
  - Tipografía primaria: Blanco puro (`#FFFFFF`) para títulos, tiempo y elementos activos.
  - Tipografía secundaria y metadatos: Gris pizarra suave (`#94A3B8` / `#64748B`), con tamaño proporcionado (11sp a 13sp).
  - Acentos y Foco: Acento sutil (oro tenue, cian cinemático o blanco monochrome) con elevación o escala (`1.05x`) y halos difuminados, NUNCA bordes toscos de 2px de color chillón.

## 2. Experiencia de Reproducción y Navegación (Seek / Scrubbing)
- **Seek por D-Pad Fluido y Debounced**:
  - Al pulsar izquierda o derecha en el mando durante la reproducción a pantalla completa, la interfaz NO debe invocar `exoPlayer.seekTo()` de forma inmediata en cada pulsación repetida (esto satura el buffer de red y provoca tirones).
  - Debe acumular el salto visualmente en un indicador flotante elegante y pulido (ej. `+30s`, `+1m 15s` y tiempo destino en formato `01:24:10`).
  - El salto real en el reproductor debe ejecutarse con un debounce suave (350-450 ms) tras la última pulsación o al pulsar el botón central.
- **Indicador Flotante Minimalista**:
  - Diseñado como una cápsula flotante de cristal ahumado (`RoundedCornerShape(16.dp)`, fondo `0xCC0B0D17`, borde sutil de 0.5dp translúcido).
  - Tipografía tabular limpia y monospaced para números (`FontWeight.SemiBold`).

## 3. Menús Laterales y Modales (Paneles de Ajustes)
- **Estructura tipo Drawer Lateral**:
  - Ancho adecuado para pantalla 1080p/4K (380dp a 420dp).
  - Fondo de cristal oscuro con desenfoque (`Color(0xF2090B14)`).
  - Los ítems de lista deben ser tarjetas estilizadas con esquinas suaves (8dp a 12dp), con estados de foco nítidos pero elegantes.
  - Metadatos de pistas (idioma, códec, canales) formateados con badges estilizados en miniatura (`[5.1]`, `[DTS]`, `[SRT]`).
- **Controles de Audio**:
  - Modos de sonido claros y descriptivos sin jerga incomprensible (ej. "Modo Nocturno: Apagado / Suave (Diálogos claros) / Fuerte").
  - Sliders interactivos con porcentaje visual limpio y navegación izquierda/derecha fluida.

## 4. Respeta el D-Pad de Control Remoto
- Todo elemento interactivo debe tener un orden de foco lógico.
- Nunca atrapar el foco en un slider o lista sin permitir salir fácilmente hacia atrás o hacia la izquierda.
