package tv.medusa.app.ui.screens.search

import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardDirectionResolverTest {

    @Test
    fun `spanish keyboard includes enye`() {
        assertTrue('Ñ' in KeyboardDirectionResolver.ALL_CHARS)
        assertTrue(KeyboardDirectionResolver.KEYBOARD_ROWS.flatten().contains('Ñ'))
    }

    @Test
    fun `all keyboard chars include enye`() {
        assertTrue('Ñ' in SearchPredictiveEngine.ALL_KEYBOARD_CHARS)
    }
}
