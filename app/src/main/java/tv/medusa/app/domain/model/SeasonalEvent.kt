package tv.medusa.app.domain.model

import java.util.Calendar

/**
 * Representa un evento temático estacional (Halloween, Navidad, San Valentín, Premios).
 *
 * Sigue una dirección de arte sobria y cinematográfica (estilo Apple TV+ / Criterion Channel),
 * sin emojis ni estéticas infantiles.
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
    val matchingGenres: List<String>,
    val accentHex: String,
    val homeSectionTitle: String
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
            badgeLabel = "CICLO DE TERROR",
            title = "Especial Halloween: Clásicos del Miedo",
            subtitle = "Una cuidada selección de misterio, horror y suspense psicológico de tu catálogo",
            startMonth = Calendar.OCTOBER,
            startDay = 15,
            endMonth = Calendar.NOVEMBER,
            endDay = 6,
            matchingGenres = listOf("Terror", "Horror", "Misterio", "Mystery", "Suspense", "Thriller"),
            accentHex = "#EA580C",
            homeSectionTitle = "Ciclo de Terror • Especial de Temporada"
        ),
        SeasonalEvent(
            id = "christmas",
            badgeLabel = "SELECCIÓN DE INVIERNO",
            title = "Cine Navideño & Familiar",
            subtitle = "Grandes historias familiares, clásicos de animación y comedia para estas fiestas",
            startMonth = Calendar.DECEMBER,
            startDay = 1,
            endMonth = Calendar.JANUARY,
            endDay = 8,
            matchingGenres = listOf("Familiar", "Family", "Navidad", "Holiday", "Animación", "Animation", "Comedia", "Comedy"),
            accentHex = "#DC2626",
            homeSectionTitle = "Selección Festiva • Cine de Invierno"
        ),
        SeasonalEvent(
            id = "valentines",
            badgeLabel = "CICLO ROMÁNTICO",
            title = "Historias de Pasión & Romance",
            subtitle = "Los romances más aclamados y el mejor drama cinematográfico",
            startMonth = Calendar.FEBRUARY,
            startDay = 7,
            endMonth = Calendar.FEBRUARY,
            endDay = 20,
            matchingGenres = listOf("Romance", "Romántica", "Drama"),
            accentHex = "#BE185D",
            homeSectionTitle = "Ciclo Romántico • Selección de Pasión"
        ),
        SeasonalEvent(
            id = "awards_season",
            badgeLabel = "TEMPORADA DE PREMIOS",
            title = "Obras Maestras & Galardonadas",
            subtitle = "Cine de culto, obras aclamadas por la crítica y selecciones de autor",
            startMonth = Calendar.FEBRUARY,
            startDay = 25,
            endMonth = Calendar.MARCH,
            endDay = 25,
            matchingGenres = listOf("Drama", "Historia", "History", "Biografía", "Biography", "Crimen", "Crime"),
            accentHex = "#D97706",
            homeSectionTitle = "Temporada de Premios • Cine Aclamado"
        )
    )

    fun getActiveEvent(now: Calendar = Calendar.getInstance()): SeasonalEvent? {
        return ALL_EVENTS.firstOrNull { it.isActive(now) }
    }
}
