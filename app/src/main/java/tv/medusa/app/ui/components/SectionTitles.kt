package tv.medusa.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.medusa.app.R
import tv.medusa.app.domain.model.HomeSection

/**
 * Traducción de presentación de los títulos de sección del feed.
 *
 * Los títulos se generan en la capa de dominio (GetHomeFeedUseCase, BrandViewModel,
 * GenreViewModel…) y además de mostrarse se usan como identidad estable (SectionPriority,
 * `it.title == "Continuar Viendo"`, `distinctBy { it.title }`). Por eso el texto canónico en
 * español se conserva tal cual en el dominio y la traducción se resuelve aquí, en el único
 * punto de pintado de las secciones ([MediaRow]).
 *
 * Los títulos dinámicos (plataforma, "porque viste", listas de Trakt, géneros recomendados)
 * se resuelven por prefijo para no perder el fragmento variable.
 */
@Composable
fun localizedSectionTitle(section: HomeSection): String {
    val title = section.title
    return when {
        title == "Continuar Viendo" -> stringResource(R.string.section_continue_watching)
        title == "Añadido recientemente" -> stringResource(R.string.section_recently_added)
        title == "Grandes Producciones de tu colección" -> stringResource(R.string.section_top_movies)
        title == "Series Destacadas" -> stringResource(R.string.section_top_series)
        title == "Películas Recomendadas" -> stringResource(R.string.section_recommended_movies)
        title == "Joyas de tu colección" -> stringResource(R.string.section_hidden_gems)
        title == "Joyas Ocultas" -> stringResource(R.string.section_genre_hidden_gems)
        title == "Top 10 Imprescindibles" -> stringResource(R.string.section_top10_essentials)
        title == "Novedades Recientes" -> stringResource(R.string.section_new_releases)
        title == "Últimos estrenos" -> stringResource(R.string.section_latest_releases)
        title.startsWith("Top 10 en ") -> {
            val country = title.removePrefix("Top 10 en ").removeSuffix(" hoy").trim()
            if (java.util.Locale.getDefault().language == "en") {
                val englishCountry = when (country) {
                    "España" -> "Spain"
                    "México" -> "Mexico"
                    "Argentina" -> "Argentina"
                    "Colombia" -> "Colombia"
                    "Chile" -> "Chile"
                    "Perú" -> "Peru"
                    "EE. UU." -> "the US"
                    "Reino Unido" -> "the UK"
                    "Francia" -> "France"
                    "Alemania" -> "Germany"
                    "Italia" -> "Italy"
                    else -> country
                }
                "Top 10 in $englishCountry Today"
            } else {
                title
            }
        }
        title == "Top 10 en España hoy" -> stringResource(R.string.section_top10_spain)
        title == "Top 10 hoy" || title == "Top 10 Global hoy" -> {
            if (java.util.Locale.getDefault().language == "en") "Top 10 Today" else "Top 10 hoy"
        }
        title == "Tendencias hoy" -> {
            if (java.util.Locale.getDefault().language == "en") "Trending Today" else title
        }
        title == "Top 10 Netflix · Películas" -> {
            if (java.util.Locale.getDefault().language == "en") "Netflix Top 10 · Movies" else title
        }
        title == "Top 10 Netflix · Series" -> {
            if (java.util.Locale.getDefault().language == "en") "Netflix Top 10 · Series" else title
        }
        title == "Grandes Éxitos de tu colección" -> {
            if (java.util.Locale.getDefault().language == "en") "Blockbusters in Your Library" else title
        }
        title == "Sagas y Colecciones Legendarias" -> {
            if (java.util.Locale.getDefault().language == "en") "Legendary Sagas & Collections" else title
        }
        title == "Volver a disfrutar" -> {
            if (java.util.Locale.getDefault().language == "en") "Rewatch Favorites" else title
        }
        title == "Documentales y Grandes Reportajes" -> {
            if (java.util.Locale.getDefault().language == "en") "Documentaries & Feature Stories" else title
        }
        title == "Joyas Ocultas Inéditas" -> {
            if (java.util.Locale.getDefault().language == "en") "Unseen Hidden Gems" else title
        }
        title.contains("Producción Nacional") -> {
            if (java.util.Locale.getDefault().language == "en") {
                title.replace("y Producción Nacional", "& Local Production")
                    .replace("Cine Español", "Spanish Cinema")
                    .replace("Cine Mexicano", "Mexican Cinema")
                    .replace("Cine Argentino", "Argentine Cinema")
                    .replace("Cine Colombiano", "Colombian Cinema")
                    .replace("Cine Chileno", "Chilean Cinema")
                    .replace("Cine Peruano", "Peruvian Cinema")
            } else {
                title
            }
        }
        title == "Cine Español e Iberoamericano" -> {
            if (java.util.Locale.getDefault().language == "en") "Spanish & Ibero-American Cinema" else title
        }
        title == "Recomendado por la comunidad Trakt" -> stringResource(R.string.section_trakt_community)
        title == "Tu Watchlist de Trakt.tv" -> stringResource(R.string.section_trakt_watchlist)
        title == "Tendencias en Trakt.tv" -> stringResource(R.string.section_trakt_trending)
        title == "Tu Watchlist" -> stringResource(R.string.section_watchlist)
        title == "Recomendaciones para ti" -> stringResource(R.string.section_for_you)
        title == "En Tendencia" -> stringResource(R.string.section_trending)
        title == "Cine Aclamado" -> stringResource(R.string.section_acclaimed_cinema)
        title == "Películas" -> stringResource(R.string.section_movies)
        title == "Series" -> stringResource(R.string.section_series)
        title.startsWith("Recomendados de ") ->
            stringResource(R.string.section_recommended_of, title.removePrefix("Recomendados de "))
        title.startsWith("Populares en ") ->
            stringResource(R.string.section_platform_popular, title.removePrefix("Populares en "))
        title.startsWith("Porque viste ") ->
            stringResource(R.string.section_because_watched, title.removePrefix("Porque viste "))
        title.startsWith("Trakt: ") ->
            stringResource(R.string.section_trakt_list, title.removePrefix("Trakt: "))
        else -> title
    }
}

@Composable
fun localizedSectionBadge(badge: String?): String? = when (badge) {
    null, "" -> null
    "LISTA" -> stringResource(R.string.badge_list)
    "TRAKT LISTA" -> stringResource(R.string.badge_trakt_list)
    "PENDIENTE" -> stringResource(R.string.badge_pending)
    "PARA TI" -> stringResource(R.string.badge_for_you)
    "POPULAR" -> stringResource(R.string.badge_popular)
    "NACIONAL" -> if (java.util.Locale.getDefault().language == "en") "LOCAL" else "NACIONAL"
    "FAVORITOS" -> if (java.util.Locale.getDefault().language == "en") "FAVORITES" else "FAVORITOS"
    "SAGAS" -> "SAGAS"
    "DOCS" -> "DOCS"
    "CINE" -> if (java.util.Locale.getDefault().language == "en") "CINEMA" else "CINE"
    else -> badge
}
