package tv.medusa.app.data.remote.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestSelectionTest {

    private fun manifest(
        code: Long,
        name: String,
        minSupported: Long = 0L,
        forceUpdate: Boolean = false,
        killSwitch: Boolean = false,
        url: String = "https://github.com/GitCorion/Medusa/releases/download/v$name/Medusa.apk"
    ) = UpdateManifestDto(
        versionCode = code,
        versionName = name,
        minSupportedVersionCode = minSupported,
        forceUpdate = forceUpdate,
        killSwitch = killSwitch,
        apkUrl = url
    )

    @Test
    fun `solo beta activa el canal beta`() {
        assertTrue(UpdateManifestSelection.isBeta("beta"))
        assertTrue(UpdateManifestSelection.isBeta("BETA"))
        assertFalse(UpdateManifestSelection.isBeta("stable"))
        assertFalse(UpdateManifestSelection.isBeta(""))
    }

    @Test
    fun `sin manifiestos no hay actualizacion`() {
        assertNull(UpdateManifestSelection.merge(null, null))
    }

    @Test
    fun `estable sin beta conserva su version`() {
        val stable = manifest(1000161, "2.4.0")
        val merged = UpdateManifestSelection.merge(stable, null)
        assertEquals(1000161L, merged?.versionCode)
        assertEquals("2.4.0", merged?.versionName)
    }

    @Test
    fun `en beta gana el versionCode mas alto`() {
        val stable = manifest(1000160, "2.4.0-beta.1")
        val beta = manifest(1000161, "2.5.0-beta.1")
        assertEquals("2.5.0-beta.1", UpdateManifestSelection.merge(stable, beta)?.versionName)
    }

    @Test
    fun `una estable posterior gradua al equipo beta`() {
        val stable = manifest(1000170, "2.5.0")
        val beta = manifest(1000161, "2.5.0-beta.1")
        assertEquals("2.5.0", UpdateManifestSelection.merge(stable, beta)?.versionName)
    }

    @Test
    fun `el kill switch del estable llega a la beta`() {
        val stable = manifest(1000170, "2.5.0", killSwitch = true, minSupported = 1000165)
        val beta = manifest(1000171, "2.6.0-beta.1", killSwitch = false)
        val merged = UpdateManifestSelection.merge(stable, beta)
        assertEquals("2.6.0-beta.1", merged?.versionName)
        assertTrue(merged?.killSwitch == true)
        assertTrue(merged?.forceUpdate == false)
        assertEquals(1000165L, merged?.minSupportedVersionCode)
    }

    @Test
    fun `una beta sin publicar deja pasar la estable`() {
        val stable = manifest(1000161, "2.4.0")
        val beta = manifest(0, "", url = "")
        assertEquals("2.4.0", UpdateManifestSelection.merge(stable, beta)?.versionName)
    }
}
