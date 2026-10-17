package tv.medusa.app.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Genera el Baseline Profile de Medusa para `tv.medusa.app`.
 *
 * Cubre el arranque en frío (startup profile) y unos desplazamientos en la pantalla principal
 * para incluir el hot-path de composición/scroll. Se ejecuta en un dispositivo/emulador real, una
 * vez por flavor:
 *
 * ```
 * ./gradlew :app:generateBetaReleaseBaselineProfile   # canal OTA (APK beta)
 * ./gradlew :app:generatePlayReleaseBaselineProfile   # AAB de Play
 * ```
 *
 * El resultado se guarda en `app/src/<flavor>Release/generated/baselineProfiles/` (ver `saveInSrc`) y
 * AGP lo empaqueta en el APK (`assets/dexopt/baseline.prof`) y en el AAB
 * (`BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof`).
 *
 * ⚠️ `saveInSrc` escribe en el source set del **flavor**, no en el de build type: un
 * `baseline-prof.txt` en `app/src/release/` **no** se consume (comprobado: los perfiles combinados
 * se quedaban sin clases de la app). De ahí que haya un fichero por flavor.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(
            packageName = PACKAGE_NAME,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), 5_000)
            device.waitForIdle()

            val width = device.displayWidth
            val height = device.displayHeight
            val centerX = width / 2
            val topY = (height * 0.25).toInt()
            val bottomY = (height * 0.75).toInt()

            // Desplazamiento vertical de ida y vuelta para perfilar el render de listas.
            repeat(3) {
                device.swipe(centerX, bottomY, centerX, topY, 20)
                device.swipe(centerX, topY, centerX, bottomY, 20)
            }
            device.waitForIdle()
        }
    }

    private companion object {
        const val PACKAGE_NAME = "tv.medusa.app"
    }
}
