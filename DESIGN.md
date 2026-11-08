---
name: "Medusa TV (Tujelly)"
description: "Cinematic 10-foot Leanback interface inspired by Apple TV and Netflix, optimized for Android TV hardware and OLED displays."
colors:
  void-black: "#06080d"
  surface-dark: "#0c1017"
  surface-elevated: "#141b26"
  obsidian-glass: "#16ffffff"
  obsidian-glass-active: "#28ffffff"
  gradient-scrim: "#cc06080d"
  focus-cyan: "#00f2fe"
  focus-content-dark: "#06080d"
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
    letterSpacing: "-0.02em"
  headline:
    fontFamily: "Inter, Roboto, sans-serif"
    fontSize: "24px"
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: "-0.01em"
  title:
    fontFamily: "Inter, Roboto, sans-serif"
    fontSize: "18px"
    fontWeight: 600
    lineHeight: 1.3
    letterSpacing: "0em"
  body:
    fontFamily: "Inter, Roboto, sans-serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: "0.01em"
  label:
    fontFamily: "Inter, Roboto, sans-serif"
    fontSize: "11px"
    fontWeight: 600
    lineHeight: 1.2
    letterSpacing: "0.05em"
rounded:
  badge: "4px"
  chip: "10px"
  card: "12px"
  collection: "14px"
  circle: "9999px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "16px"
  lg: "24px"
  xl: "36px"
  gutter: "48px"
components:
  button-primary:
    backgroundColor: "{colors.obsidian-glass}"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.chip}"
    padding: "8px 18px"
  button-primary-focus:
    backgroundColor: "{colors.focus-cyan}"
    textColor: "{colors.focus-content-dark}"
    rounded: "{rounded.chip}"
    padding: "8px 18px"
  filter-chip:
    backgroundColor: "{colors.obsidian-glass}"
    textColor: "{colors.text-secondary}"
    rounded: "{rounded.chip}"
    padding: "6px 14px"
  filter-chip-selected:
    backgroundColor: "{colors.obsidian-glass-active}"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.chip}"
    padding: "6px 14px"
  filter-chip-focus:
    backgroundColor: "{colors.focus-cyan}"
    textColor: "{colors.focus-content-dark}"
    rounded: "{rounded.chip}"
    padding: "6px 14px"
  media-card-poster:
    backgroundColor: "{colors.surface-elevated}"
    rounded: "{rounded.card}"
    width: "150px"
    height: "225px"
  media-card-backdrop:
    backgroundColor: "{colors.surface-elevated}"
    rounded: "{rounded.card}"
    width: "280px"
    height: "158px"
  media-card-collection:
    backgroundColor: "{colors.surface-elevated}"
    rounded: "{rounded.collection}"
    width: "320px"
    height: "180px"
---

# Design System: Medusa TV (Tujelly)

## Overview

**Creative North Star: "Cinematic Leanback Precision"**

Medusa TV is an editorial, zero-slop streaming interface built specifically for Android TV, living room displays, and OLED panels. Designed following the clean, content-first philosophies of Apple TV+ and Netflix, it rejects artificial cosmetic containers, decorative border noise, and fantasy prose in favor of pure cinematic art, strong focal hierarchy, and high-performance rendering.

The entire interface rests on pure dark cinema negative space (`#06080D`), giving cover art and photographic backdrops total dominance. Interactive elements possess absolute clarity: exactly one item on screen commands focus at any moment via an electric cyan beacon (`#00F2FE`) with dark high-contrast typography, while neighboring cards in active carousels recede via smooth spotlight attenuation (`alpha = 0.65f`). Real-time blur effects (`Modifier.blur()` / `RenderEffect`) are strictly prohibited to ensure a locked 60 FPS frame rate on TV hardware.

**Key Characteristics:**
- **Zero Idle Border Slop:** Clean obsidian glass surfaces with no 1px outline noise in resting state.
- **Inescapable 10-Foot Focus:** High-contrast solid cyan focus with deep ink text (`#06080D`) readable from 3 meters.
- **Pure Full-Bleed Art:** Movie and series cards are 100% photographic artwork with title and metadata displayed cleanly outside the poster on the dark ground plane.
- **Hardware-Friendly Performance:** Flat alpha layers and compiled native gradients instead of GPU-heavy real-time blur.
- **Remote-First Navigation:** Zero on-screen back buttons on TV; navigation relies naturally on the physical TV remote `BACK` key.

## Colors

The palette is engineered for high contrast on OLED living room screens: absolute dark space, crisp white typography, and a razor-sharp focus beacon.

### Primary
- **Focus Cyan** (`#00F2FE`): The sole active focus indicator. Used for card focus borders (`2.dp`), active button fills, filter selection, and playback progress bars.
- **Focus Content Dark** (`#06080D`): Ultra-dark ink color used for text and icons inside solid focused buttons, ensuring high WCAG contrast.

### Secondary
- **Amber Rating** (`#F59E0B`): Star ratings and community scores. Warm cinematic gold that never collides with the focus cyan accent.

### Neutral
- **Void Black** (`#06080D`): The root background plane. True deep dark tone that allows OLED pixels to shut off.
- **Surface Dark** (`#0C1017`): Elevated resting container for panels, search keyboards, and drawers.
- **Surface Elevated** (`#141B26`): Neutral placeholder backing for unloaded poster and backdrop images.
- **Obsidian Glass** (`#16FFFFFF`): Translucent resting container (9% white alpha) for action buttons and filter chips.
- **Obsidian Glass Active** (`#28FFFFFF`): Active format filter state and selected tab highlights.
- **Gradient Scrim** (`#CC06080D`): Directional black gradient overlay protecting text legibility over bright backdrops.
- **Text Primary** (`#F8FAFC`): Crisp white for titles, active headers, and focused labels.
- **Text Secondary** (`#94A3B8`): Cool neutral slate for release years, seasons, runtimes, and resting metadata.
- **Text Tertiary** (`#64748B`): Muted slate for secondary metadata tags and unselected icons.

### Named Rules
**The Single Beacon Rule.** Exactly one element owns the focus indicator at any given moment. Idle elements must never simulate focus or carry glowing accents.

**The Zero-Blur Rule.** All glass, scrim, and layer treatments must be achieved using native color alphas and linear `Brush` gradients. Costly GPU blur shaders are strictly prohibited.

## Typography

**Typography Stack:** System Sans-Serif (Inter / Roboto)  
**Character:** Neutral, clean, modern grotesk. Typography serves pure legibility and hierarchy; it never distracts from the cinematic imagery.

### Hierarchy
- **Display** (Bold, 36px, line-height 1.15, letter-spacing -0.02em): Hero titles when transparent ClearLogo vector artwork is unavailable. Supports autoscroll marquee on focus.
- **Headline** (Bold, 24px, line-height 1.2, letter-spacing -0.01em): Section row titles (`Top 10 Today`, `Continuar viendo`, `Títulos similares`).
- **Title** (SemiBold, 18px, line-height 1.3): Focused card title positioned beneath poster artwork on the dark ground plane.
- **Body** (Regular, 14px, line-height 1.5): Editorial overview, synopsis, and series descriptions (max 5 lines with ellipsis).
- **Label** (SemiBold, 11px, letter-spacing 0.05em, uppercase): Format chips, episode codes (`T2:E4`), and rating badges.

### Named Rules
**The Marquee Reveal Rule.** Text that overflows its single-line boundary must execute a smooth continuous marquee autoscroll (`repeatDelayMillis = 2000`) exclusively when its parent card or button holds D-Pad focus. Resting text truncates cleanly with ellipsis.

## Layout

Designed for 10-foot Leanback interaction on 1080p and 4K TV displays:

- **Screen Gutters:** Consistent `48.dp` horizontal margins on both edges.
- **Overlaid Top Bar:** `TvTopBar` floats on top of the root container (`Z = 10`), allowing full-bleed hero artwork to reach `Y = 0` with a smooth 90dp vertical dark gradient scrim (`0xD906080D` to transparent).
- **Carousel Spacing:** Content carousels use `16.dp` item spacing and `32.dp` - `40.dp` vertical row spacing.
- **Full-Width Detail View:** `DetailScreen` is an edge-to-edge unified layout. Hero artwork and metadata lead at the top, and carousels expand across the full 1080p width with zero collision or cutoffs.

## Elevation & Depth

Medusa TV relies on **Z-Index Layering**, **Optical Spotlighting**, and **Scale Zoom** rather than drop shadows:

- **Root Zoom:** Focused cards scale to `1.05f` and set `zIndex(10f)` on the outer root container to elevate smoothly over neighboring cards.
- **Optical Spotlight:** In an active carousel, non-focused sibling cards are attenuated to `alpha = 0.65f`. The focused card stands at `alpha = 1.0f`, making scanning instant and fatigue-free.
- **Ambient Crossfade:** Full-screen backdrop artwork crossfades smoothly (`tween(400ms)`) behind content rows as the user navigates across media items.

### Named Rules
**The Root-Scale Rule.** Zoom scaling (`graphicsLayer { scaleX = ...; scaleY = ... }`) and `zIndex` must be applied to the outer container root before any clipping or border modifiers. Scaling inside a clipped container causes badge and text cutoff.

## Shapes

- **Metadata Badges:** `RoundedCornerShape(4.dp)`. Compact badges for star ratings, resolution (`4K`), and age ratings (`ES-16`, `TV-MA`).
- **Buttons & Filter Chips:** `RoundedCornerShape(10.dp)`. Used across all action buttons (`Reproducir`, `Trailer`, `Favoritos`), format filters, and navigation tabs.
- **Poster & Backdrop Cards:** `RoundedCornerShape(12.dp)`. Applied uniformly to 2:3 vertical posters, 16:9 continue watching cards, and episode thumbnails.
- **Collection Cards:** `RoundedCornerShape(14.dp)` with dual-offset layered tab silhouette suggesting stacked multi-film box sets.
- **Touch Overlays:** `CircleShape`. Play trigger center icons and tablet touch back buttons.

## Components

### Vertical Media Card (`MediaCard`)
- **Format:** 2:3 aspect ratio (`150.dp x 225.dp`), full-bleed artwork, no grey container box beneath the poster.
- **Resting:** Smooth `12.dp` rounded poster, score badge (`★ 8.7`) pinned at top-right with `10.dp` internal padding. Title and metadata beneath poster remain subtle (`alpha = 0.40f`).
- **Focused:** Outer cyan border (`2.dp`), scale `1.05f`, `zIndex(10f)`. Title and year beneath illuminate in solid white/cyan with active marquee scroll. Non-focused row siblings dim to `0.65f`.

### Continue Watching Card (`ContinueWatchingCard`)
- **Format:** 16:9 panoramic backdrop (`280.dp x 158.dp`).
- **Elements:** Episode code tag (`T1:E4`) at top-left, star rating badge at top-right, floating cyan progress bar with `12.dp` horizontal and `10.dp` bottom inset padding over dark gradient.
- **Focused:** Scale `1.05f`, cyan border (`2.dp`), circular frosted play icon revealed in center.

### Ranked Top 10 Card (`RankedMediaCard`)
- **Format:** 2:3 vertical poster flanked by oversized typographic ranking glyphs (1 to 10).
- **Typography:** Custom `110.sp` Bold/Black numerals with vertical gradient fill from `CyanBioluminescent` to transparent, tucked slightly behind the poster edge with a `-16.dp` horizontal offset.

### Sagas & Collection Card (`LandscapeMediaCard`)
- **Format:** 16:9 panoramic card (`320.dp x 180.dp`).
- **Stack Effect:** Two subtle stepped back-plates (`0x16FFFFFF` obsidian glass) peeking behind the top border, instantly communicating a multi-movie collection.
- **Typography:** Metadata tag `COLECCIÓN • [AÑO]` padded by `16.dp` from bottom edge to avoid border collisions.

### Action Buttons & Filter Chips (`TvBackButton`, `FormatFilterBar`)
- **Height & Shape:** Fixed `34.dp` height, `RoundedCornerShape(10.dp)`.
- **Resting:** Solid obsidian glass `Color(0x18FFFFFF)`, content `Color(0xFFCBD5E1)`, zero 1px borders.
- **Focused:** Solid `CyanBioluminescent` (`#00F2FE`) container, deep ink `#06080D` text and icons, scale `1.08f`.

### TV Top Bar (`TvTopBar`)
- **Layout:** Root overlay with horizontal padding `48.dp`, brand logo at start, centered icon-pill navigation tabs (`Inicio`, `Descubrir`, `Favoritos`, `Buscar`, `Ajustes`).
- **Scroll Behavior:** Floats over the hero banner with a top scrim; does not collide with scrolled content.

### Tablet Touch Back Button
- **Format:** `38.dp` circular obsidian glass (`0x40000000`) floating at `TopStart` (`statusBarsPadding()`).
- **Behavior:** Rendered exclusively on touch/tablet devices (`DeviceUtils.isTouchDevice == true`). Bound with `focusProperties { canFocus = false }` to guarantee zero D-Pad focus traps.

## Do's and Don'ts

### Do:
- **Do** rely on the TV remote physical `BACK` key as the primary navigation mechanism for Android TV.
- **Do** position title and metadata outside the poster boundary on the dark ground plane in vertical media rows.
- **Do** illuminate the focused interactive element with solid `Focus Cyan` and deep `#06080D` text for 3-meter legibility.
- **Do** dim non-focused cards in an active carousel to `alpha = 0.65f` to create an optical spotlight.
- **Do** apply `graphicsLayer` scale and `zIndex` to the root container before any clip or border modifiers.
- **Do** use `basicMarquee(repeatDelayMillis = 2000)` on long movie titles and episode descriptions when focused.

### Don't:
- **Don't** use real-time blur (`Modifier.blur()` or `RenderEffect`) anywhere in the app.
- **Don't** draw 1px grey outline borders on cards, buttons, or chips in idle state (*cero border slop*).
- **Don't** render on-screen `[← Back]` buttons in TV mode; TV users navigate via the remote.
- **Don't** enclose vertical poster cards inside rigid grey boxes with static text beneath.
- **Don't** clip badges or progress bars against card focus borders; always provide at least `10.dp` internal breathing room.
- **Don't** invent artificial drop shadows on dark backgrounds.
