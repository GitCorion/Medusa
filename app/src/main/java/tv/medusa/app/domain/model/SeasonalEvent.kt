package tv.medusa.app.domain.model

import java.util.Calendar

/**
 * Item curado de la comunidad para colecciones estacionales (ej. clásicos esenciales de Halloween / Navidad).
 */
data class CuratedSeasonalItem(
    val tmdbId: String,
    val title: String,
    val year: Int? = null
)

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
    val excludedGenres: List<String> = listOf("Documental", "Documentary", "Música", "Music", "Bélica", "War", "Guerra", "Western", "Noticias", "News", "Concierto", "Concert", "Cortometraje", "Short"),
    val accentHex: String,
    val homeSectionTitle: String,
    val curatedItems: List<CuratedSeasonalItem> = emptyList(),
    val tmdbDiscoverGenreId: String? = null,
    val tmdbWithoutGenres: String? = null
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

    /**
     * Selección curada comunitaria de obras maestras y clásicos esenciales de Halloween / Terror.
     * Basada en las listas históricas más valoradas de Letterboxd, IMDb Top Horror y agregadores de cine.
     */
    private val HALLOWEEN_CURATED_COMMUNITY = listOf(
        CuratedSeasonalItem("948", "Halloween", 1978),
        CuratedSeasonalItem("424139", "Halloween", 2018),
        CuratedSeasonalItem("694", "The Shining", 1980),
        CuratedSeasonalItem("807", "The Exorcist", 1973),
        CuratedSeasonalItem("493922", "Hereditary", 2018),
        CuratedSeasonalItem("377", "A Nightmare on Elm Street", 1984),
        CuratedSeasonalItem("4232", "Scream", 1996),
        CuratedSeasonalItem("646385", "Scream", 2022),
        CuratedSeasonalItem("138843", "The Conjuring", 2013),
        CuratedSeasonalItem("259693", "The Conjuring 2", 2016),
        CuratedSeasonalItem("351286", "It", 2017),
        CuratedSeasonalItem("4011", "Beetlejuice", 1988),
        CuratedSeasonalItem("917496", "Beetlejuice Beetlejuice", 2024),
        CuratedSeasonalItem("10439", "Hocus Pocus", 1993),
        CuratedSeasonalItem("9479", "The Nightmare Before Christmas", 1993),
        CuratedSeasonalItem("30497", "The Texas Chain Saw Massacre", 1974),
        CuratedSeasonalItem("4488", "Friday the 13th", 1980),
        CuratedSeasonalItem("609", "Poltergeist", 1982),
        CuratedSeasonalItem("539", "Psycho", 1960),
        CuratedSeasonalItem("348", "Alien", 1979),
        CuratedSeasonalItem("1091", "The Thing", 1982),
        CuratedSeasonalItem("419430", "Get Out", 2017),
        CuratedSeasonalItem("481848", "Us", 2019),
        CuratedSeasonalItem("176", "Saw", 2004),
        CuratedSeasonalItem("58138", "Insidious", 2010),
        CuratedSeasonalItem("242224", "The Babadook", 2014),
        CuratedSeasonalItem("82507", "Sinister", 2012),
        CuratedSeasonalItem("530385", "Midsommar", 2019),
        CuratedSeasonalItem("23629", "Trick 'r Treat", 2007),
        CuratedSeasonalItem("2668", "Sleepy Hollow", 1999),
        CuratedSeasonalItem("2907", "The Addams Family", 1991),
        CuratedSeasonalItem("3933", "Corpse Bride", 2005),
        CuratedSeasonalItem("620", "Ghostbusters", 1984),
        CuratedSeasonalItem("310131", "The Witch", 2015),
        CuratedSeasonalItem("805", "Rosemary's Baby", 1968),
        CuratedSeasonalItem("7340", "Carrie", 1976),
        CuratedSeasonalItem("565", "The Ring", 2002),
        CuratedSeasonalItem("11906", "Suspiria", 1977),
        CuratedSeasonalItem("170", "28 Days Later", 2002),
        CuratedSeasonalItem("274", "The Silence of the Lambs", 1991),
        CuratedSeasonalItem("2667", "The Blair Witch Project", 1999),
        CuratedSeasonalItem("764", "The Evil Dead", 1981),
        CuratedSeasonalItem("765", "Evil Dead II", 1987),
        CuratedSeasonalItem("713704", "Evil Dead Rise", 2023),
        CuratedSeasonalItem("747", "Shaun of the Dead", 2004),
        CuratedSeasonalItem("62215", "The Cabin in the Woods", 2011),
        CuratedSeasonalItem("14836", "Coraline", 2009),
        CuratedSeasonalItem("9297", "Monster House", 2006),
        CuratedSeasonalItem("162", "Edward Scissorhands", 1990),
        CuratedSeasonalItem("23827", "Paranormal Activity", 2007),
        CuratedSeasonalItem("9003", "Hellraiser", 1987),
        CuratedSeasonalItem("565028", "Candyman", 2021),
        CuratedSeasonalItem("745", "The Sixth Sense", 1999),
        CuratedSeasonalItem("653", "Nosferatu", 1922),
        CuratedSeasonalItem("6114", "Bram Stoker's Dracula", 1992),
        CuratedSeasonalItem("3035", "Frankenstein", 1931),
        CuratedSeasonalItem("420634", "Terrifier", 2016),
        CuratedSeasonalItem("663712", "Terrifier 2", 2022),
        CuratedSeasonalItem("882598", "Smile", 2022),
        CuratedSeasonalItem("1008042", "Talk to Me", 2022),
        CuratedSeasonalItem("913290", "Barbarian", 2022),
        CuratedSeasonalItem("756999", "The Black Phone", 2021),
        CuratedSeasonalItem("447332", "A Quiet Place", 2018),
        CuratedSeasonalItem("13310", "Let the Right One In", 2008),
        CuratedSeasonalItem("10331", "Night of the Living Dead", 1968),
        CuratedSeasonalItem("1933", "The Others", 2001),
        CuratedSeasonalItem("6537", "El orfanato", 2007),
        CuratedSeasonalItem("8329", "[REC]", 2007),
        CuratedSeasonalItem("8922", "Jeepers Creepers", 2001),
        CuratedSeasonalItem("9532", "Final Destination", 2000),
        CuratedSeasonalItem("794", "The Omen", 1976),
        CuratedSeasonalItem("5825", "The Mist", 2007),
        CuratedSeasonalItem("16871", "Drag Me to Hell", 2009),
        CuratedSeasonalItem("270303", "It Follows", 2014),
        CuratedSeasonalItem("760104", "X", 2022),
        CuratedSeasonalItem("675353", "Pearl", 2022),
        CuratedSeasonalItem("396535", "Train to Busan", 2016),
        CuratedSeasonalItem("10585", "Child's Play", 1988)
    )

    private val CHRISTMAS_CURATED_COMMUNITY = listOf(
        CuratedSeasonalItem("771", "Home Alone", 1990),
        CuratedSeasonalItem("772", "Home Alone 2: Lost in New York", 1992),
        CuratedSeasonalItem("8871", "How the Grinch Stole Christmas", 2000),
        CuratedSeasonalItem("9479", "The Nightmare Before Christmas", 1993),
        CuratedSeasonalItem("1585", "It's a Wonderful Life", 1946),
        CuratedSeasonalItem("522681", "Klaus", 2019),
        CuratedSeasonalItem("508439", "Love Actually", 2003),
        CuratedSeasonalItem("10719", "Elf", 2003),
        CuratedSeasonalItem("562", "Die Hard", 1988),
        CuratedSeasonalItem("927", "Gremlins", 1984),
        CuratedSeasonalItem("5255", "The Polar Express", 2004),
        CuratedSeasonalItem("671", "Harry Potter and the Sorcerer's Stone", 2001),
        CuratedSeasonalItem("162", "Edward Scissorhands", 1990),
        CuratedSeasonalItem("11395", "The Santa Clause", 1994),
        CuratedSeasonalItem("364", "Batman Returns", 1992)
    )

    val ALL_EVENTS = listOf(
        SeasonalEvent(
            id = "halloween",
            badgeLabel = "CICLO DE TERROR",
            title = "Especial Halloween: Clásicos del Miedo",
            subtitle = "Una cuidada selección de terror y cine de culto para la noche de brujas",
            startMonth = Calendar.OCTOBER,
            startDay = 15,
            endMonth = Calendar.NOVEMBER,
            endDay = 6,
            matchingGenres = listOf("Terror", "Horror"), // Estricto: sin Thriller ni Suspense genéricos
            excludedGenres = listOf("Documental", "Documentary", "Música", "Music", "Bélica", "War", "Guerra", "Western", "Noticias", "News", "Concierto", "Concert", "Cortometraje", "Short"),
            accentHex = "#EA580C",
            homeSectionTitle = "Ciclo de Terror • Especial de Temporada",
            curatedItems = HALLOWEEN_CURATED_COMMUNITY,
            tmdbDiscoverGenreId = "27", // ID de género Horror en TMDb
            tmdbWithoutGenres = "99,10752,10402" // Sin Documentales, Bélicas ni Música
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
            matchingGenres = listOf("Navidad", "Holiday", "Familiar", "Family"),
            excludedGenres = listOf("Terror", "Horror", "Bélica", "War", "Guerra", "Documental", "Documentary"),
            accentHex = "#DC2626",
            homeSectionTitle = "Selección Festiva • Cine de Invierno",
            curatedItems = CHRISTMAS_CURATED_COMMUNITY,
            tmdbWithoutGenres = "27,99,10752"
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
            matchingGenres = listOf("Romance", "Romántica"),
            excludedGenres = listOf("Terror", "Horror", "Bélica", "War", "Guerra", "Documental", "Documentary"),
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
            matchingGenres = listOf("Drama", "Historia", "History", "Biografía", "Biography"),
            excludedGenres = listOf("Documental", "Documentary", "Música", "Music"),
            accentHex = "#D97706",
            homeSectionTitle = "Temporada de Premios • Cine Aclamado"
        )
    )

    fun getActiveEvent(now: Calendar = Calendar.getInstance()): SeasonalEvent? {
        return ALL_EVENTS.firstOrNull { it.isActive(now) }
    }
}
