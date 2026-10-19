package tv.medusa.app.domain.model

import tv.medusa.app.R
import java.util.Calendar

enum class ParticleType {
    NONE,
    LEAVES,
    SNOW,
    EMBERS
}

/**
 * Representa un evento temático estacional (Halloween, Navidad, San Valentín).
 * Con soporte para fondo cinematográfico 16:9, partículas ambientales y keyword de TMDb.
 */
data class SeasonalEvent(
    val id: String,
    val badgeLabel: String,
    val title: String,
    val subtitle: String,
    val startMonth: Int, // 0-indexed: Calendar.JANUARY (0) .. Calendar.DECEMBER (11)
    val startDay: Int,   // 1-indexed (1 .. 31)
    val endMonth: Int,
    val endDay: Int,
    val accentHex: String,
    val homeSectionTitle: String,
    val backdropResId: Int,
    val particleType: ParticleType,
    val tmdbKeywordId: String,
    val primaryGenreName: String,
    val soundResId: Int = 0
) {
    fun isActive(calendar: Calendar = Calendar.getInstance()): Boolean {
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        return if (startMonth < endMonth || (startMonth == endMonth && startDay <= endDay)) {
            val afterStart = month > startMonth || (month == startMonth && day >= startDay)
            val beforeEnd = month < endMonth || (month == endMonth && day <= endDay)
            afterStart && beforeEnd
        } else {
            // Evento que cruza fin de año (ej: 1 de Dic al 8 de Ene)
            val inFirstYear = month > startMonth || (month == startMonth && day >= startDay)
            val inNextYear = month < endMonth || (month == endMonth && day <= endDay)
            inFirstYear || inNextYear
        }
    }
}

object SeasonalEventsManager {

    val ALL_EVENTS = listOf(
        SeasonalEvent(
            id = "halloween",
            badgeLabel = "HALLOWEEN",
            title = "Noche de Terror",
            subtitle = "Grandes clásicos y los estrenos de terror más aclamados para la noche de brujas",
            startMonth = Calendar.OCTOBER,
            startDay = 15,
            endMonth = Calendar.NOVEMBER,
            endDay = 6,
            accentHex = "#EA580C",
            homeSectionTitle = "Selección de Halloween",
            backdropResId = R.drawable.bg_seasonal_halloween,
            particleType = ParticleType.LEAVES,
            tmdbKeywordId = "3335",
            primaryGenreName = "Terror"
        ),
        SeasonalEvent(
            id = "christmas",
            badgeLabel = "NAVIDAD",
            title = "Magia Festiva",
            subtitle = "Grandes historias familiares, clásicos entrañables y cine de invierno para estas fiestas",
            startMonth = Calendar.DECEMBER,
            startDay = 1,
            endMonth = Calendar.JANUARY,
            endDay = 8,
            accentHex = "#DC2626",
            homeSectionTitle = "Selección de Navidad",
            backdropResId = R.drawable.bg_seasonal_christmas,
            particleType = ParticleType.SNOW,
            tmdbKeywordId = "207317",
            primaryGenreName = "Familiar"
        ),
        SeasonalEvent(
            id = "valentines",
            badgeLabel = "SAN VALENTÍN",
            title = "Historias de Amor",
            subtitle = "Los romances más aclamados y el mejor drama cinematográfico para celebrar juntos",
            startMonth = Calendar.FEBRUARY,
            startDay = 7,
            endMonth = Calendar.FEBRUARY,
            endDay = 20,
            accentHex = "#BE185D",
            homeSectionTitle = "Selección Romántica",
            backdropResId = R.drawable.bg_seasonal_valentines,
            particleType = ParticleType.EMBERS,
            tmdbKeywordId = "9840",
            primaryGenreName = "Romance"
        )
    )

    fun getActiveEvent(now: Calendar = Calendar.getInstance()): SeasonalEvent? {
        return ALL_EVENTS.firstOrNull { it.isActive(now) }
    }
}
