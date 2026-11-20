package tv.medusa.app.ui.components.sort

import tv.medusa.app.R
import tv.medusa.app.domain.model.MediaItem

/**
 * Criterios de ordenación para vistas de catálogo en cuadrícula ("muro") de Medusa TV.
 */
enum class CatalogSortOption(
    val labelRes: Int,
    val shortLabelRes: Int
) {
    DEFAULT(R.string.sort_default, R.string.sort_default_short),
    TITLE_ASC(R.string.sort_title_asc, R.string.sort_title_asc_short),
    TITLE_DESC(R.string.sort_title_desc, R.string.sort_title_desc_short),
    YEAR_DESC(R.string.sort_year_desc, R.string.sort_year_desc_short),
    YEAR_ASC(R.string.sort_year_asc, R.string.sort_year_asc_short),
    RATING_DESC(R.string.sort_rating_desc, R.string.sort_rating_desc_short);

    fun sort(items: List<MediaItem>): List<MediaItem> {
        return when (this) {
            DEFAULT -> items
            TITLE_ASC -> items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            TITLE_DESC -> items.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title })
            YEAR_DESC -> items.sortedWith(
                compareByDescending<MediaItem> { it.year ?: 0 }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            )
            YEAR_ASC -> items.sortedWith(
                compareBy<MediaItem> { it.year ?: 9999 }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            )
            RATING_DESC -> items.sortedWith(
                compareByDescending<MediaItem> { it.rating ?: 0f }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            )
        }
    }
}
