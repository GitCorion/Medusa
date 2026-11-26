# Diseño de interfaz — Medusa TV

Documentación de las decisiones de diseño, sistema visual y restricciones de
rendimiento de Medusa TV sobre la base de `main` (`2.4.1`).

> La versión interactiva está publicada en <https://gitcorion.github.io/Medusa/>.

## 1. Ficha técnica

| | |
|---|---|
| Producto | Medusa TV, cliente Jellyfin nativo para Android TV y tablet |
| Repositorio | <https://github.com/GitCorion/Medusa> |
| Distribución | APK firmado en GitHub Releases con actualización OTA |
| Plataforma | Android 8.0+ (`minSdk 26`, `targetSdk 36`) |
| Stack | Kotlin 2.0.21, Compose TV, Hilt, Room 22, Media3 |
| Capturas | Televisor 1920 × 1080 |
| Licencia | GPL-3.0 |

## 2. Criterios para televisor

Diseñar para Android TV impone cuatro restricciones concretas frente a una app
móvil o web:

- **Distancia de lectura.** En televisor el texto pequeño pierde legibilidad
  rápido; las pantallas priorizan jerarquía clara y poca densidad por fila.
- **Control por mando (D-pad).** No hay puntero ni *hover*; `OK` selecciona y
  `BACK` retrocede. El foco actual debe ser inequívoco en todo momento.
- **Contraste en sala.** La interfaz convive con luz ambiental y reflejos de
  pantalla; los textos y estados necesitan contraste suficiente sobre fondos
  oscuros.
- **Hardware limitado.** En *dongles* y televisores de gama de entrada, el
  desenfoque en tiempo real (`blur`) y las animaciones por tarjeta provocan
  caídas de *framerate*.

Regla general: cualquier elemento que no aporte foco, jerarquía o dato útil se
omite, y no se alteran las interacciones estándar del mando.

## 3. Decisiones principales

### Navegación y carátulas

- Barra superior con iconos de navegación (`18 dp`) sin subrayados ni pestañas
  que obliguen a pulsar `OK` para cambiar de vista.
- Carátulas y fondos a pantalla completa. En las filas estándar, título y año
  van fuera del póster; *Continuar viendo* muestra título y barra de progreso
  sobre la tarjeta.
- Sin cajas grises con texto incrustado ni bordes en reposo.

### Foco único y rendimiento (60 fps)

Las primeras iteraciones escalaban la tarjeta seleccionada (`1,1x`) y atenuaban
las vecinas. En hardware real generaba temblor horizontal en los carruseles y
coste extra de composición. La solución actual es más simple y estable:

- **Tarjeta enfocada:** borde de `1,5 dp` en cian (`#00D2FF`) y `zIndex(10f)`,
  sin escalado.
- **Tarjetas vecinas:** conservan el 100 % de opacidad.
- **Título:** pasa de `0.75f` a opacidad completa al recibir foco.

### Evolución en `main`

| Fecha | Commit | Cambio |
|---|---|---|
| 22 nov | `8430b41` | Paleta oscura propia (`#04060A`) y acento cian `#00D2FF` |
| 22 nov | `2d6958f` | Prueba de navegación tipográfica con subrayados (descartada) |
| 22 nov | `f47e297` | Prueba de cabecera con arcos/sonar animados (descartada por ruido visual) |
| 22 nov | `43ab861` | Ajuste de tamaños, contraste y legibilidad en televisor real |
| 22 nov | `886ec79` | Retirada de Fraunces (serif) en favor de tipografía sans (DM Sans) |
| 22 nov | `8ed14d9` | Prueba con tabs *pill* y escala `1,1x` de Material TV (descartada) |
| 23 nov | `507fa6d` | Consolidación: DM Sans, `#00D2FF`, dock de iconos, borde `1,5 dp` y cero escala |

## 4. Sistema visual

### Color

| Token | Hex | Uso | Contraste sobre `#04060A` |
|---|---|---|---|
| Fondo raíz | `#04060A` | Fondo principal y texto sobre acentos | — |
| Superficie base | `#0A0F1D` | Paneles y contenedores base | 1,1:1 |
| Superficie elevada | `#10162A` | Controles y tarjetas | 1,1:1 |
| Cian principal | `#00D2FF` | Foco, selección y progreso | 11,3:1 |
| Azul secundario | `#0B5EFF` | Gradientes y brillos (no para texto) | 3,9:1 |
| Cian claro | `#8BFFFF` | Detalles y reflejos | 17,2:1 |
| Ámbar | `#E5A44D` | Favoritos | 9,4:1 |
| Dorado | `#D4AF37` | Indicador de visto | 9,6:1 |
| Amarillo nota | `#FACC15` | Valoraciones | 13,2:1 |
| Blanco | `#FFFFFF` | Texto principal | 20,3:1 |
| Gris secundario | `#A1A1AA` | Metadatos y texto secundario | 7,9:1 |
| Gris tenue | `#71717A` | Microdatos y separadores | 4,2:1 |

### Tipografía

Una única familia tipográfica: **DM Sans** (variable, licencia OFL).

| Rol | Tamaño | Peso |
|---|---|---|
| Display / cabecera | 32–36 sp | 700 |
| Titular | 24 sp | 700 |
| Título | 16–20 sp | 600 |
| Cuerpo | 13–15 sp | 400 |
| Etiqueta | 11–12 sp | 600 |

### Radios y movimiento

| Elemento | Radio |
|---|---|
| Insignia | 6–9 dp |
| Chip | 8 dp |
| Tarjeta | 10 dp |
| Cápsula del dock | 18 dp |
| Círculo | 999 dp |

- Sin `Modifier.blur()` ni `RenderEffect`.
- Sin animaciones autónomas en la barra de navegación. El carrusel principal
  rota cada 12 s cuando no tiene foco y las partículas estacionales son
  opcionales.
- Respeta la configuración de movimiento reducido del sistema.

## 5. Pantallas

- **Inicio:** cabecera a pantalla completa con ClearLogo, metadatos, acción
  primaria y filas de pósters / *Continuar viendo*.
- **Descubrir:** filtros de formato y rail de temáticas con lógica `AND` (`BACK`
  deshace el último filtro aplicado).
- **Ficha:** póster a la izquierda, metadatos y sinopsis sin caja contenedora a
  la derecha, temporadas/episodios y acciones circulares.
- **Buscar:** teclado adaptado al mando a la izquierda y sugerencias/resultados
  a la derecha en la misma pantalla.
- **Favoritos:** colección filtrable y ordenable directamente en la vista.
- **Ajustes:** barra lateral y grupos sobre superficie elevada con estado del
  servidor y sincronización.

## 6. Accesibilidad y deuda técnica

**Cubierto:**
- Contraste AA en textos principales y foco visible en todos los controles.
- Soporte para teclas físicas y `BACK` en TV, además de `TvBackButton` táctil.
- Subtítulos configurables en tamaño y fondo; textos en español e inglés.

**Deuda conocida:**
1. **Capa táctil en tablet:** el dock de `18 dp` y el foco por borde están
   optimizados para mando; falta una revisión específica para uso táctil.
2. **Nombres heredados en `TvPalette`:** constantes como `TextSecondaryCool` o
   `FocusInk` conservan nombres antiguos.
3. **Benchmarks en CI:** la ausencia de `blur` se vigila en código, pero no hay
   medición automática de *frame time* en CI.
4. **Catálogos estáticos:** `catalogs.yml` genera `docs/catalogs`, excluido por
   `.gitignore` en este árbol.

## 7. Reproducir el estudio en local

```bash
adb exec-out screencap -p > captura.png
python3 -m http.server 8123 -d docs
# Abrir http://127.0.0.1:8123/
```

## 8. Créditos

Jellyfin, TMDB y Trakt son proyectos independientes. *This product uses the TMDB
API but is not endorsed or certified by TMDB.* DM Sans se distribuye bajo SIL
OFL. El cartel de *Night of the Living Dead* (1968) es de dominio público; el
resto de pósters pertenecen a sus respectivos titulares.
