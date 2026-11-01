package tv.medusa.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.medusa.app.domain.model.SeasonalEventsManager

class GetSeasonalFeedUseCaseTest {

    @Test
    fun `halloween event configuration is valid`() {
        val event = SeasonalEventsManager.ALL_EVENTS.first { it.id == "halloween" }
        assertEquals("halloween", event.id)
        assertEquals("Selección de Halloween", event.homeSectionTitle)
    }
}
