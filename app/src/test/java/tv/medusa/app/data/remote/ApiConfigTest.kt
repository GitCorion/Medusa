package tv.medusa.app.data.remote

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * Fija la regla de idioma/región que se envía a TMDB.
 *
 * La app solo está traducida a es/en, así que el idioma se mapea a `es-ES`/`en-US` (pedir un tercer
 * idioma dejaría la UI en un idioma y el contenido en otro). La región de plataformas, en cambio,
 * sale del país del dispositivo tal cual; el usuario puede sobrescribirla en Ajustes y ese ajuste
 * manda siempre sobre este valor por defecto.
 */
class ApiConfigTest {

    private lateinit var defaultLocale: Locale

    @Before
    fun saveLocale() {
        defaultLocale = Locale.getDefault()
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    private fun withLocale(tag: String) {
        Locale.setDefault(Locale.forLanguageTag(tag))
    }

    @Test
    fun `el espanol se pide como es-ES`() {
        withLocale("es-ES")
        assertEquals("es-ES", ApiConfig.TMDB_LANGUAGE)

        withLocale("es-MX")
        assertEquals("es-ES", ApiConfig.TMDB_LANGUAGE)
    }

    @Test
    fun `cualquier otro idioma se pide como en-US`() {
        withLocale("en-GB")
        assertEquals("en-US", ApiConfig.TMDB_LANGUAGE)

        withLocale("fr-FR")
        assertEquals("en-US", ApiConfig.TMDB_LANGUAGE)
    }

    @Test
    fun `la region de plataformas sale del pais del dispositivo`() {
        withLocale("en-GB")
        assertEquals("GB", ApiConfig.TMDB_WATCH_REGION)

        withLocale("es-MX")
        assertEquals("MX", ApiConfig.TMDB_WATCH_REGION)
    }

    @Test
    fun `sin pais en el locale se usa ES para espanol y US para el resto`() {
        withLocale("es")
        assertEquals("ES", ApiConfig.TMDB_WATCH_REGION)

        withLocale("fr")
        assertEquals("US", ApiConfig.TMDB_WATCH_REGION)
    }
}
