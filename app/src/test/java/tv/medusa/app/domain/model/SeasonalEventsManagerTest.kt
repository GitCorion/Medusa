package tv.medusa.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SeasonalEventsManagerTest {

    private fun calendarFor(month1Indexed: Int, day: Int, year: Int = 2026): Calendar {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month1Indexed - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
    }

    @Test
    fun `halloween event is active between October 15 and November 6`() {
        val event = SeasonalEventsManager.ALL_EVENTS.first { it.id == "halloween" }

        assertTrue("Oct 15 should be active", event.isActive(calendarFor(10, 15)))
        assertTrue("Oct 19 should be active", event.isActive(calendarFor(10, 19)))
        assertTrue("Oct 31 should be active", event.isActive(calendarFor(10, 31)))
        assertTrue("Nov 6 should be active", event.isActive(calendarFor(11, 6)))

        assertFalse("Oct 14 should not be active", event.isActive(calendarFor(10, 14)))
        assertFalse("Nov 7 should not be active", event.isActive(calendarFor(11, 7)))
    }

    @Test
    fun `christmas event spans year boundary correctly`() {
        val event = SeasonalEventsManager.ALL_EVENTS.first { it.id == "christmas" }

        assertTrue("Dec 1 should be active", event.isActive(calendarFor(12, 1)))
        assertTrue("Dec 25 should be active", event.isActive(calendarFor(12, 25)))
        assertTrue("Dec 31 should be active", event.isActive(calendarFor(12, 31)))
        assertTrue("Jan 1 should be active", event.isActive(calendarFor(1, 1)))
        assertTrue("Jan 8 should be active", event.isActive(calendarFor(1, 8)))

        assertFalse("Nov 30 should not be active", event.isActive(calendarFor(11, 30)))
        assertFalse("Jan 9 should not be active", event.isActive(calendarFor(1, 9)))
    }

    @Test
    fun `getActiveEvent selects active event for current date`() {
        val oct19Cal = calendarFor(10, 19)
        val event = SeasonalEventsManager.getActiveEvent(oct19Cal)

        assertNotNull(event)
        assertEquals("halloween", event?.id)
        assertEquals("CICLO DE TERROR", event?.badgeLabel)
    }

    @Test
    fun `getActiveEvent returns null during unassigned dates`() {
        val july15Cal = calendarFor(7, 15)
        val event = SeasonalEventsManager.getActiveEvent(july15Cal)

        assertNull(event)
    }

    @Test
    fun `halloween curated list contains community classics and excludes non-horror genres`() {
        val event = SeasonalEventsManager.ALL_EVENTS.first { it.id == "halloween" }

        // Comprueba que los clásicos de la comunidad están incluidos
        val titles = event.curatedItems.map { it.title }
        assertTrue(titles.contains("The Shining"))
        assertTrue(titles.contains("Hereditary"))
        assertTrue(titles.contains("Halloween"))
        assertTrue(titles.contains("Scream"))
        assertTrue(titles.contains("Beetlejuice"))
        assertTrue(titles.contains("Hocus Pocus"))
        assertTrue(titles.contains("The Conjuring"))

        // Comprueba que géneros no temáticos están estrictamente en la lista negra
        assertTrue(event.excludedGenres.contains("Documental"))
        assertTrue(event.excludedGenres.contains("Música"))
        assertTrue(event.excludedGenres.contains("Bélica"))
        assertTrue(event.excludedGenres.contains("War"))
        assertTrue(event.excludedGenres.contains("Music"))

        // Comprueba que no tiene géneros ambiguos como Thriller o Misterio que contaminan la selección
        assertFalse(event.matchingGenres.contains("Thriller"))
        assertFalse(event.matchingGenres.contains("Misterio"))
        assertFalse(event.matchingGenres.contains("Suspense"))
    }

    @Test
    fun `editorial badge labels do not contain emojis`() {
        // Strict requirement: zero tacky WhatsApp emojis, clean cinema-grade UI
        val emojiRegex = Regex("[\\p{So}\\p{Cn}]")
        SeasonalEventsManager.ALL_EVENTS.forEach { event ->
            assertFalse(
                "Badge label '${event.badgeLabel}' should not contain emojis",
                emojiRegex.containsMatchIn(event.badgeLabel)
            )
            assertFalse(
                "Title '${event.title}' should not contain emojis",
                emojiRegex.containsMatchIn(event.title)
            )
            assertFalse(
                "Home section title '${event.homeSectionTitle}' should not contain emojis",
                emojiRegex.containsMatchIn(event.homeSectionTitle)
            )
        }
    }
}
