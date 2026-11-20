package tv.medusa.app.ui.components.sort

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.medusa.app.domain.model.MediaItem

class CatalogSortOptionTest {

    private val itemA = MediaItem(id = "1", title = "Avatar", year = 2009, rating = 7.9f)
    private val itemB = MediaItem(id = "2", title = "batman", year = 2022, rating = 8.2f)
    private val itemC = MediaItem(id = "3", title = "Casablanca", year = 1942, rating = 8.5f)
    private val itemD = MediaItem(id = "4", title = "Zootopia", year = 2016, rating = 8.0f)
    private val itemNoYearNoRating = MediaItem(id = "5", title = "Unknown", year = null, rating = null)

    private val sampleList = listOf(itemD, itemB, itemA, itemNoYearNoRating, itemC)

    @Test
    fun defaultSort_preservesOriginalOrder() {
        val sorted = CatalogSortOption.DEFAULT.sort(sampleList)
        assertEquals(sampleList, sorted)
    }

    @Test
    fun titleAsc_sortsAlphabeticallyAscendingCaseInsensitive() {
        val sorted = CatalogSortOption.TITLE_ASC.sort(sampleList)
        assertEquals(listOf("1", "2", "3", "5", "4"), sorted.map { it.id })
    }

    @Test
    fun titleDesc_sortsAlphabeticallyDescendingCaseInsensitive() {
        val sorted = CatalogSortOption.TITLE_DESC.sort(sampleList)
        assertEquals(listOf("4", "5", "3", "2", "1"), sorted.map { it.id })
    }

    @Test
    fun yearDesc_sortsNewestFirstWithNullsLast() {
        val sorted = CatalogSortOption.YEAR_DESC.sort(sampleList)
        assertEquals(listOf("2", "4", "1", "3", "5"), sorted.map { it.id })
    }

    @Test
    fun yearAsc_sortsOldestFirstWithNullsLast() {
        val sorted = CatalogSortOption.YEAR_ASC.sort(sampleList)
        assertEquals(listOf("3", "1", "4", "2", "5"), sorted.map { it.id })
    }

    @Test
    fun ratingDesc_sortsHighestRatingFirstWithNullsLast() {
        val sorted = CatalogSortOption.RATING_DESC.sort(sampleList)
        assertEquals(listOf("3", "2", "4", "1", "5"), sorted.map { it.id })
    }
}
