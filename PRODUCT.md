# Product: Medusa TV (Tujelly)

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Living room TV viewers and Android tablet users managing self-hosted Jellyfin media servers. Users operate the application from a couch at a 3-meter distance using a standard TV remote control (D-Pad, Center/OK, and physical Back key), with secondary usage on touch tablets.

## Product Purpose

Medusa TV is a native, high-performance Android TV streaming client engineered to turn self-hosted Jellyfin libraries into a modern, cinematic 10-foot experience comparable to Apple TV+ and Netflix. It solves the performance lag, washed-out grey containers, and clunky web-view wrappers common in traditional media center clients.

## Positioning

Unlike sluggish web-based Jellyfin wrappers, Medusa TV is built 100% natively in Kotlin and Jetpack Compose TV. It features:
- **Locked 60 FPS Performance:** By strictly banning real-time GPU blur shaders (`Modifier.blur()` / `RenderEffect`), it runs smoothly even on entry-level Android TV dongles.
- **Pure Full-Bleed Artwork:** Eliminates generic grey card text containers, letting cover art breathe on an OLED-grade pitch-black canvas.
- **Inescapable 3-Meter Focus:** Active elements illuminate in electric cyan with deep ink text, lift to `zIndex(10f)` and gain a 1.5dp accent border on the artwork. Since v2.3.12 inactive carousel items keep full opacity (the per-card spotlight was removed for stable 60 FPS), so focus is carried by elevation, border and title brightness instead.
- **Ecosystem Integration:** Connects seamlessly with Jellyfin servers, Trakt.tv scrobbling, and TMDB enriched metadata.

## Operating Context

- **Primary:** Living room Smart TVs, Google TV, Fire TV, and Android TV boxes at 1080p and 4K resolutions, operated via D-Pad remote control.
- **Secondary:** Android tablets operated via touchscreen.
- **Network:** Home LAN and remote server streaming, supporting offline demo mode and background catalog synchronization.
- **Distribution:** Public GitHub repository with releases; the app self-updates from the signed APK attached to each GitHub Release. No Firebase, analytics, crash reporting, or Play Store dependency. TMDB lookups use a user-supplied API key configured in Settings.

## Capabilities and Constraints

### Capabilities
1. **Universal Navigation (`TvTopBar`):** Minimalist top header with brand emblem and 5 compact circular icon buttons: Inicio, Descubrir, Favoritos, Buscar y Ajustes.
2. **Home Billboard & Dynamic Rows:** Hero banner with ClearLogo transparent vector titles, favorite genres row, platform brand tiles (Netflix, Prime Video, Disney+, Apple TV+, Max, Crunchyroll), and curated carousels.
3. **Card Archetypes:**
   - *Vertical Media Card (2:3):* Full-bleed poster art with top-right score badge and external title/year on void with autoscroll marquee on focus.
   - *Continue Watching Card (16:9):* Panoramic backdrop with episode code tag (`T2:E4`), floating cyan progress bar, and centered play overlay.
   - *Top 10 Today Card:* 2:3 vertical poster flanked by oversized gradient ranking numbers (1 to 10).
   - *Sagas & Collections (16:9):* Multi-disc stacked tab silhouette communicating film collections.
4. **Instant Tag Discovery (`MedusaScreen`):** Direct exploration feed pairing a 4-icon format selector with a horizontal genre/mood tag rail, updating the media carousel in real time.
5. **Detail Screen:** Full-width cinematic view with hero metadata, action buttons (`Reproducir`, `Favoritos`, `Trailer`, `Visto`), seasons/episodes picker, and recommendations carousels.
6. **ExoPlayer Video Engine:** Direct play and transcoding with Apple TV-style Scrubber HUD, audio/subtitle track selector, and binge-watching next episode countdown.
7. **Search & Integrated Keyboard:** 10-foot D-Pad optimized on-screen keyboard with instant predictive search.
8. **Settings & Personalization:** Accent color themes (Cian, Ámbar Cine, Blanco Puro / Monocromático), button display modes (solo iconos, solo texto, ambos), Trakt linking, and OTA auto-updates.

### Constraints
- **Zero Real-Time Blur:** Prohibits `Modifier.blur()` and `RenderEffect` across all screens to guarantee 60 FPS on TV hardware.
- **Zero Idle Border Slop:** No 1px grey outline borders in idle state on buttons, chips, or cards.
- **Remote-First Navigation:** On TV, on-screen back buttons are banned; navigation relies strictly on the physical remote `BACK` key. On tablets, an elegant 38dp circular obsidian glass overlay button handles touch return.

## Brand Commitments

- **Name:** Medusa TV (Tujelly).
- **Emblem:** Bioluminescent Jellyfish header mark.
- **Palette Identity:** Pure pitch-black ground (`#04060A`), obsidian glass (`0x18FFFFFF`), electric focus cyan (`#00D2FF`), and crisp gold rating (`#FACC15`).
- **Typography:** DM Sans (bundled) across the whole interface; DM Serif Display is no longer used in UI. Focus marquee autoscroll on long card titles.

## Evidence on Hand

- Native Kotlin codebase in `app/src/main/java/tv/medusa/app/`.
- Verified and running on Android TV 1080p emulators and physical hardware.
- Functional Jellyfin API client, Trakt OAuth integration, and SQLite Room local cache.

## Product Principles

1. **Content First, Zero Slop:** The movie, series, or episode artwork is the star. UI containers, grey background boxes, and 1px outline noise recede into negative space.
2. **Inescapable 10-Foot Focus:** At any microsecond, the user knows exactly where the remote control is pointing from 3 meters away.
3. **Hardware-Friendly Fluidity:** Rock-solid 60 FPS achieved through native alpha channels and compiled gradients instead of heavy post-processing.
4. **Remote-First Architecture:** Respect TV platform standards. Trust the remote control's physical keys rather than cluttering screens with mobile-oriented back buttons.

