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
 * Soporta curaduría multinivel: listas editoriales de Trakt, palabras clave y géneros de TMDb,
 * y escaneo por términos de búsqueda en la biblioteca local.
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
    val traktCuratedLists: List<Pair<String, String>> = emptyList(), // Pair(username/userSlug, listSlugOrId)
    val tmdbKeywords: List<String> = emptyList(),
    val genreNames: List<String> = emptyList(),
    val searchTerms: List<String> = emptyList(),
    val soundResId: Int = 0
) {
    /** Propiedad de compatibilidad hacia atrás: devuelve la primera palabra clave o string vacío. */
    val tmdbKeywordId: String get() = tmdbKeywords.firstOrNull() ?: ""

    /** Propiedad de compatibilidad hacia atrás: devuelve el género principal o "Terror". */
    val primaryGenreName: String get() = genreNames.firstOrNull() ?: "Terror"

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
            traktCuratedLists = listOf(
                "trakt" to "official-halloween-movies-and-tv-shows",
                "movist" to "halloween",
                "" to "halloween-movies",
                "" to "spooky-season",
                "" to "essential-horror-movies"
            ),
            tmdbKeywords = listOf("3335", "12339", "616", "3133", "162804", "12377", "10224", "10292", "9715"),
            genreNames = listOf("Terror", "Horror", "Misterio", "Fantasía", "Thriller"),
            searchTerms = listOf(
                "halloween", "noche de brujas", "bruja", "witch", "fantasma", "ghost",
                "vampiro", "vampire", "zombie", "zombi", "slasher", "terror", "horror",
                "misterio", "sobrenatural", "supernatural", "demonio", "demon",
                "monstruo", "monster", "posesion", "posensión", "espeluznante", "spooky", "asesino"
            )
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
            traktCuratedLists = listOf(
                "trakt" to "official-christmas-movies",
                "movist" to "christmas",
                "" to "christmas-classics",
                "" to "holiday-movies"
            ),
            tmdbKeywords = listOf("207317", "6513", "192500", "18035", "9882"),
            genreNames = listOf("Familiar", "Animación", "Comedia"),
            searchTerms = listOf(
                "navidad", "christmas", "santa", "papá noel", "papa noel", "reyes magos",
                "navideño", "navideña", "fiestas", "invierno", "snow", "nieve", "familiar", "espíritu navideño"
            )
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
            traktCuratedLists = listOf(
                "trakt" to "official-valentines-day-movies",
                "movist" to "valentines-day",
                "" to "romantic-movies"
            ),
            tmdbKeywords = listOf("9840", "9799", "1453"),
            genreNames = listOf("Romance", "Drama"),
            searchTerms = listOf(
                "san valentín", "san valentin", "amor", "romance", "romántico", "romántica",
                "love", "valentín", "valentin", "pareja", "enamorados"
            )
        )
    )

    fun getActiveEvent(now: Calendar = Calendar.getInstance()): SeasonalEvent? {
        return ALL_EVENTS.firstOrNull { it.isActive(now) }
    }
}
