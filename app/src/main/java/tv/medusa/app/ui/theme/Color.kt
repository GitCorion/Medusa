package tv.medusa.app.ui.theme

import androidx.compose.ui.graphics.Color

// =============================================================================
// Fondos Cinemáticos Abisales y Obsidiana (0% blur, 100% native alpha / 60 FPS)
// =============================================================================
val AbyssVoid = Color(0xFF070709)              // Obsidiana pura con tono carbón cálido (evita negro plano digital)
val AbyssSurface = Color(0xFF101014)           // Superficie base de chasis
val AbyssSurfaceElevated = Color(0xFF18171E)   // Tarjetas y controles elevados
val AbyssGlass = Color(0x18FFFFFF)             // Brillo de lente esmerilada
val AbyssGlassHighlight = Color(0x32FFFFFF)    // Bisel superior especular de 0.75dp
val AbyssScrim = Color(0xEB070709)            // Degradado de atmósfera teatral

// =============================================================================
// Metales Cinemáticos, Luces de Proyector y Bioluminiscencia
// =============================================================================
val GoldNitrate = Color(0xFFD4AF37)            // Oro de nitrato de película de época
val AmberCinema = Color(0xFFF59E0B)            // Calidez de lámpara halógena 3200K
val CyanBioluminescent = Color(0xFF00F2FE)     // Cian abisal conservado para fidelidad de perfil
val CyanFocusedContent = Color(0xFF070709)     // Contenido de alto contraste sobre resalte
val PlatinumSilver = Color(0xFFE2E4E9)         // Monocromo platino de alta costura

// =============================================================================
// Tipografía con Temperatura Noble (Reemplaza los grises fríos de Tailwind)
// =============================================================================
val TextPrimary = Color(0xFFF7F5F0)            // Blanco marfil cálido para lectura descansada a 3m
val TextSecondary = Color(0xFFA39E93)          // Gris pergamino cinematográfico
val TextTertiary = Color(0xFF827D73)           // Grafito atenuado para micro-metadatos (4.9:1 sobre abismo, WCAG AA)
val TextGold = Color(0xFFE5C07B)               // Oro suave para clasificaciones y distinciones

/**
 * Valores recurrentes extraídos del código (no del DESIGN.md). Centralizarlos evita
 * que cada pantalla invente su propio cristal/gris y facilita cambiar la paleta.
 */
object TvPalette {
    val FocusInk = Color(0xFF0F172A)
    val TextSecondaryCool = Color(0xFF94A3B8)
    val GlassIdle = Color(0x18FFFFFF)
    val GlassRaised = Color(0x22FFFFFF)
    val TextMuted = Color(0xFFCBD5E1)
    val GlassSubtle = Color(0x14FFFFFF)
    val TextSoft = Color(0xFFE2E8F0)
    val GlassSelected = Color(0x28FFFFFF)
    val Placeholder = Color(0xFF64748B)
    val GlassDivider = Color(0x33FFFFFF)
}
