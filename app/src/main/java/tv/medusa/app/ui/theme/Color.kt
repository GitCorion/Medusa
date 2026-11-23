package tv.medusa.app.ui.theme

import androidx.compose.ui.graphics.Color

// =============================================================================
// Fondos Cinemáticos Abisales y Obsidiana (0% blur, 100% native alpha / 60 FPS)
// =============================================================================
val AbyssVoid = Color(0xFF04060A)              // Vacío abisal ultra-profundo con matiz oceánico puro
val AbyssSurface = Color(0xFF0A0F1D)           // Superficie base con tinte de agua profunda
val AbyssSurfaceElevated = Color(0xFF10162A)   // Tarjetas y controles elevados sobre el abismo
val AbyssGlass = Color(0x18FFFFFF)             // Brillo de lente esmerilada
val AbyssGlassHighlight = Color(0x32FFFFFF)    // Bisel superior especular de 0.75dp
val AbyssScrim = Color(0xEB04060A)            // Degradado de atmósfera teatral sin banding
 
// =============================================================================
// Metales Cinemáticos, Luces de Proyector y Bioluminiscencia Medusa
// =============================================================================
val GoldNitrate = Color(0xFFD4AF37)            // Oro de nitrato de película de época (sello cinematográfico)
val AmberCinema = Color(0xFFE5A44D)            // Calidez de lámpara halógena 3200K (dorado ámbar)
val CyanBioluminescent = Color(0xFF00D2FF)     // Cian eléctrico original del logotipo oficial de Medusa
val AbyssalCobalt = Color(0xFF0B5EFF)          // Cobalto oceánico profundo de los tentáculos del logotipo
val GlacialAqua = Color(0xFF8BFFFF)            // Aqua glaciar luminoso para reflejos y badges activos
val VioletBioluminescent = Color(0xFFA78BFA)    // Violeta abisal translúcido (alternativo)
val CyanFocusedContent = Color(0xFF04060A)     // Contenido de alto contraste sobre resalte bioluminiscente
val PlatinumSilver = Color(0xFFE2E4E9)         // Monocromo platino de alta costura
val BorderSubtle = Color(0x20FFFFFF)           // Borde sutil modal anti-slop
 
// =============================================================================
// Tipografía con Temperatura Noble (Reemplaza los grises fríos de Tailwind)
// =============================================================================
val TextPrimary = Color(0xFFF7F5F0)            // Blanco marfil cálido para lectura descansada a 3m
val TextSecondary = Color(0xFFA39E93)          // Gris pergamino cinematográfico
val TextTertiary = Color(0xFF827D73)           // Grafito atenuado para micro-metadatos (4.9:1 sobre abismo, WCAG AA)
val TextGold = Color(0xFFE5C07B)               // Oro suave para clasificaciones y distinciones
 
/**
 * Valores recurrentes centralizados (Anti-Slop Token System).
 * Centralizarlos evita colores hardcodeados dispersos en componentes.
 */
object TvPalette {
    val FocusInk = Color(0xFF04060A)
    val TextSecondaryCool = Color(0xFFA39E93)   // Unificado con TextSecondary para evitar choque térmico
    val GlassIdle = Color(0x18FFFFFF)
    val GlassRaised = Color(0x22FFFFFF)
    val TextMuted = Color(0xFFCBD5E1)
    val GlassSubtle = Color(0x14FFFFFF)
    val TextSoft = Color(0xFFE2E8F0)
    val GlassSelected = Color(0x28FFFFFF)
    val Placeholder = Color(0xFF64748B)
    val GlassDivider = Color(0x33FFFFFF)
 
    // Superficies y contenedores con matiz oceánico profundo
    val SurfaceCard = Color(0xFF080D1A)
    val SurfaceChip = Color(0xFF0D1424)
    val SurfaceTrigger = Color(0xFF0F182B)
    val SurfaceBrandCard = Color(0xFF0E1626)
    val SurfaceBrandCardBottom = Color(0xFF070B14)
    val FallbackGradientTop = Color(0xFF142038)
    val FallbackGradientBottom = Color(0xFF080D18)
    val BadgeBackdrop = Color(0xCC000000)

    // Semánticos cinematográficos
    val SemanticWatched = GoldNitrate
    val SemanticFavorite = AmberCinema
    val SemanticError = Color(0xFFEF4444)
}
